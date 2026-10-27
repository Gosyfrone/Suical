package app.suical.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.Food
import app.suical.domain.LogEntry
import app.suical.domain.Nutrients
import app.suical.domain.SLOTS
import app.suical.domain.entryNut
import app.suical.domain.recentEntries
import app.suical.llm.EstimatedItem
import app.suical.llm.LlmBridge
import app.suical.llm.LlmImport
import app.suical.llm.MealEstimate
import app.suical.llm.Prompts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sheetDateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/** Étapes de la fenêtre d'ajout. */
sealed interface AddStep {
    data object Browse : AddStep
    /** Un plat (ou une combinaison d'aliments) dont on ajuste les ingrédients pour ce repas seulement. */
    data class Items(val dishId: String, val name: String, val ing: Map<String, Double>) : AddStep
    data class OneFood(val foodId: String, val qty: Double) : AddStep
    data class Free(val name: String = "", val nut: Map<String, Double> = emptyMap()) : AddStep
    /** Décrire (ou photographier) un plat inconnu et l'envoyer à l'IA. */
    data object Ai : AddStep
    /** Réponse de l'IA à relire et corriger avant d'ajouter. */
    data class Estimate(val est: MealEstimate) : AddStep
}

private const val TAB_RECENT = 0
private const val TAB_DISHES = 1
private const val TAB_FOODS = 2

