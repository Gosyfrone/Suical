package app.suical.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.WeightStats
import app.suical.domain.observedMaintenance
import app.suical.domain.todayEpochDay
import kotlin.math.abs
import kotlin.math.roundToInt

const val SUIVI_STATS = 0
const val SUIVI_WEIGHT = 1
const val SUIVI_MAINT = 2

/** Onglet Suivi : stats, poids et maintien sous un même sélecteur. */
@Composable
fun SuiviScreen(x: Ctx, vm: MainViewModel, section: Int, onSection: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp)) { ScreenHeader("Suivi") }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
            Segmented(listOf(SUIVI_STATS to "Stats", SUIVI_WEIGHT to "Poids", SUIVI_MAINT to "Maintien"), section, fill = true, onSelect = onSection)
        }
        when (section) {
            SUIVI_STATS -> StatsScreen(x)
            SUIVI_WEIGHT -> WeightScreen(x, vm) { onSection(SUIVI_MAINT) }
            else -> MaintenanceScreen(x, vm)
        }
    }
}

@Composable
private fun WeightScreen(x: Ctx, vm: MainViewModel, onMaintenance: () -> Unit) {
    val today = todayEpochDay()
    val span = 28
    val start = today - span + 1
    val weights = x.s.weights.filter { it.epochDay in start..today }
    val last = x.s.weights.maxByOrNull { it.epochDay }
    val trend = WeightStats.trendKgPerWeek(x.s.weights, today, span)
    val obs = x.c.observedMaintenance(today)

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            Panel(Modifier.padding(top = 8.dp)) {
                Hint(if (last == null) "Aucune pesée" else "Dernière pesée")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (last == null) "—" else nf(last.kg, 1), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    if (trend != null) {
                        val down = trend < 0
                        Text((if (abs(trend) < 0.005) "0" else (if (down) "−" else "+") + nf(abs(trend), 2)) + " kg / sem",
                            color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
                if (last != null) Text("kg", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            if (obs != null) {
                Panel(Modifier.padding(top = 12.dp)) {
                    Hint("D'après ton poids et tes repas")
                    Text("Maintien réel : ~${nf(((obs.kcal / 5).roundToInt() * 5).toDouble())} kcal", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    GhostButton("Voir mon maintien", Modifier.fillMaxWidth().padding(top = 10.dp), onClick = onMaintenance)
                }
            }
            WeightSection(x, vm, start, today, span, weights)
        }
    }
}
