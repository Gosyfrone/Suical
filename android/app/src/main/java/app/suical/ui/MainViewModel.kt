package app.suical.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.suical.data.Backup
import app.suical.data.OpenFoodFacts
import app.suical.data.Repository
import app.suical.domain.AppState
import app.suical.domain.Calc
import app.suical.domain.DEFAULT_LLM_PKG
import app.suical.domain.Defaults
import app.suical.domain.Dish
import app.suical.domain.Food
import app.suical.domain.GROUP_MEAL
import app.suical.domain.LogEntry
import app.suical.domain.Nutrients
import app.suical.domain.PlanConfig
import app.suical.domain.Session
import app.suical.domain.todayEpochDay
import app.suical.llm.Change
import app.suical.llm.LlmBridge
import app.suical.llm.LlmImport
import app.suical.reminders.Reminders
import app.suical.widget.SuicalWidget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Un état + ses calculs, passés d'un bloc aux écrans. */
class Ctx(val s: AppState, val c: Calc)

/**
 * Toutes les actions de l'app. Chaque fonction lit l'état courant, calcule le changement avec le
 * domaine puis l'écrit via le [Repository] : les écrans ne font qu'appeler ces fonctions.
 */
class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    val ctx: StateFlow<Ctx?> = repo.state()
        .map { Ctx(it, Calc.of(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch { repo.seedIfNeeded() }
        // Widget et rappels suivent les changements de données et de réglages.
        viewModelScope.launch { ctx.filterNotNull().collect { SuicalWidget.refresh(app, it.s) } }
        viewModelScope.launch {
            ctx.filterNotNull().map { it.s.config }.distinctUntilChanged().collect { Reminders.sync(app, it) }
        }
    }

    fun say(msg: String) { _messages.tryEmit(msg) }
    private fun act(block: suspend (Ctx) -> Unit) {
        viewModelScope.launch { ctx.value?.let { block(it) } }
    }

    // ---- plats ----
    fun eat(d: Dish) = act { x -> if (repo.eat(x.s, x.c, d)) say("${d.name} : c'est noté") }

    fun undo(e: LogEntry) = act { x -> repo.undo(x.s, x.c, e); say("Repas annulé") }

    /** Repas hors plan : valeurs saisies à la main, sans toucher au stock. */
    fun addExtra(name: String, kcal: Double, p: Double, g: Double, l: Double) = act {
        val nut = Nutrients.keys.associateWith { 0.0 } + mapOf("kcal" to kcal, "p" to p, "g" to g, "l" to l)
        repo.addExtra(name.ifBlank { "Repas hors plan" }, nut)
        say("Repas ajouté")
    }

    fun newDish(onCreated: (String) -> Unit) = act { x ->
        val id = uniqueId("plat", x.s.dishes.map { it.id }.toSet())
        repo.saveDish(Dish(id, "Nouveau plat", GROUP_MEAL, sauce = false, fresh = false, ing = emptyMap()))
        onCreated(id)
    }

    fun saveDish(d: Dish) { viewModelScope.launch { repo.saveDish(d) } }

    fun deleteDish(d: Dish) {
        viewModelScope.launch { repo.deleteDish(d.id); say("Plat supprimé") }
    }

    // ---- semaine ----
    fun setPlan(day: Int, slot: Int, dishId: String) { viewModelScope.launch { repo.setPlan(day, slot, dishId) } }
    fun setTarget(t: Int?) { viewModelScope.launch { repo.setTarget(t) } }
    fun setSauce(v: String) { viewModelScope.launch { repo.setSauce(v) } }

    fun cook(session: Session) = act { x ->
        val p = x.c.sessionPlan(session)
        if (p.missing.isNotEmpty() || p.batch.isEmpty()) return@act
        repo.tx {
            p.need.forEach { (k, q) -> repo.setStock(k, x.s.stockOf(k) - q) }
            p.batch.forEach { (id, n) -> repo.setReady(id, (x.s.ready[id] ?: 0) + n) }
        }
        say("Portions ajoutées au frigo")
    }

    // ---- stock et courses ----
    fun setStock(foodId: String, qty: Double) { viewModelScope.launch { repo.setStock(foodId, qty) } }

    fun addPack(f: Food) = act { x -> repo.setStock(f.id, x.s.stockOf(f.id) + f.pack) }

    fun addOnePackOfEach() = act { x ->
        repo.tx { x.s.foods.values.forEach { repo.setStock(it.id, x.s.stockOf(it.id) + it.pack) } }
    }

    fun shoppingDone(days: Int) = act { x ->
        repo.tx {
            for (r in x.c.buyList(days)) {
                val f = x.s.foods.getValue(r.foodId)
                repo.setStock(f.id, x.s.stockOf(f.id) + r.packs * f.pack)
            }
        }
        say("Courses ajoutées au stock")
    }

    // ---- aliments ----
    fun newFood(name: String = "Nouvel aliment", onCreated: (String) -> Unit) = act { x ->
        val id = uniqueId(if (name == "Nouvel aliment") "aliment" else name, x.s.foods.keys)
        repo.saveFood(Food(id, name, "g", "epicerie", 1000.0, "1 kg", 0.0, Nutrients.keys.associateWith { 0.0 }))
        onCreated(id)
        say("Aliment créé, complète ses valeurs")
    }

    fun saveFood(f: Food) { viewModelScope.launch { repo.saveFood(f) } }

    fun deleteFood(f: Food) {
        viewModelScope.launch { repo.deleteFood(f.id); say("Aliment supprimé") }
    }

    /** Code-barres lu : récupère le produit sur Open Food Facts et le crée dans les aliments. */
    fun addFoodFromBarcode(code: String, onCreated: (String) -> Unit) = act { x ->
        say("Recherche du produit…")
        val found = try { OpenFoodFacts.fetch(code) } catch (e: Exception) { say("Pas de connexion"); return@act }
        if (found == null) { say("Produit introuvable (code $code)"); return@act }
        val id = uniqueId(found.name, x.s.foods.keys)
        repo.saveFood(found.copy(id = id))
        repo.setStock(id, 0.0)
        onCreated(id)
        say("« ${found.name} » ajouté. Les micronutriments manquants : bouton « Demander à l'IA ».")
    }

    /** Applique des valeurs nutritionnelles reçues de l'IA aux aliments correspondants. */
    fun applyFoodValues(values: Map<String, Map<String, Double>>) = act { x ->
        var n = 0
        repo.tx {
            for ((id, nut) in values) {
                val f = x.s.foods[id] ?: continue
                repo.saveFood(f.copy(nut = f.nut + nut)); n++
            }
        }
        say(if (n == 1) "1 aliment mis à jour" else "$n aliments mis à jour")
    }

    // ---- sauvegarde et réglages ----
    fun exportJson(): String = Backup.toJson(ctx.value!!.s)

    /** @return null si tout va bien, sinon le message d'erreur. */
    fun importJson(text: String, done: (String?) -> Unit) = act { x ->
        val err = try {
            repo.replaceAll(Backup.fromJson(text, x.s)); null
        } catch (e: Exception) {
            e.message ?: "Export illisible"
        }
        done(err)
    }

    fun resetAll() = act { x ->
        repo.replaceAll(Defaults.fresh().copy(llmPkg = x.s.llmPkg))
        say("Réinitialisé")
    }

    fun setLlmPkg(p: String) { viewModelScope.launch { repo.setLlmPkg(p.trim()) } }
    fun saveConfig(c: PlanConfig) { viewModelScope.launch { repo.setConfig(c) } }

    // ---- poids ----
    fun setWeight(kg: Double) { viewModelScope.launch { repo.setWeight(todayEpochDay(), kg); say("Poids enregistré") } }
    fun deleteWeight(epochDay: Long) { viewModelScope.launch { repo.deleteWeight(epochDay) } }

    // ---- IA ----
    /** Envoie le prompt à l'app IA choisie dans les réglages (il reste aussi dans le presse-papier). */
    fun sendToLlm(context: Context, prompt: String, image: Uri? = null) {
        val direct = LlmBridge.send(context, prompt, ctx.value?.s?.llmPkg ?: DEFAULT_LLM_PKG, image)
        say(if (direct) "Prompt envoyé (et copié)" else "Prompt copié : choisis ton app IA")
    }

    /** Lit la réponse JSON de l'IA dans le presse-papier et met à jour les aliments. */
    fun pasteFoodValues(context: Context, expected: List<String>) {
        try {
            applyFoodValues(LlmImport.parseFoodValues(LlmBridge.paste(context), expected))
        } catch (e: Exception) {
            say(e.message ?: "Réponse illisible")
        }
    }

    /** Crée le plat (et les nouveaux aliments éventuels) décrits dans la réponse de l'IA. */
    fun pasteRecipe(context: Context, onCreated: (String) -> Unit) = act { x ->
        val draft = try { LlmImport.parseRecipe(LlmBridge.paste(context)) } catch (e: Exception) {
            say(e.message ?: "Réponse illisible"); return@act
        }
        val foodIds = x.s.foods.keys.toMutableSet()
        val renamed = HashMap<String, String>()
        repo.tx {
            for (f in draft.newFoods) {
                if (f.id in x.s.foods) { renamed[f.id] = f.id; continue }
                val id = uniqueId(f.id.ifBlank { f.name }, foodIds)
                foodIds += id; renamed[f.id] = id
                repo.saveFood(f.copy(id = id)); repo.setStock(id, 0.0)
            }
            val ing = draft.dish.ing.filterKeys { (renamed[it] ?: it) in foodIds }.mapKeys { renamed[it.key] ?: it.key }
            val id = uniqueId(draft.dish.name, x.s.dishes.map { it.id }.toSet())
            repo.saveDish(draft.dish.copy(id = id, ing = ing))
            onCreated(id)
        }
        val unknown = draft.dish.ing.keys.count { (renamed[it] ?: it) !in foodIds }
        say("« ${draft.dish.name} » créé" + if (unknown > 0) " ($unknown ingrédient(s) inconnu(s) ignoré(s))" else "")
    }

    /** Applique des changements de quantités proposés par l'IA. */
    fun applyChanges(changes: List<Change>) = act { x ->
        var n = 0
        val edited = LinkedHashMap<String, Dish>() // plusieurs changements peuvent viser le même plat
        for (ch in changes) {
            val d = edited[ch.dishId] ?: x.s.dish(ch.dishId) ?: continue
            if (ch.foodId !in x.s.foods) continue
            edited[d.id] = d.copy(ing = if (ch.qty <= 0) d.ing - ch.foodId else d.ing + (ch.foodId to ch.qty))
            n++
        }
        repo.tx { edited.values.forEach { repo.saveDish(it) } }
        say(if (n == 0) "Rien à appliquer" else "$n changement(s) appliqué(s)")
    }

    private fun uniqueId(prefix: String, taken: Set<String>): String {
        val base = Normalizer.normalize(prefix.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifEmpty { "x" }
        var id = base
        var i = 2
        while (id in taken) id = base + "_" + i++
        return id
    }
}