/**
 * Ajout à un moment d'un jour : récents, plats, aliments (avec quantité), scan ou saisie libre.
 * `stockDefault` : position de départ de « prendre dans mon stock » (activé pour un repas prévu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(x: Ctx, vm: MainViewModel, day: Long, slot: Int, start: AddStep = AddStep.Browse, stockDefault: Boolean = false, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(start) }
    var fromStock by rememberSaveable { mutableStateOf(stockDefault) }
    val back = { if (start == AddStep.Browse && step != AddStep.Browse) step = AddStep.Browse else onDismiss() }
    val where = SLOTS.first { it.first == slot }.second + " · " + LocalDate.ofEpochDay(day).format(sheetDateFmt)
    fun add(e: LogEntry) { vm.logEntry(e.copy(t = System.currentTimeMillis(), day = day, slot = slot), fromStock); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding().navigationBarsPadding()) {
            when (val s = step) {
                AddStep.Browse -> Browse(x, vm, where, onDismiss) { step = it }
                is AddStep.Items -> StepScaffold(s.name, where, back) {
                    ItemsStep(x, s, fromStock, { fromStock = it }) { ing -> add(LogEntry(0, s.dishId, 0, false, ing, name = s.name)) }
                }
                is AddStep.OneFood -> {
                    val f = x.s.foods[s.foodId]
                    StepScaffold(f?.name ?: "Aliment", where, back) {
                        if (f == null) Hint("Chargement de l'aliment…")
                        else FoodStep(x, f, s.qty, fromStock, { fromStock = it }) { q -> add(LogEntry(0, "", 0, false, mapOf(f.id to q), name = f.name)) }
                    }
                }
                is AddStep.Free -> StepScaffold("Saisie libre", where, back) {
                    FreeStep(s) { name, nut -> add(LogEntry(0, "", 0, false, emptyMap(), name.ifBlank { "Repas hors plan" }, nut)) }
                }
                AddStep.Ai -> StepScaffold("Estimer avec l'IA", where, back) { AiStep(x, vm) { step = AddStep.Estimate(it) } }
                is AddStep.Estimate -> StepScaffold("Estimation de l'IA", where, back) {
                    EstimateStep(x, s.est, fromStock, { fromStock = it }) { name, ing, custom ->
                        add(LogEntry(0, "", 0, false, ing, name.ifBlank { "Repas estimé" }, custom))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Browse(x: Ctx, vm: MainViewModel, where: String, onDismiss: () -> Unit, go: (AddStep) -> Unit) {
    val context = LocalContext.current
    val recents = remember(x.s.log) { recentEntries(x.s.log) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(if (recents.isEmpty()) TAB_DISHES else TAB_RECENT) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Ajouter", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Hint(where)
        }
        SearchField(query, { query = it }, "Chercher un plat ou un aliment")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Scanner", Modifier.weight(1f)) {
                scanBarcode(context, { code -> vm.addFoodFromBarcode(code) { go(AddStep.OneFood(it, 0.0)) } }, { vm.say(it) })
            }
            GhostButton("Saisie libre", Modifier.weight(1f)) { go(AddStep.Free()) }
        }
        AccentButton("Plat inconnu ? Estimer avec l'IA", modifier = Modifier.fillMaxWidth()) { go(AddStep.Ai) }
        if (query.isBlank()) ChipRow(listOf(TAB_RECENT to "Récents", TAB_DISHES to "Plats", TAB_FOODS to "Aliments"), tab) { tab = it }
    }

    val searching = query.isNotBlank()
    val dishes = if (searching || tab == TAB_DISHES) x.s.dishes.filter { matchesQuery(it.name, query) } else emptyList()
    val foods = if (searching || tab == TAB_FOODS) x.s.foods.values.filter { matchesQuery(it.name, query) }.sortedBy { normalizeText(it.name) } else emptyList()

    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
        if (!searching && tab == TAB_RECENT) {
            if (recents.isEmpty()) item { Hint("Rien de noté pour l'instant.", Modifier.padding(4.dp)) }
            items(recents, key = { "r" + it.id }) { e ->
                val dish = x.s.dish(e.dishId)
                val food = if (e.dishId.isEmpty() && e.custom.isEmpty() && e.ing.size == 1) x.s.foods[e.ing.keys.first()] else null
                val sub = when {
                    food != null -> fmtQty(food, e.ing.values.first()) + " · " + nf(x.c.entryNut(e).getValue("kcal")) + " kcal"
                    dish != null -> "Plat · " + nf(x.c.nutOf(x.c.effIng(dish)).getValue("kcal")) + " kcal"
                    else -> nf(x.c.entryNut(e).getValue("kcal")) + " kcal"
                }
                PickRow(dish?.name ?: e.name.ifEmpty { "Repas" }, sub) {
                    go(when {
                        dish != null -> AddStep.Items(dish.id, dish.name, x.c.effIng(dish))
                        food != null -> AddStep.OneFood(food.id, e.ing.values.first())
                        e.custom.isNotEmpty() && e.ing.isNotEmpty() ->
                            AddStep.Estimate(MealEstimate(e.name, e.ing, listOf(EstimatedItem("Part estimée", 1.0, false, e.custom))))
                        e.custom.isNotEmpty() -> AddStep.Free(e.name, e.custom)
                        else -> AddStep.Items("", e.name, e.ing)
                    })
                }
            }
        }
        if (dishes.isNotEmpty()) {
            if (searching) item(key = "td") { SectionTitle("Plats") }
            items(dishes, key = { "d" + it.id }) { d ->
                PickRow(d.name, nf(x.c.nutOf(x.c.effIng(d)).getValue("kcal")) + " kcal la portion") { go(AddStep.Items(d.id, d.name, x.c.effIng(d))) }
            }
        }
        if (foods.isNotEmpty()) {
            if (searching) item(key = "tf") { SectionTitle("Aliments") }
            items(foods, key = { "f" + it.id }) { f -> PickRow(f.name, f.summary()) { go(AddStep.OneFood(f.id, defaultQty(f))) } }
        }
        if (searching && dishes.isEmpty() && foods.isEmpty()) item {
            Hint("Rien ne correspond à « ${query.trim()} ». Essaie la saisie libre.", Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun PickRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Hint(sub)
    }
    Divider()
}

/** En-tête avec retour, puis le contenu de l'étape qui défile. */
@Composable
private fun ColumnScope.StepScaffold(title: String, where: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(back) { Icon(Icons.Default.ArrowBack, "Retour") }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Hint(where)
        }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

private val PORTIONS = listOf(0.5 to "½", 1.0 to "1", 1.5 to "1½", 2.0 to "2")

