package app.suical.ui

import androidx.compose.foundation.background
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
fun DishesScreen(x: Ctx, vm: MainViewModel) {
    val context = LocalContext.current
    var askRecipe by rememberSaveable { mutableStateOf(false) }
    var extra by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
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
        item(key = "new") {
            SectionTitle("Créer un plat")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Nouveau plat") { vm.newDish { editing = it } }
                GhostButton("Avec l'IA") { askRecipe = true }
            }
            Gap()
            GhostButton("Coller la recette de l'IA") { vm.pasteRecipe(context) { editing = it } }
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todays = x.s.log.filter { Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() == today }
        item(key = "today") {
            SectionTitle("Aujourd'hui")
            if (todays.isEmpty()) Hint("Aucun repas enregistré aujourd'hui.")
            else {
                val t = Nutrients.keys.associateWith { 0.0 }.toMutableMap()
                for (e in todays) for ((k, v) in x.c.entryNut(e)) t[k] = t.getValue(k) + v
                MacroRow(t)
                NutPanel(t, "Détail des micronutriments du jour", withMacros = false)
            }
            Gap()
            GhostButton("Ajouter un repas hors plan") { extra = true }
            SectionTitle("Derniers repas")
            if (x.s.log.isEmpty()) Hint("Appuie sur « Mangé » après un repas.")
        }
        items(x.s.log.take(15), key = { "log-${it.id}" }) { e ->
            val name = x.s.dish(e.dishId)?.name ?: e.name.ifEmpty { return@items }
            val extraKcal = if (e.custom.isNotEmpty()) " · ${nf(e.custom["kcal"] ?: 0.0)} kcal" else ""
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name)
                    Hint(Instant.ofEpochMilli(e.t).atZone(zone).format(whenFmt) + extraKcal)
                }
                TextButton({ vm.undo(e) }) { Text("Annuler") }
            }
        }
    }

    if (askRecipe) RecipeAskDialog({ askRecipe = false }) { vm.sendToLlm(context, Prompts.recipe(x.s, it)) }
    if (extra) ExtraMealDialog({ extra = false }) { n, k, p, g, l -> vm.addExtra(n, k, p, g, l) }
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
private fun ExtraMealDialog(onDismiss: () -> Unit, onAdd: (String, Double, Double, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableDoubleStateOf(0.0) }
    var p by rememberSaveable { mutableDoubleStateOf(0.0) }
    var g by rememberSaveable { mutableDoubleStateOf(0.0) }
    var l by rememberSaveable { mutableDoubleStateOf(0.0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repas hors plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("Compté dans les statistiques du jour, sans toucher au stock.")
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nom (restaurant, snack…)") }, singleLine = true)
                NumField(kcal, "Calories (kcal)", Modifier.fillMaxWidth()) { kcal = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(p, "Prot. (g)", Modifier.weight(1f)) { p = it }
                    NumField(g, "Gluc. (g)", Modifier.weight(1f)) { g = it }
                    NumField(l, "Lip. (g)", Modifier.weight(1f)) { l = it }
                }
            }
        },
        confirmButton = { TextButton({ onAdd(name.trim(), kcal, p, g, l); onDismiss() }, enabled = kcal > 0) { Text("Ajouter") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}
@Composable
private fun DishCard(x: Ctx, d: Dish, editing: Boolean, vm: MainViewModel, onToggle: () -> Unit, onDeleted: () -> Unit) {
    val pr = x.c.possible(d)
    val ready = x.s.ready[d.id] ?: 0
    val n = pr.n + ready
    val zero = n < 1
    val ingTxt = x.c.effIng(d).entries.joinToString(", ") { (k, q) ->
        val f = x.s.foods[k]
        fmtQty(f, q) + " " + (f?.name?.lowercase() ?: k)
    }.ifEmpty { "Aucun ingrédient" }
    val limitTxt = when {
        ready > 0 -> "$ready ${if (ready > 1) "portions prêtes" else "portion prête"} au frigo"
        pr.limit != null -> (if (zero) "Manque : " else "Limité par : ") + (x.s.foods[pr.limit]?.name?.lowercase() ?: pr.limit)
        else -> ""
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.size(70.dp).background(
                    if (zero) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondary,
                    RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val c = if (zero) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondary
                Text(nf(n.toDouble()), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = c)
                Text("fois", fontSize = 11.sp, color = c)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(d.name, fontWeight = FontWeight.Bold)
                Hint(ingTxt)
                if (limitTxt.isNotEmpty()) Hint(limitTxt, error = zero)
            }
            Button({ vm.eat(d) }, enabled = !zero, shape = RoundedCornerShape(50)) { Text("Mangé", fontWeight = FontWeight.Bold) }
        }
        NutPanel(x.c.nutOf(x.c.effIng(d)), "Valeurs nutritionnelles")
        GhostButton(if (editing) "Fermer" else "Modifier", onClick = onToggle)
        if (editing) DishEditor(x, d, vm, onDeleted)
        Divider()
    }
}

@Composable
private fun DishEditor(x: Ctx, d: Dish, vm: MainViewModel, onDeleted: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(d.name, "Nom", Modifier.fillMaxWidth()) { vm.saveDish(d.copy(name = it)) }
        Choice(listOf(GROUP_MEAL to "Repas (midi ou soir)", GROUP_DAILY to "Tous les jours"), d.group, label = "Catégorie") {
            vm.saveDish(d.copy(group = it))
        }
        Choice(listOf(false to "Se prépare en lot", true to "Se cuisine sur le moment"), d.fresh, label = "Préparation") {
            vm.saveDish(d.copy(fresh = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crème ou fromage blanc dans les pâtes (60 g)", Modifier.weight(1f))
            Switch(d.sauce, { vm.saveDish(d.copy(sauce = it)) })
        }
        Text("Ingrédients", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((k, q) in d.ing) {
            val f = x.s.foods[k]
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumField(q, (f?.name ?: k) + " (" + (f?.let { unitLabel(it.unit) } ?: "?") + ")", Modifier.weight(1f)) {
                    vm.saveDish(d.copy(ing = d.ing + (k to it)))
                }
                TextButton({ vm.saveDish(d.copy(ing = d.ing - k)) }) { Text("Retirer") }
            }
        }
        val addable = x.s.foods.values.filter { it.id !in d.ing }
        if (addable.isNotEmpty()) {
            Choice(listOf("" to "Ajouter un ingrédient…") + addable.map { it.id to it.name }, "") {
                if (it.isNotEmpty()) vm.saveDish(d.copy(ing = d.ing + (it to 100.0)))
            }
        }
        DangerButton("Supprimer ce plat") { confirmDelete = true }
    }
    if (confirmDelete) Confirm("Supprimer « ${d.name} » ?", "Il sera aussi retiré du planning.", "Supprimer",
        { confirmDelete = false }) { vm.deleteDish(d); onDeleted() }
}
