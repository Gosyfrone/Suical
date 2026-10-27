package app.suical.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.suical.domain.Nutrients
import app.suical.domain.PeriodStats
import app.suical.domain.Weight
import app.suical.domain.WeightStats
import app.suical.domain.dailyTotals
import app.suical.domain.periodStats
import app.suical.domain.todayEpochDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
private val longFmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.FRANCE)
private fun dayText(epochDay: Long, withYear: Boolean = false) =
    LocalDate.ofEpochDay(epochDay).format(if (withYear) longFmt else shortFmt)

private fun signed(v: Double, d: Int = 0): String {
    val text = nf(abs(v), d)
    return if (text == "0") "0" else (if (v > 0) "+" else "−") + text
}

/** Durées proposées en jours ; 0 = tout l'historique. */
private val RANGES = listOf(7 to "7 j", 14 to "14 j", 30 to "30 j", 90 to "3 mois", 0 to "Tout")

@Composable
fun StatsScreen(x: Ctx) {
    var range by rememberSaveable { mutableIntStateOf(14) }
    var offset by rememberSaveable { mutableIntStateOf(0) } // nombre de périodes en arrière
    val today = todayEpochDay()

    val firstDay = minOf(
        x.s.log.minOfOrNull { it.day } ?: today,
        x.s.weights.minOfOrNull { it.epochDay } ?: today,
    )
    val all = range == 0
    val span = if (all) (today - firstDay + 1).toInt().coerceAtLeast(7) else range
    val end = if (all) today else today - offset.toLong() * range
    val start = end - span + 1
    val longRange = span >= 90

    val totals = x.c.dailyTotals(start, end)
    val avg = Nutrients.keys.associateWith { k -> if (totals.isEmpty()) 0.0 else totals.values.sumOf { it.getValue(k) } / totals.size }
    val target = (x.s.target ?: (x.c.baseKcal / 50).roundToInt() * 50).toDouble()
    val weights = x.s.weights.filter { it.epochDay in start..end }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            Box(Modifier.padding(top = 8.dp)) { Segmented(RANGES, range, fill = true) { range = it; offset = 0 } }

            // Navigation dans le temps
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ offset++ }, enabled = !all) { Icon(Icons.Default.ArrowBack, "Période précédente") }
                Text(
                    dayText(start, longRange) + " – " + dayText(end, longRange),
                    Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                IconButton({ offset-- }, enabled = !all && offset > 0) { Icon(Icons.Default.ArrowForward, "Période suivante") }
            }

            SectionTitle("Calories et poids")
            if (totals.isEmpty() && weights.isEmpty()) {
                Hint("Rien de noté sur cette période. Appuie sur « Mangé » dans l'onglet Aujourd'hui, ou enregistre ton poids dans Suivi > Poids.")
            } else {
                CombinedChart(start, end, totals.mapValues { it.value.getValue("kcal") }, target, weights)
            }

            if (!all) Comparison(x, start, end, span)

            if (totals.isNotEmpty()) {
                SectionTitle("Moyennes des jours notés")
                Hint("${totals.size} jour(s) notés sur $span")
                MacroRow(avg)
                NutPanel(avg, "Micronutriments (moyenne des jours notés)", withMacros = false)
            }

        }
    }
}

/** Compare la période affichée à la précédente, de même durée. */
@Composable
private fun Comparison(x: Ctx, start: Long, end: Long, span: Int) {
    val cur = x.c.periodStats(start, end)
    val prev = x.c.periodStats(start - span, start - 1)
    if (cur.loggedDays == 0 && prev.loggedDays == 0) return

    SectionTitle("Comparaison avec la période précédente")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PeriodColumn("Cette période", cur, Modifier.weight(1f))
        PeriodColumn("Précédente", prev, Modifier.weight(1f))
    }
    if (cur.loggedDays > 0 && prev.loggedDays > 0) {
        Text("Calories : ${signed(cur.avgKcal - prev.avgKcal)} kcal par jour", Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
        Hint("Protéines : ${signed(cur.avgProtein - prev.avgProtein)} g par jour")
        if (cur.avgWeight != null && prev.avgWeight != null) {
            Hint("Poids moyen : ${signed(cur.avgWeight - prev.avgWeight, 1)} kg")
        }
    }
}

@Composable
private fun PeriodColumn(title: String, s: PeriodStats, modifier: Modifier) {
    Column(modifier) {
        Hint(title)
        Text(if (s.loggedDays == 0) "—" else nf(s.avgKcal) + " kcal", fontWeight = FontWeight.ExtraBold)
        Hint("${s.loggedDays} jour(s) notés")
        if (s.avgWeight != null) Hint(nf(s.avgWeight, 1) + " kg en moyenne")
        if (s.weightChange != null) Hint("Variation : ${signed(s.weightChange, 1)} kg")
    }
}

