package app.suical.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

typealias Ing = Map<String, Double>
typealias Nut = Map<String, Double>

data class Possible(val n: Int, val limit: String?)
data class BuyRow(val foodId: String, val packs: Int, val cost: Double)
data class Missing(val foodId: String, val lack: Double)
data class SessionPlan(
    val batch: Map<String, Int>,
    val need: Ing,
    val missing: List<Missing>,
    val fresh: List<Pair<Dish, Int>>,
)

/**
 * Tous les calculs dérivés de l'état (fonctions pures, testables sans Android).
 * `lv` = quantités des leviers une fois l'objectif calorique appliqué.
 */
class Calc private constructor(
    val s: AppState,
    val lv: Map<String, Double>,
    val baseKcal: Double,
    val clamped: Boolean,
) {
    val reachedKcal: Double by lazy { nutOf(weekAvgIng()).getValue("kcal") }

    /** Consommation moyenne par jour (sur 7 jours) de chaque aliment. */
    val daily: Map<String, Double> by lazy {
        val out = HashMap<String, Double>()
        for (d in s.dishes) {
            val n = weeklyCount(d.id)
            if (n == 0) continue
            for ((k, q) in effIng(d)) out[k] = (out[k] ?: 0.0) + q * n / s.config.period
        }
        out
    }

    private fun leverHits(l: Lever, d: Dish) =
        if (l.scope == "repas") d.group == GROUP_MEAL && l.item in d.ing else l.scope == d.id

    /** Ingrédients réels d'un plat : leviers de l'objectif et sauce inclus. */
    fun effIng(d: Dish): Ing {
        val ing = LinkedHashMap(d.ing)
        for (l in s.config.levers) {
            if (l.item !in s.foods) continue
            if (leverHits(l, d)) ing[l.item] = lv.getValue(l.id)
        }
        if (d.sauce) {
            val it = if (s.sauce == "fb") "fromage_blanc" else "creme"
            if (it in s.foods) ing[it] = (ing[it] ?: 0.0) + s.config.sauceQty
        }
        return ing
    }

    fun nutOf(ing: Ing): Nut {
        val t = HashMap<String, Double>()
        for (k in Nutrients.keys) t[k] = 0.0
        for ((id, q) in ing) {
            val f = s.foods[id] ?: continue
            val m = f.factor(q)
            for (k in Nutrients.keys) t[k] = t.getValue(k) + f.n(k) * m
        }
        return t
    }

    fun weeklyCount(dishId: String): Int {
        val d = s.dish(dishId) ?: return 0
        if (d.group == GROUP_DAILY) return s.plan.size
        return s.plan.sumOf { day -> day.count { it == dishId } }
    }

    fun dayIng(i: Int): Ing {
        val out = HashMap<String, Double>()
        val ids = s.plan[i] + s.dishes.filter { it.group == GROUP_DAILY }.map { it.id }
        for (id in ids) {
            val d = s.dish(id) ?: continue
            for ((k, q) in effIng(d)) out[k] = (out[k] ?: 0.0) + q
        }
        return out
    }

    fun weekAvgIng(): Ing {
        val out = HashMap<String, Double>()
        val n = s.plan.size
        for (i in 0 until n) for ((k, q) in dayIng(i)) out[k] = (out[k] ?: 0.0) + q / n
        return out
    }

    fun possible(d: Dish): Possible {
        val ing = effIng(d)
        if (ing.isEmpty()) return Possible(0, null)
        var n = Double.POSITIVE_INFINITY
        var limit: String? = null
        for ((k, q) in ing) {
            val c = if (q > 0) floor(s.stockOf(k) / q) else Double.POSITIVE_INFINITY
            if (c < n) { n = c; limit = k }
        }
        return Possible(if (n.isInfinite()) 0 else n.toInt(), limit)
    }

    fun daysLeft(foodId: String): Double {
        val d = daily[foodId] ?: return Double.POSITIVE_INFINITY
        return if (d > 0) s.stockOf(foodId) / d else Double.POSITIVE_INFINITY
    }

    fun buyList(days: Int): List<BuyRow> = s.foods.values.mapNotNull { f ->
        val need = (daily[f.id] ?: 0.0) * days - s.stockOf(f.id)
        if (need <= 0) return@mapNotNull null
        val packs = ceil(need / (if (f.pack > 0) f.pack else 1.0)).toInt()
        BuyRow(f.id, packs, packs * f.price)
    }

    fun sessionPlan(session: Session): SessionPlan {
        val batch = LinkedHashMap<String, Int>()
        val days = session.days.filter { it < s.plan.size }
        for (i in days) for (id in s.plan[i]) {
            val d = s.dish(id) ?: continue
            if (d.fresh) continue
            batch[id] = (batch[id] ?: 0) + 1
        }
        val need = LinkedHashMap<String, Double>()
        for ((id, n) in batch) for ((k, q) in effIng(s.dish(id)!!)) need[k] = (need[k] ?: 0.0) + q * n
        val missing = need.filter { (k, q) -> s.stockOf(k) < q }.map { (k, q) -> Missing(k, q - s.stockOf(k)) }
        val fresh = ArrayList<Pair<Dish, Int>>()
        for (i in days) for (id in s.plan[i]) {
            val d = s.dish(id)
            if (d != null && d.fresh && d.group == GROUP_MEAL) fresh += d to i
        }
        return SessionPlan(batch, need, missing, fresh)
    }

    /** Kcal par jour qu'ajoute 1 g de ce levier, compte tenu du plan. */
    fun leverKcalPerGram(l: Lever): Double {
        val f = s.foods[l.item] ?: return 0.0
        val days = s.plan.size
        var freq = 0.0
        for (d in s.dishes) if (leverHits(l, d)) freq += weeklyCount(d.id).toDouble() / days
        return freq * f.n("kcal") / 100
    }

    companion object {
        private fun baseLevers(s: AppState) = s.config.levers.associate { it.id to it.base }

        /** Construit les calculs de l'état, leviers réglés pour viser `s.target` (s'il y en a un). */
        fun of(s: AppState): Calc {
            val base = baseLevers(s)
            val c0 = Calc(s, base, 0.0, false)
            val baseKcal = c0.nutOf(c0.weekAvgIng()).getValue("kcal")
            val target = s.target ?: return Calc(s, base, baseKcal, false)

            val lv = base.toMutableMap()
            var delta = target - baseKcal
            for (l in s.config.levers.sortedBy { it.priority }) {
                if (abs(delta) < 1) break
                val id = l.id
                val bound = if (delta < 0) l.min else l.max
                val kpg = c0.leverKcalPerGram(l)
                if (kpg == 0.0) continue
                val room = max(0.0, if (delta < 0) lv.getValue(id) - bound else bound - lv.getValue(id))
                val grams = min(room, abs(delta) / kpg)
                lv[id] = lv.getValue(id) + sign(delta) * grams
                delta -= sign(delta) * grams * kpg
            }
            for (k in lv.keys.toList()) lv[k] = floor(lv.getValue(k) / 5 + 0.5) * 5
            return Calc(s, lv, baseKcal, abs(delta) > 25)
        }
    }
}
