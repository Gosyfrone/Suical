package app.suical.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.GROUP_MEAL

/** Cuisine en lot : on choisit combien de portions de chaque repas, l'app donne les ingrédients et leurs quantités. */
@Composable
fun CookScreen(x: Ctx, vm: MainViewModel) {
    val meals = x.s.dishes.filter { it.group == GROUP_MEAL }
    val batch = x.s.cook.filterKeys { id -> meals.any { it.id == id } }
    val total = batch.values.sum()
    val p = x.c.cookPlan(batch)

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            Hint("Choisis combien de portions de chaque plat tu prépares.", Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Reprendre le planning", Modifier.weight(1f)) { vm.cookFromPlan() }
                GhostButton("Tout à zéro", Modifier.weight(1f), enabled = total > 0) { vm.clearCook() }
            }
            SectionTitle("Plats")
        }
        if (meals.isEmpty()) item { Hint("Aucun repas pour l'instant : crée-en dans Plus > Plats.") }
        items(meals, key = { it.id }) { d ->
            val ready = x.s.ready[d.id] ?: 0
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(d.name, fontWeight = FontWeight.SemiBold)
                        val notes = listOfNotNull(
                            if (ready > 0) "$ready au frigo" else null,
                            if (d.fresh) "plutôt à cuisiner sur le moment" else null,
                        )
                        if (notes.isNotEmpty()) Hint(notes.joinToString(" · "))
                    }
                    Counter(d.name, batch[d.id] ?: 0) { vm.addCook(d.id, it) }
                }
                Divider()
            }
        }

        item { SectionTitle(if (total > 0) "Ingrédients pour $total portion" + (if (total > 1) "s" else "") else "Ingrédients") }
        if (p.need.isEmpty()) item { Hint("Ajoute des portions pour voir les ingrédients à sortir.") }
        items(p.need.entries.sortedByDescending { it.value }, key = { "ing-" + it.key }) { (k, q) ->
            val f = x.s.foods[k]
            val lack = q - x.s.stockOf(k)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f?.name ?: k, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtQty(f, q), fontWeight = FontWeight.Bold)
                    if (lack > 0) Hint("manque " + fmtQty(f, lack), error = true)
                }
            }
        }
        if (p.need.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (batch.keys.any { x.s.dish(it)?.sauce == true })
                    Hint("Ajoute la crème ou le fromage blanc au moment de réchauffer, pas avant.")
                Hint(
                    if (p.missing.isEmpty()) "Stock suffisant."
                    else "Il manque ${p.missing.size} ingrédient" + (if (p.missing.size > 1) "s" else "") + " dans le stock.",
                    error = p.missing.isNotEmpty())
                PrimaryButton("Cuisiné", enabled = p.missing.isEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { vm.cook() }
                Hint("Retire les ingrédients du stock et met les portions au frigo.", Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** − n + : nombre de portions d'un plat. */
@Composable
private fun Counter(name: String, n: Int, onChange: (Int) -> Unit) {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Une portion de moins de $name", enabled = n > 0) { onChange(-1) }
        Text(n.toString(), Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        StepButton("+", "Une portion de plus de $name") { onChange(1) }
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    }
}