@Composable
private fun ItemsStep(x: Ctx, s: AddStep.Items, fromStock: Boolean, onStock: (Boolean) -> Unit, onAdd: (Map<String, Double>) -> Unit) {
    var ing by remember(s) { mutableStateOf(s.ing) }
    var portion by remember(s) { mutableDoubleStateOf(1.0) }
    MacroRow(x.c.nutOf(ing))
    if (s.dishId.isNotEmpty()) {
        Hint("Portions")
        Segmented(PORTIONS, portion, fill = true) { p ->
            ing = ing.mapValues { it.value * p / portion }
            portion = p
        }
    }
    IngredientsEditor(x, ing) { ing = it }
    if (s.dishId.isNotEmpty()) Hint("Ces changements ne valent que pour ce repas : le plat reste tel quel.")
    StockSwitch(fromStock, onChange = onStock)
    PrimaryButton("Ajouter au journal", enabled = ing.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { onAdd(ing) }
}

/** Quantités rapides selon l'unité de l'aliment. */
private fun quickQty(f: Food) = when (f.unit) {
    "g", "ml" -> listOf(30.0, 50.0, 100.0, 150.0, 200.0, 250.0)
    "pc" -> listOf(1.0, 2.0, 3.0, 4.0)
    else -> listOf(0.5, 1.0, 2.0)
}

private fun defaultQty(f: Food?) = if (f == null || f.perHundred) 100.0 else 1.0

@Composable
private fun FoodStep(x: Ctx, f: Food, start: Double, fromStock: Boolean, onStock: (Boolean) -> Unit, onAdd: (Double) -> Unit) {
    var qty by remember(f.id) { mutableDoubleStateOf(if (start > 0) start else defaultQty(f)) }
    Hint(f.summary())
    MacroRow(x.c.nutOf(mapOf(f.id to qty)))
    NumField(qty, "Quantité (${unitLabel(f.unit)})", Modifier.fillMaxWidth()) { qty = it }
    ChipRow(quickQty(f).map { it to fmtQty(f, it) }, qty) { qty = it }
    StockSwitch(fromStock, "Retire cette quantité du stock", onStock)
    PrimaryButton("Ajouter au journal", enabled = qty > 0, modifier = Modifier.fillMaxWidth()) { onAdd(qty) }
}

/** Repas hors plan : valeurs saisies à la main, comptées dans les stats sans toucher au stock. */
@Composable
private fun FreeStep(s: AddStep.Free, onAdd: (String, Map<String, Double>) -> Unit) {
    var name by rememberSaveable { mutableStateOf(s.name) }
    var kcal by rememberSaveable { mutableDoubleStateOf(s.nut["kcal"] ?: 0.0) }
    var p by rememberSaveable { mutableDoubleStateOf(s.nut["p"] ?: 0.0) }
    var g by rememberSaveable { mutableDoubleStateOf(s.nut["g"] ?: 0.0) }
    var l by rememberSaveable { mutableDoubleStateOf(s.nut["l"] ?: 0.0) }
    Hint("Tu saisis les valeurs toi-même (restaurant, plat inconnu…).")
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField(kcal, "Calories (kcal)", Modifier.weight(1f)) { kcal = it }
        NumField(p, "Protéines (g)", Modifier.weight(1f)) { p = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField(g, "Glucides (g)", Modifier.weight(1f)) { g = it }
        NumField(l, "Lipides (g)", Modifier.weight(1f)) { l = it }
    }
    PrimaryButton("Ajouter au journal", enabled = kcal > 0, modifier = Modifier.fillMaxWidth()) {
        onAdd(name.trim(), Nutrients.keys.associateWith { 0.0 } + mapOf("kcal" to kcal, "p" to p, "g" to g, "l" to l))
    }
}

/** Description libre et/ou photo, envoyées à l'app IA ; sa réponse revient par le presse-papier. */
@Composable
private fun AiStep(x: Ctx, vm: MainViewModel, onEstimate: (MealEstimate) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var sent by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        photo?.let { if (ok) { vm.sendToLlm(context, Prompts.mealEstimate(x.s, text, withPhoto = true), it); sent = true } }
        photo = null
    }
    Hint("Décris ce que tu as mangé, sa taille et ce qui change par rapport à d'habitude. L'IA répond avec une estimation que tu pourras corriger.")
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp),
        label = { Text("Ex. : kebab galette, sauce blanche, sans frites") })
    PrimaryButton("1. Envoyer à l'IA", enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        vm.sendToLlm(context, Prompts.mealEstimate(x.s, text, withPhoto = false)); sent = true
    }
    GhostButton("1. Ou prendre une photo du plat", Modifier.fillMaxWidth()) {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "meal-${System.currentTimeMillis()}.jpg"))
        photo = uri
        camera.launch(uri)
    }
    Hint(if (sent) "Dans l'app IA, copie sa réponse, puis reviens ici." else "La réponse de l'IA se copie puis se colle ici.")
    AccentButton("2. Coller la réponse de l'IA", modifier = Modifier.fillMaxWidth()) {
        try {
            onEstimate(LlmImport.parseMealEstimate(LlmBridge.paste(context), x.s.foods.keys))
        } catch (e: Exception) {
            vm.say(e.message ?: "Réponse illisible")
        }
    }
}

