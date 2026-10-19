package app.suical.data

import androidx.room.withTransaction
import app.suical.domain.AppState
import app.suical.domain.DEFAULT_LLM_PKG
import app.suical.domain.Defaults
import app.suical.domain.Dish
import app.suical.domain.Food
import app.suical.domain.LogEntry
import app.suical.domain.Nutrients
import app.suical.domain.PlanConfig
import app.suical.domain.Weight
import app.suical.domain.Calc
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Assez pour des statistiques sur plusieurs mois (~5 repas par jour). */
private const val LOG_KEEP = 3000
private const val K_SEEDED = "seeded"
private const val K_TARGET = "target"
private const val K_SAUCE = "sauce"
private const val K_LLM = "llm_pkg"
private const val K_CONFIG = "config"

private fun encodeIng(m: Map<String, Double>) = m.entries.joinToString(";") { "${it.key}=${it.value}" }
private fun decodeIng(s: String): Map<String, Double> =
    if (s.isBlank()) emptyMap() else s.split(";").mapNotNull {
        val p = it.split("=")
        if (p.size == 2) p[1].toDoubleOrNull()?.let { v -> p[0] to v } else null
    }.toMap()

/**
 * Seul point d'accès à la base. Les méthodes `suspend` sont de petites écritures ;
 * pour en enchaîner plusieurs de façon atomique, les envelopper dans [tx].
 */
class Repository(private val db: SuicalDb) {
    constructor(context: android.content.Context) : this(SuicalDb.get(context))

    private val dao = db.dao()

    suspend fun <T> tx(block: suspend () -> T): T = db.withTransaction { block() }

    /** L'état complet, ré-émis à chaque modification de n'importe quelle table. */
    fun state(): Flow<AppState> = combine(
        listOf(dao.foods(), dao.foodNuts(), dao.dishes(), dao.dishIngs(), dao.plan(),
            dao.stock(), dao.ready(), dao.log(), dao.settings(), dao.weights())
    ) { a ->
        @Suppress("UNCHECKED_CAST")
        build(a[0] as List<FoodE>, a[1] as List<FoodNutE>, a[2] as List<DishE>, a[3] as List<DishIngE>,
            a[4] as List<PlanE>, a[5] as List<StockE>, a[6] as List<ReadyE>, a[7] as List<LogE>, a[8] as List<SettingE>, a[9] as List<WeightE>)
    }

    private fun build(
        foods: List<FoodE>, nuts: List<FoodNutE>, dishes: List<DishE>, ings: List<DishIngE>, plan: List<PlanE>,
        stock: List<StockE>, ready: List<ReadyE>, log: List<LogE>, settings: List<SettingE>, weights: List<WeightE>,
    ): AppState {
        val nutBy = nuts.groupBy { it.foodId }
        val ingBy = ings.groupBy { it.dishId }
        val set = settings.associate { it.k to it.v }
        val config = set[K_CONFIG]?.let { runCatching { Backup.configFromJson(JSONObject(it)) }.getOrNull() } ?: PlanConfig()
        val days = config.days
        val grid = List(days) { d -> List(2) { s -> plan.firstOrNull { it.day == d && it.slot == s }?.dishId ?: "" } }
        return AppState(
            foods = foods.associate { f ->
                val n = (nutBy[f.id] ?: emptyList()).associate { it.nutKey to it.value }
                f.id to Food(f.id, f.name, f.unit, f.cat, f.pack, f.packLabel, f.price, Nutrients.keys.associateWith { n[it] ?: 0.0 })
            },
            dishes = dishes.map { d ->
                Dish(d.id, d.name, d.grp, d.sauce, d.fresh, (ingBy[d.id] ?: emptyList()).associate { it.foodId to it.qty })
            },
            plan = grid,
            stock = stock.associate { it.foodId to it.qty },
            ready = ready.filter { it.n > 0 }.associate { it.dishId to it.n },
            log = log.map { LogEntry(it.id, it.dishId, it.t, it.ready, decodeIng(it.ing), it.name, decodeIng(it.custom)) },
            target = set[K_TARGET]?.toIntOrNull(),
            sauce = set[K_SAUCE] ?: "creme",
            llmPkg = set[K_LLM] ?: DEFAULT_LLM_PKG,
            config = config,
            weights = weights.map { Weight(it.day, it.kg) },
        )
    }

    /** Premier lancement : charge les données de départ. */
    suspend fun seedIfNeeded() = tx {
        if (dao.setting(K_SEEDED) == null) replaceAll(Defaults.fresh())
    }

