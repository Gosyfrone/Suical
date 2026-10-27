package app.suical.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

const val SUB_PLATS = "plats"
const val SUB_ALIMENTS = "aliments"
const val SUB_REGLAGES = "reglages"

fun subTitle(sub: String) = when (sub) {
    SUB_PLATS -> "Plats"
    SUB_ALIMENTS -> "Aliments"
    else -> "Réglages"
}

/** Onglet Plus : plats, aliments, réglages et sauvegarde. */
@Composable
fun PlusScreen(x: Ctx, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
        item { ScreenHeader("Plus") }
        item {
            SectionTitle("Ma cuisine")
            Panel {
                MenuRow("Plats", "${x.s.dishes.size} recettes, quantités et sauces") { onOpen(SUB_PLATS) }
                Divider()
                MenuRow("Aliments", "${x.s.foods.size} produits, valeurs nutritionnelles et prix") { onOpen(SUB_ALIMENTS) }
            }
            SectionTitle("Application")
            Panel {
                MenuRow("Réglages", "Pas, plan, rappels, sessions de cuisine, leviers, sauvegarde") { onOpen(SUB_REGLAGES) }
            }
            Hint("Tout est stocké sur ce téléphone.", Modifier.padding(top = 16.dp, start = 4.dp))
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Hint(subtitle)
        }
        Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
