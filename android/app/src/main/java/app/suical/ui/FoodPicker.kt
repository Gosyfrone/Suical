package app.suical.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.CATS
import app.suical.domain.Food

/** Rayons dans l'ordre d'affichage, plus « Autres » pour un aliment dont le rayon est inconnu. */
fun rayons(foods: Collection<Food>): List<Pair<String, String>> {
    val known = CATS.map { it.id to it.name }
    val orphans = foods.any { f -> CATS.none { it.id == f.cat } }
    return if (orphans) known + ("" to "Autres") else known
}

fun Food.inRayon(rayon: String) = if (rayon.isEmpty()) CATS.none { it.id == cat } else cat == rayon

fun Food.summary(): String {
    val per = if (perHundred) "pour 100 $unit" else "par " + (if (unit == "pc") "pièce" else unit)
    return "${nf(n("kcal"))} kcal · ${nf(n("p"))} g de protéines $per"
}

/**
 * Choix d'un aliment : recherche, filtre par rayon et liste rangée par rayon.
 * `exclude` = aliments déjà pris (masqués).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSheet(
    foods: Collection<Food>,
    exclude: Set<String> = emptySet(),
    title: String = "Choisir un aliment",
    onPick: (Food) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var rayon by rememberSaveable { mutableStateOf("*") } // "*" = tous
    val available = foods.filter { it.id !in exclude }
    val all = rayons(available)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp))
            SearchField(query, { query = it }, "Rechercher un aliment", Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp)) { ChipRow(listOf("*" to "Tous") + all, rayon) { rayon = it } }
            val shown = all.map { (id, name) ->
                Triple(id, name, available.filter { it.inRayon(id) && (rayon == "*" || rayon == id) && matchesQuery(it.name, query) }.sortedBy { normalizeText(it.name) })
            }.filter { it.third.isNotEmpty() }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
                if (shown.isEmpty()) item {
                    Hint(if (available.isEmpty()) "Tous les aliments sont déjà dans ce plat." else "Aucun aliment ne correspond à « ${query.trim()} ».",
                        Modifier.padding(4.dp))
                }
                for ((id, name, list) in shown) {
                    item(key = "t-$id") { SectionTitle(name) }
                    item(key = "p-$id") {
                        Panel(Modifier.fillMaxWidth()) {
                            list.forEachIndexed { i, f ->
                                if (i > 0) Divider()
                                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(f); onDismiss() }.padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.Center) {
                                    Text(f.name, fontWeight = FontWeight.SemiBold)
                                    Hint(f.summary())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bouton qui montre l'aliment choisi et ouvre le sélecteur. */
@Composable
fun FoodPickerField(label: String, foods: Collection<Food>, selected: String, modifier: Modifier = Modifier, onPick: (Food) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Hint(label, Modifier.padding(bottom = 2.dp))
        OutlinedButton({ open = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Text(foods.firstOrNull { it.id == selected }?.name ?: "Choisir…", maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (open) FoodPickerSheet(foods, title = label, onPick = onPick, onDismiss = { open = false })
}
