package app.suical.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import app.suical.domain.Food
import app.suical.domain.Nutrients
import app.suical.domain.UNITS
import app.suical.llm.Prompts
import java.io.File

/** Un aliment dont aucun micronutriment n'a encore été renseigné. */
private fun Food.incomplete() = Nutrients.micros.all { n(it.key) == 0.0 }

/** Liste des aliments : recherche, filtre par rayon, puis fiche de l'aliment ouvert. */
@Composable
fun FoodsScreen(x: Ctx, vm: MainViewModel, open: String?, onOpen: (String?) -> Unit) {
    val food = open?.let { x.s.foods[it] }
    if (food != null) FoodDetail(x, food, vm) else FoodList(x, vm, onOpen)
}

@Composable
private fun FoodList(x: Ctx, vm: MainViewModel, onOpen: (String?) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var rayon by rememberSaveable { mutableStateOf("*") }
    val incomplete = x.s.foods.values.filter { it.incomplete() }
    val all = rayons(x.s.foods.values)
    val groups = all.map { (id, name) ->
        Triple(id, name, x.s.foods.values.filter { it.inRayon(id) && (rayon == "*" || rayon == id) && matchesQuery(it.name, query) }
            .sortedBy { normalizeText(it.name) })
    }.filter { it.third.isNotEmpty() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            SearchField(query, { query = it }, "Rechercher un aliment")
            Gap(12)
            ChipRow(listOf("*" to "Tous") + all, rayon) { rayon = it }
            Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Scanner un code-barres", modifier = Modifier.weight(1f)) {
                    scanBarcode(context, { code -> vm.addFoodFromBarcode(code) { onOpen(it) } }, { vm.say(it) })
                }
                GhostButton("Nouveau") { vm.newFood { onOpen(it) } }
            }
            Hint("Le scan cherche le produit sur Open Food Facts (connexion requise, seul moment où l'app utilise internet).",
                Modifier.padding(start = 4.dp, top = 6.dp))
        }
        if (incomplete.isNotEmpty() && query.isEmpty()) item {
            Panel(Modifier.padding(top = 16.dp)) {
                Text("${incomplete.size} aliment${if (incomplete.size > 1) "s" else ""} sans micronutriments", fontWeight = FontWeight.Bold)
                Hint(incomplete.joinToString(", ") { it.name }, Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Demander à l'IA", modifier = Modifier.weight(1f)) { vm.sendToLlm(context, Prompts.foodValues(incomplete)) }
                    GhostButton("Coller la réponse") { vm.pasteFoodValues(context, x.s.foods.keys.toList()) }
                }
            }
        }
        if (groups.isEmpty()) item {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hint("Aucun aliment ne correspond à « ${query.trim()} ».")
                if (query.isNotBlank()) PrimaryButton("Créer « ${query.trim()} »") {
                    vm.newFood(query.trim().replaceFirstChar { it.uppercase() }) { onOpen(it) }
                }
            }
        }
        for ((id, name, list) in groups) {
            item(key = "t-$id") { SectionTitle("$name · ${list.size}") }
            item(key = "p-$id") {
                Panel {
                    list.forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(f.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.SemiBold)
                                Hint(if (f.incomplete()) f.summary() + " · à compléter" else f.summary())
                            }
                            Hint(eur(f.price), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Fiche d'un aliment : identité, valeurs nutritionnelles, remplissage par l'IA. */
@Composable
private fun FoodDetail(x: Ctx, f: Food, vm: MainViewModel) {
    val context = LocalContext.current
    val per = if (f.perHundred) "pour 100 ${f.unit}" else "par " + (if (f.unit == "pc") "pièce" else f.unit)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var micros by rememberSaveable { mutableStateOf(false) }
    val usedIn = x.s.dishes.filter { f.id in it.ing }.map { it.name }
    var photo by remember { mutableStateOf<Pair<Uri, Food>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        photo?.let { (uri, food) -> if (ok) vm.sendToLlm(context, Prompts.foodValues(listOf(food), fromPhoto = true), uri) }
        photo = null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            TextField(f.name, "Nom", Modifier.fillMaxWidth()) { vm.saveFood(f.copy(name = it)) }
            Gap(12)
            Hint("Unité", Modifier.padding(bottom = 4.dp))
            Segmented(UNITS.map { it to if (it == "pc") "pièce" else it }, f.unit, fill = true) { vm.saveFood(f.copy(unit = it)) }
            Gap(12)
            Hint("Rayon", Modifier.padding(bottom = 4.dp))
            ChipRow(rayons(listOf(f)).filter { it.first.isNotEmpty() }, f.cat) { vm.saveFood(f.copy(cat = it)) }
            Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(f.pack, "Format (${unitLabel(f.unit)})", Modifier.weight(1f)) { vm.saveFood(f.copy(pack = it)) }
                NumField(f.price, "Prix du format (€)", Modifier.weight(1f)) { vm.saveFood(f.copy(price = it)) }
            }
            Gap(8)
            TextField(f.packLabel, "Nom du format", Modifier.fillMaxWidth()) { vm.saveFood(f.copy(packLabel = it)) }
        }

        SectionTitle("Valeurs $per")
        Panel {
            Nutrients.all.filter { it.ref == null }.chunked(2).forEach { pair ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { n ->
                        NumField(f.n(n.key), "${n.label} (${n.unit})", Modifier.weight(1f)) { vm.saveFood(f.copy(nut = f.nut + (n.key to it))) }
                    }
                }
            }
            Text((if (micros) "▾ " else "▸ ") + "Micronutriments", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { micros = !micros }.padding(vertical = 10.dp))
            if (micros) Nutrients.micros.chunked(2).forEach { pair ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { n ->
                        NumField(f.n(n.key), "${n.label} (${n.unit})", Modifier.weight(1f)) { vm.saveFood(f.copy(nut = f.nut + (n.key to it))) }
                    }
                    if (pair.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
            Hint("Corrige avec les valeurs de ton étiquette : tout le reste de l'app se recalcule.")
        }

        SectionTitle("Remplir plus vite")
        Panel {
            PrimaryButton("Demander à l'IA", modifier = Modifier.fillMaxWidth()) { vm.sendToLlm(context, Prompts.foodValues(listOf(f))) }
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Coller la réponse", Modifier.weight(1f)) { vm.pasteFoodValues(context, listOf(f.id)) }
                GhostButton("Photo d'étiquette", Modifier.weight(1f)) {
                    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "label-${System.currentTimeMillis()}.jpg"))
                    photo = uri to f
                    camera.launch(uri)
                }
            }
        }

        if (usedIn.isNotEmpty()) Hint("Utilisé dans : " + usedIn.joinToString(", "), Modifier.padding(horizontal = 4.dp))
        DangerButton("Supprimer cet aliment", Modifier.fillMaxWidth()) {
            if (usedIn.isNotEmpty()) vm.say("Utilisé dans : " + usedIn.joinToString(", ")) else confirmDelete = true
        }
    }
    if (confirmDelete) Confirm("Supprimer « ${f.name} » ?", "Son stock sera aussi supprimé.", "Supprimer",
        { confirmDelete = false }) { vm.deleteFood(f) }
}
