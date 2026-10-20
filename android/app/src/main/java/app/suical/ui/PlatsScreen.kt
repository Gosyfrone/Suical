package app.suical.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.platform.LocalContext
import app.suical.domain.Nutrients
import app.suical.domain.entryNut
import app.suical.llm.Prompts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.Dish
import app.suical.domain.GROUP_DAILY
import app.suical.domain.GROUP_MEAL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM 'à' HH:mm", Locale.FRANCE)

@Composable
fun PlatsScreen(x: Ctx, vm: MainViewModel) {
    val context = LocalContext.current
    var askRecipe by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item(key = "new") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Nouveau plat", modifier = Modifier.weight(1f)) { vm.newDish { editing = it } }
                GhostButton("Avec l'IA") { askRecipe = true }
            }
            Gap()
            GhostButton("Coller la recette de l'IA", Modifier.fillMaxWidth()) { vm.pasteRecipe(context) { editing = it } }
        }
        for ((group, title) in listOf(GROUP_MEAL to "Repas", GROUP_DAILY to "Tous les jours")) {
            val list = x.s.dishes.filter { it.group == group }
            item(key = "title-$group") { SectionTitle(title) }
            if (list.isEmpty()) item(key = "empty-$group") { Hint("Aucun plat dans cette catégorie.") }
            items(list, key = { it.id }) { d ->
                DishCard(x, d, editing == d.id, vm,
                    onToggle = { editing = if (editing == d.id) null else d.id },
                    onDeleted = { editing = null })
            }
        }
    }

    if (askRecipe) RecipeAskDialog({ askRecipe = false }) { vm.sendToLlm(context, Prompts.recipe(x.s, it)) }
}

@Composable
private fun RecipeAskDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recette avec l'IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("Décris ce que tu veux. L'IA connaît tes aliments et ton stock.")
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = { Text("ex. un dîner à 40 g de protéines, prêt en 15 min") })
            }
        },
        confirmButton = { TextButton({ onSend(text.ifBlank { "un repas équilibré" }); onDismiss() }) { Text("Envoyer") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun DishCard(x: Ctx, d: Dish, editing: Boolean, vm: MainViewModel, onToggle: () -> Unit, onDeleted: () -> Unit) {
    val pr = x.c.possible(d)
    val ready = x.s.ready[d.id] ?: 0
    val n = pr.n + ready
    val zero = n < 1
    val nut = x.c.nutOf(x.c.effIng(d))
    val status = when {
        zero -> "Manque : " + (x.s.foods[pr.limit]?.name?.lowercase() ?: "des ingrédients")
        ready > 0 -> "$ready ${if (ready > 1) "portions prêtes" else "portion prête"} au frigo"
        else -> "Possible $n ${if (n > 1) "fois" else "fois"} avec ton stock"
    }

    Panel(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Hint("${nf(nut.getValue("kcal"))} kcal · ${nf(nut.getValue("p"))} g de protéines")
                Hint(status, error = zero)
            }
            Text(if (editing) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton("Mangé", enabled = !zero, modifier = Modifier.weight(1f)) { vm.eat(d) }
            GhostButton(if (editing) "Fermer" else "Modifier", onClick = onToggle)
        }
        NutPanel(nut, "Valeurs nutritionnelles")
        if (editing) DishEditor(x, d, vm, onDeleted)
    }
}

@Composable
private fun DishEditor(x: Ctx, d: Dish, vm: MainViewModel, onDeleted: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Divider()
        TextField(d.name, "Nom", Modifier.fillMaxWidth()) { vm.saveDish(d.copy(name = it)) }
        Hint("Catégorie")
        Segmented(listOf(GROUP_MEAL to "Midi ou soir", GROUP_DAILY to "Tous les jours"), d.group, fill = true) { vm.saveDish(d.copy(group = it)) }
        Hint("Préparation")
        Segmented(listOf(false to "En lot", true to "Sur le moment"), d.fresh, fill = true) { vm.saveDish(d.copy(fresh = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crème ou fromage blanc dans les pâtes (${nf(x.s.config.sauceQty)} g)", Modifier.weight(1f))
            Switch(d.sauce, { vm.saveDish(d.copy(sauce = it)) })
        }
        SectionTitle("Ingrédients")
        if (d.ing.isEmpty()) Hint("Aucun ingrédient pour l'instant.")
        for ((k, q) in d.ing) {
            val f = x.s.foods[k]
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumField(q, (f?.name ?: k) + " (" + (f?.let { unitLabel(it.unit) } ?: "?") + ")", Modifier.weight(1f)) {
                    vm.saveDish(d.copy(ing = d.ing + (k to it)))
                }
                IconButton({ vm.saveDish(d.copy(ing = d.ing - k)) }) { Icon(Icons.Default.Clear, "Retirer " + (f?.name ?: k)) }
            }
        }
        PrimaryButton("+ Ajouter un ingrédient", modifier = Modifier.fillMaxWidth()) { picking = true }
        DangerButton("Supprimer ce plat", Modifier.fillMaxWidth()) { confirmDelete = true }
    }
    if (picking) FoodPickerSheet(x.s.foods.values, exclude = d.ing.keys, title = "Ajouter à « ${d.name} »",
        onPick = { f -> vm.saveDish(d.copy(ing = d.ing + (f.id to if (f.perHundred) 100.0 else 1.0))) }, onDismiss = { picking = false })
    if (confirmDelete) Confirm("Supprimer « ${d.name} » ?", "Il sera aussi retiré du planning.", "Supprimer",
        { confirmDelete = false }) { vm.deleteDish(d); onDeleted() }
}