    /** Remplace toute la base par cet état (import, réinitialisation, premier lancement). */
    suspend fun replaceAll(s: AppState) = tx {
        dao.clearFoods(); dao.clearNuts(); dao.clearDishes(); dao.clearIngs()
        dao.clearPlan(); dao.clearStock(); dao.clearReady(); dao.clearLog(); dao.clearWeights()
        s.weights.forEach { dao.put(WeightE(it.epochDay, it.kg)) }
        s.foods.values.forEach { saveFood(it) }
        s.dishes.forEach { saveDish(it) }
        s.plan.forEachIndexed { d, row -> row.forEachIndexed { sl, id -> dao.put(PlanE(d, sl, id)) } }
        s.foods.keys.forEach { dao.put(StockE(it, s.stockOf(it))) }
        s.ready.forEach { (id, n) -> dao.put(ReadyE(id, n)) }
        s.log.asReversed().forEach { addLog(it) }
        setTarget(s.target)
        setSetting(K_SAUCE, s.sauce)
        setSetting(K_LLM, s.llmPkg)
        setConfig(s.config)
        setSetting(K_SEEDED, "1")
    }

    // ---- aliments ----
    suspend fun saveFood(f: Food) = tx {
        val sort = dao.foodSort(f.id) ?: dao.nextFoodSort()
        dao.put(FoodE(f.id, f.name, f.unit, f.cat, f.pack, f.packLabel, f.price, sort))
        dao.deleteNuts(f.id)
        dao.putNuts(f.nut.map { (k, v) -> FoodNutE(f.id, k, v) })
    }

    suspend fun deleteFood(id: String) = tx {
        dao.deleteFood(id); dao.deleteNuts(id); dao.deleteStock(id); dao.deleteIngsOfFood(id)
    }

    // ---- plats ----
    suspend fun saveDish(d: Dish) = tx {
        val sort = dao.dishSort(d.id) ?: dao.nextDishSort()
        dao.put(DishE(d.id, d.name, d.group, d.sauce, d.fresh, sort))
        dao.deleteIngsOfDish(d.id)
        dao.putIngs(d.ing.map { (k, q) -> DishIngE(d.id, k, q) })
    }

    suspend fun deleteDish(id: String) = tx {
        dao.deleteDish(id); dao.deleteIngsOfDish(id); dao.clearPlanOf(id); dao.deleteReady(id)
    }

    suspend fun setPlan(day: Int, slot: Int, dishId: String) = dao.put(PlanE(day, slot, dishId))

    // ---- stock et portions prêtes ----
    suspend fun setStock(foodId: String, qty: Double) = dao.put(StockE(foodId, qty.coerceAtLeast(0.0)))
    suspend fun setReady(dishId: String, n: Int) = if (n > 0) dao.put(ReadyE(dishId, n)) else dao.deleteReady(dishId)

    // ---- journal ----
    suspend fun addLog(e: LogEntry) = tx {
        dao.put(LogE(0, e.dishId, e.t, e.ready, encodeIng(e.ing), e.name, encodeIng(e.custom)))
        dao.trimLog(LOG_KEEP)
    }
    suspend fun deleteLog(id: Long) = dao.deleteLog(id)

    /**
     * Note un plat comme mangé : consomme une portion prête, sinon les ingrédients du stock.
     * @return false s'il n'y avait pas de quoi le faire.
     */
    suspend fun eat(s: AppState, c: Calc, d: Dish): Boolean {
        val ready = s.ready[d.id] ?: 0
        val used = c.effIng(d)
        val now = System.currentTimeMillis()
        if (ready > 0) {
            tx { setReady(d.id, ready - 1); addLog(LogEntry(0, d.id, now, true, used)) }
        } else {
            if (c.possible(d).n < 1) return false
            tx {
                used.forEach { (k, q) -> setStock(k, s.stockOf(k) - q) }
                addLog(LogEntry(0, d.id, now, false, used))
            }
        }
        return true
    }

    /** Annule une ligne du journal en rendant la portion ou les ingrédients. */
    suspend fun undo(s: AppState, c: Calc, e: LogEntry) = tx {
        val d = s.dish(e.dishId)
        if (d != null && e.ready) setReady(d.id, (s.ready[d.id] ?: 0) + 1)
        else if (d != null) (e.ing.ifEmpty { c.effIng(d) }).forEach { (k, q) -> setStock(k, s.stockOf(k) + q) }
        deleteLog(e.id)
    }

    /** Repas hors plan : ne touche pas au stock. */
    suspend fun addExtra(name: String, nut: Map<String, Double>) =
        addLog(LogEntry(0, "", System.currentTimeMillis(), false, emptyMap(), name, nut))

    // ---- poids ----
    suspend fun setWeight(epochDay: Long, kg: Double) = dao.put(WeightE(epochDay, kg))
    suspend fun deleteWeight(epochDay: Long) = dao.deleteWeight(epochDay)

    // ---- réglages ----
    suspend fun setTarget(t: Int?) = if (t == null) dao.deleteSetting(K_TARGET) else dao.put(SettingE(K_TARGET, t.toString()))
    suspend fun setSauce(s: String) = setSetting(K_SAUCE, s)
    suspend fun setLlmPkg(p: String) = setSetting(K_LLM, p)
    suspend fun setConfig(c: PlanConfig) = setSetting(K_CONFIG, Backup.configToJson(c).toString())
    private suspend fun setSetting(k: String, v: String) = dao.put(SettingE(k, v))
}