/** Relecture de l'estimation : nom, quantités des éléments estimés et de mes aliments, avant l'ajout. */
@Composable
private fun EstimateStep(
    x: Ctx, est: MealEstimate, fromStock: Boolean, onStock: (Boolean) -> Unit,
    onAdd: (String, Map<String, Double>, Map<String, Double>) -> Unit,
) {
    var name by remember(est) { mutableStateOf(est.name) }
    var ing by remember(est) { mutableStateOf(est.ing) }
    var others by remember(est) { mutableStateOf(est.others.map { it to it.qty }) }
    val custom = Nutrients.keys.associateWith { k -> others.sumOf { (item, q) -> item.nutFor(q)[k] ?: 0.0 } }
    val mine = x.c.nutOf(ing)
    MacroRow(Nutrients.keys.associateWith { mine.getValue(it) + custom.getValue(it) })
    if (est.note.isNotEmpty()) Hint("Hypothèses de l'IA : ${est.note}")
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true)
    if (others.isNotEmpty()) {
        Hint("Estimé par l'IA")
        others.forEachIndexed { i, (item, q) ->
            key(i, item.name) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumField(q, item.name + if (item.grams) " (g)" else " (portion)", Modifier.weight(1f)) { v ->
                        others = others.toMutableList().also { it[i] = item to v }
                    }
                    Text(nf(item.nutFor(q)["kcal"] ?: 0.0) + " kcal", Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                    IconButton({ others = others.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Default.Clear, "Retirer " + item.name) }
                }
            }
        }
    }
    IngredientsEditor(x, ing) { ing = it }
    Hint("Les valeurs suivent les quantités que tu corriges.")
    if (ing.isNotEmpty()) StockSwitch(fromStock, "Retire mes aliments de cette liste du stock", onStock)
    PrimaryButton("Ajouter au journal", enabled = ing.isNotEmpty() || others.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        onAdd(name.trim(), ing, if (others.isEmpty()) emptyMap() else custom)
    }
}

@Composable
fun StockSwitch(on: Boolean, hint: String = "Retire une portion prête ou les ingrédients", onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Prendre dans mon stock", fontWeight = FontWeight.SemiBold)
            Hint(hint)
        }
        Switch(on, onChange)
    }
}

/** Liste d'ingrédients modifiable (quantités, retrait, ajout). Ne touche à rien : `onChange` reçoit la nouvelle liste. */
@Composable
fun IngredientsEditor(x: Ctx, ing: Map<String, Double>, onChange: (Map<String, Double>) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hint("Ingrédients")
        if (ing.isEmpty()) Hint("Aucun ingrédient.")
        for ((k, q) in ing) key(k) {
            val f = x.s.foods[k]
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumField(q, (f?.name ?: k) + " (" + (f?.let { unitLabel(it.unit) } ?: "?") + ")", Modifier.weight(1f)) { onChange(ing + (k to it)) }
                IconButton({ onChange(ing - k) }) { Icon(Icons.Default.Clear, "Retirer " + (f?.name ?: k)) }
            }
        }
        GhostButton("+ Ajouter un ingrédient", Modifier.fillMaxWidth()) { picking = true }
    }
    if (picking) FoodPickerSheet(x.s.foods.values, exclude = ing.keys, title = "Ajouter un ingrédient",
        onPick = { f -> onChange(ing + (f.id to defaultQty(f))) }, onDismiss = { picking = false })
}
