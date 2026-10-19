package app.suical.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.runtime.remember
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.suical.domain.CATS
import app.suical.domain.Food
import app.suical.domain.Nutrients
import app.suical.domain.UNITS
import app.suical.llm.Prompts

/** Un aliment dont aucun micronutriment n'a encore été renseigné. */
private fun Food.incomplete() = Nutrients.micros.all { n(it.key) == 0.0 }

@Composable
fun FoodsScreen(x: Ctx, vm: MainViewModel) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val incomplete = x.s.foods.values.filter { it.incomplete() }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            Hint("Corrige ici les valeurs avec celles de tes étiquettes : tout le reste de l'app se recalcule.",
                Modifier.padding(top = 4.dp))
            SectionTitle("Ajouter un produit")
            PrimaryButton("Scanner un code-barres") {
                scanBarcode(context, { code -> vm.addFoodFromBarcode(code) { open = it } }, { vm.say(it) })
            }
            Hint("Cherche le produit sur Open Food Facts (connexion requise, seul moment où l'app utilise internet).", Modifier.padding(top = 4.dp))
            SectionTitle("Valeurs avec l'IA")
            Hint(
                if (incomplete.isEmpty()) "Tous les aliments ont leurs micronutriments."
                else "${incomplete.size} aliment(s) sans micronutriments : " + incomplete.joinToString(", ") { it.name })
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (incomplete.isNotEmpty()) PrimaryButton("Demander à l'IA") { vm.sendToLlm(context, Prompts.foodValues(incomplete)) }
                GhostButton("Coller la réponse") { vm.pasteFoodValues(context, x.s.foods.keys.toList()) }
            }
            Hint("L'app IA s'ouvre avec la demande déjà écrite. Copie sa réponse (le JSON), reviens ici et appuie sur « Coller la réponse ».",
                Modifier.padding(top = 6.dp))
        }
        for (cat in CATS) {
            val foods = x.s.foods.values.filter { it.cat == cat.id }
            if (foods.isEmpty()) continue
            item(key = "cat-${cat.id}") { SectionTitle(cat.name) }
            items(foods, key = { it.id }) { f ->
                FoodRow(x, f, vm, open == f.id) { open = if (open == f.id) null else f.id }
            }
        }
        item {
            SectionTitle("Ajouter un aliment")
            PrimaryButton("Nouvel aliment") { vm.newFood { open = it } }
        }
    }
}

@Composable
private fun FoodRow(x: Ctx, f: Food, vm: MainViewModel, open: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val per = if (f.perHundred) "pour 100 ${f.unit}" else "par " + (if (f.unit == "pc") "pièce" else f.unit)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickableNoRipple(onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(f.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Hint("${nf(f.n("kcal"))} kcal $per")
        }
        if (open) FoodEditor(x, f, vm, context, per)
        Divider()
    }
}

@Composable
private fun FoodEditor(x: Ctx, f: Food, vm: MainViewModel, context: android.content.Context, per: String) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val usedIn = x.s.dishes.filter { f.id in it.ing }.map { it.name }
    var photo by remember { mutableStateOf<Pair<Uri, Food>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        photo?.let { (uri, food) -> if (ok) vm.sendToLlm(context, Prompts.foodValues(listOf(food), fromPhoto = true), uri) }
        photo = null
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Demander à l'IA") { vm.sendToLlm(context, Prompts.foodValues(listOf(f))) }
            GhostButton("Coller la réponse") { vm.pasteFoodValues(context, listOf(f.id)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Photo d'étiquette") {
                val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "label-${System.currentTimeMillis()}.jpg"))
                photo = uri to f
                camera.launch(uri)
            }
        }
        TextField(f.name, "Nom", Modifier.fillMaxWidth()) { vm.saveFood(f.copy(name = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(UNITS.map { it to if (it == "pc") "pièce" else it }, f.unit, Modifier.weight(1f), "Unité") { vm.saveFood(f.copy(unit = it)) }
            Choice(CATS.map { it.id to it.name }, f.cat, Modifier.weight(1f), "Rayon") { vm.saveFood(f.copy(cat = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(f.pack, "Format (${unitLabel(f.unit)})", Modifier.weight(1f)) { vm.saveFood(f.copy(pack = it)) }
            NumField(f.price, "Prix du format (€)", Modifier.weight(1f)) { vm.saveFood(f.copy(price = it)) }
        }
        TextField(f.packLabel, "Nom du format", Modifier.fillMaxWidth()) { vm.saveFood(f.copy(packLabel = it)) }
        Text("Valeurs $per", fontWeight = FontWeight.Bold)
        Nutrients.all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { n ->
                    NumField(f.n(n.key), "${n.label} (${n.unit})", Modifier.weight(1f)) {
                        vm.saveFood(f.copy(nut = f.nut + (n.key to it)))
                    }
                }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        if (usedIn.isNotEmpty()) Hint("Utilisé dans : " + usedIn.joinToString(", "))
        DangerButton("Supprimer") {
            if (usedIn.isNotEmpty()) vm.say("Utilisé dans : " + usedIn.joinToString(", ")) else confirmDelete = true
        }
    }
    if (confirmDelete) Confirm("Supprimer « ${f.name} » ?", "Son stock sera aussi supprimé.", "Supprimer",
        { confirmDelete = false }) { vm.deleteFood(f) }
}