@Composable
fun WeightSection(x: Ctx, vm: MainViewModel, start: Long, end: Long, span: Int, inRange: List<Weight>) {
    val weights = x.s.weights

    SectionTitle("Poids")
    Hint("La pesée se saisit dans le Journal, au jour voulu.")

    val cfg = x.s.config
    val trend = WeightStats.trendKgPerWeek(weights, end, windowDays = span.coerceAtLeast(14))
    val logged = x.c.periodStats(start, end)
    val useLogged = logged.loggedDays >= 7
    val intake = if (useLogged) logged.avgKcal else x.c.reachedKcal
    val expected = ((x.s.target ?: x.c.baseKcal.roundToInt()) - cfg.maint) * 7 / 7700.0
    if (trend == null) {
        Hint("Il faut au moins 3 pesées sur 7 jours dans la période pour estimer une tendance.", Modifier.padding(top = 8.dp))
    } else {
        val observed = WeightStats.observedMaintenance(intake, trend)
        val rounded = (observed / 5).roundToInt() * 5
        Text("Tendance : ${signed(trend, 2)} kg par semaine (prévu : ${signed(expected, 2)}).",
            Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
        Hint((if (useLogged) "D'après tes repas notés (${nf(intake)} kcal par jour)" else "En suivant ton plan (${nf(intake)} kcal par jour)") + ", ta maintenance réelle serait d'environ${nf(rounded.toDouble())} kcal. " +
            "Réglée actuellement : ${nf(cfg.maint.toDouble())} kcal.")
        if (abs(rounded - cfg.maint) >= 50) {
            GhostButton("Utiliser ${nf(rounded.toDouble())} kcal comme maintenance") { vm.saveConfig(cfg.copy(maint = rounded)) }
        }
    }

    for (w in inRange.takeLast(8).asReversed()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(dayText(w.epochDay, true), Modifier.weight(1f))
            Text(nf(w.kg, 1) + " kg", fontWeight = FontWeight.SemiBold)
            TextButton({ vm.deleteWeight(w.epochDay) }) { Text("Retirer") }
        }
    }
}

/**
 * Barres = calories (axe gauche, de 0 au maximum), ligne = poids (axe droit, ajusté à ses valeurs).
 * Au-delà de 60 jours, les barres sont des moyennes par semaine pour rester lisibles.
 */
@Composable
private fun CombinedChart(start: Long, end: Long, kcalByDay: Map<Long, Double>, target: Double, weights: List<Weight>) {
    val bar = MaterialTheme.colorScheme.tertiary
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val span = (end - start + 1).toInt()
    val bucket = if (span > 60) 7 else 1

    // (premier jour, dernier jour, moyenne kcal des jours notés)
    val buckets = (0 until (span + bucket - 1) / bucket).map { b ->
        val from = start + b.toLong() * bucket
        val to = minOf(end, from + bucket - 1)
        val v = (from..to).mapNotNull { kcalByDay[it] }
        Triple(from, to, if (v.isEmpty()) null else v.average())
    }
    val maxK = (buckets.mapNotNull { it.third }.maxOrNull() ?: 0.0).coerceAtLeast(target) * 1.1
    val minW = (weights.minOfOrNull { it.kg } ?: 0.0) - 0.5
    val maxW = (weights.maxOfOrNull { it.kg } ?: 0.0) + 0.5

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("■ Calories" + if (bucket > 1) " (moy. semaine)" else "", color = bar, style = MaterialTheme.typography.bodySmall)
        if (weights.isNotEmpty()) Text("● Poids", color = line, style = MaterialTheme.typography.bodySmall)
        Text("┄ Objectif", color = guide, style = MaterialTheme.typography.bodySmall)
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val w = size.width
        val h = size.height
        buckets.forEach { (from, to, v) ->
            if (v == null || v <= 0) return@forEach
            val x0 = (from - start).toFloat() / span * w
            val x1 = (to - start + 1).toFloat() / span * w
            val gap = (x1 - x0) * 0.15f
            val bh = (v / maxK * h).toFloat()
            drawRect(bar, Offset(x0 + gap, h - bh), Size(x1 - x0 - 2 * gap, bh))
        }
        val ty = h - (target / maxK * h).toFloat()
        drawLine(guide, Offset(0f, ty), Offset(w, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))

        if (weights.isNotEmpty()) {
            fun pos(p: Weight) = Offset(
                (p.epochDay - start + 0.5f) / span * w,
                h - ((p.kg - minW) / (maxW - minW)).toFloat() * h,
            )
            weights.zipWithNext().forEach { (a, b) -> drawLine(line, pos(a), pos(b), strokeWidth = 5f, cap = StrokeCap.Round) }
            weights.forEach { drawCircle(line, 7f, pos(it)) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Hint(dayText(start, span >= 90)); Hint(dayText(end, span >= 90))
    }
    Hint("Barres : 0 à ${nf(maxK)} kcal (objectif ${nf(target)})." +
        if (weights.isNotEmpty()) " Ligne : de ${nf(minW + 0.5, 1)} à ${nf(maxW - 0.5, 1)} kg." else "")
}
