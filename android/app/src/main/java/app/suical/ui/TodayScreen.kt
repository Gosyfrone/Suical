package app.suical.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.Dish
import app.suical.domain.GROUP_DAILY
import app.suical.domain.Nutrients
import app.suical.domain.entryNut
import app.suical.domain.todayEpochDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val timeFmt = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRANCE)

@Composable
fun TodayScreen(x: Ctx, vm: MainViewModel) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var extra by rememberSaveable { mutableStateOf(false) }

    val todays = x.s.log.filter { Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() == today }.sortedByDescending { it.t }
    val eaten = Nutrients.keys.associateWith { 0.0 }.toMutableMap()
    for (e in todays) for ((k, v) in x.c.entryNut(e)) eaten[k] = eaten.getValue(k) + v
    val goal = x.c.nutOf(x.c.weekAvgIng())
    val target = (x.s.target ?: (x.c.baseKcal / 50).roundToInt() * 50).toDouble()

    val cfg = x.s.config
    val slot = if (LocalTime.now(zone).hour < 15) 0 else 1
    val dayIdx = cfg.indexOf(todayEpochDay())
    val next = dayIdx?.let { x.s.plan.getOrNull(it)?.getOrNull(slot) }?.let { x.s.dish(it) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Aujourd'hui", today.format(dayFmt).replaceFirstChar { it.uppercase() }) }

        item { Hero(eaten, goal, target) }

        item { SectionTitle("Prochain repas") }
        item {
            Panel {
                if (next == null) {
                    Text(if (dayIdx == null) "Pas de repas prévu aujourd'hui" else "Rien de prévu ce ${if (slot == 0) "midi" else "soir"}", fontWeight = FontWeight.Bold)
                    Hint("Règle ton planning dans l'onglet Semaine.", Modifier.padding(top = 2.dp))
                } else NextMeal(x, vm, next, (if (slot == 0) "Midi" else "Soir") + " · plan de " + cfg.dayLabel(dayIdx!!).lowercase())
            }
        }

        val daily = x.s.dishes.filter { it.group == GROUP_DAILY }
        if (daily.isNotEmpty()) {
            item { SectionTitle("Tous les jours") }
            item {
                Panel {
                    daily.forEachIndexed { i, d ->
                        if (i > 0) Divider()
                        DailyRow(x, vm, d)
                    }
                }
            }
        }

        item { SectionTitle("Déjà mangé") }
        item {
            if (todays.isEmpty()) Hint("Rien d'enregistré aujourd'hui. Appuie sur « Mangé » après un repas.", Modifier.padding(horizontal = 4.dp))
            else Panel {
                todays.forEachIndexed { i, e ->
                    val name = x.s.dish(e.dishId)?.name ?: e.name.ifEmpty { "Repas" }
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold)
                            Hint(Instant.ofEpochMilli(e.t).atZone(zone).format(timeFmt))
                        }
                        Text(nf(x.c.entryNut(e).getValue("kcal")) + " kcal", fontWeight = FontWeight.Bold)
                        TextButton({ vm.undo(e) }) { Text("Annuler") }
                    }
                }
            }
        }

        item { GhostButton("+ Ajouter un repas hors plan", Modifier.fillMaxWidth()) { extra = true } }
    }

    if (extra) ExtraMealSheet({ extra = false }) { n, k, p, g, l -> vm.addExtra(n, k, p, g, l) }
}

/** Anneau de calories du jour et barres de macros, sur fond sombre. */
@Composable
private fun Hero(eaten: Map<String, Double>, goal: Map<String, Double>, target: Double) {
    val left = target - eaten.getValue("kcal")
    val frac = (eaten.getValue("kcal") / target).toFloat().coerceIn(0f, 1f)
    val bg = MaterialTheme.colorScheme.heroBackground()
    val fg = Color(0xFFEEF2EF)
    val muted = Color(0xFFA9BDB5)
    val yolk = MaterialTheme.colorScheme.secondary

    Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(24.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(112.dp)) {
                val w = 10.dp.toPx()
                val inset = w / 2
                val arc = Size(size.width - w, size.height - w)
                drawArc(fg.copy(alpha = .16f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                drawArc(yolk, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(nf(kotlin.math.abs(left)), color = fg, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (left >= 0) "kcal restantes" else "kcal en plus", color = muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroBar("Protéines", "p", eaten, goal, Color(0xFF6CC49D), fg, muted)
            MacroBar("Glucides", "g", eaten, goal, yolk, fg, muted)
            MacroBar("Lipides", "l", eaten, goal, fg, fg, muted)
        }
    }
}

@Composable
private fun MacroBar(label: String, key: String, eaten: Map<String, Double>, goal: Map<String, Double>, bar: Color, fg: Color, muted: Color) {
    val have = eaten.getValue(key)
    val want = goal.getValue(key)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = fg, fontSize = 12.sp)
            Text("${nf(have)} / ${nf(want)} g", color = muted, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { if (want > 0) (have / want).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
            color = bar, trackColor = fg.copy(alpha = .16f), strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
        )
    }
}

@Composable
private fun NextMeal(x: Ctx, vm: MainViewModel, d: Dish, caption: String) {
    val pr = x.c.possible(d)
    val ready = x.s.ready[d.id] ?: 0
    val n = pr.n + ready
    val nut = x.c.nutOf(x.c.effIng(d))
    Hint(caption)
    Text(d.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
    Hint("${nf(nut.getValue("kcal"))} kcal · ${nf(nut.getValue("p"))} g de protéines", Modifier.padding(top = 2.dp))
    when {
        n < 1 -> Hint("Manque : " + (x.s.foods[pr.limit]?.name?.lowercase() ?: "des ingrédients"), Modifier.padding(top = 2.dp), error = true)
        ready > 0 -> Hint("$ready ${if (ready > 1) "portions prêtes" else "portion prête"} au frigo", Modifier.padding(top = 2.dp))
        else -> Hint("$n ${if (n > 1) "repas possibles" else "repas possible"} avec ton stock", Modifier.padding(top = 2.dp))
    }
    AccentButton("Mangé", enabled = n >= 1, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { vm.eat(d) }
}

@Composable
private fun DailyRow(x: Ctx, vm: MainViewModel, d: Dish) {
    val n = x.c.possible(d).n + (x.s.ready[d.id] ?: 0)
    val nut = x.c.nutOf(x.c.effIng(d))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.name, fontWeight = FontWeight.SemiBold)
            if (n < 1) Hint("Stock insuffisant", error = true) else Hint("${nf(nut.getValue("kcal"))} kcal")
        }
        GhostButton("Mangé", enabled = n >= 1) { vm.eat(d) }
    }
}

/** Repas hors plan : valeurs saisies à la main, comptées dans les stats sans toucher au stock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraMealSheet(onDismiss: () -> Unit, onAdd: (String, Double, Double, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableDoubleStateOf(0.0) }
    var p by rememberSaveable { mutableDoubleStateOf(0.0) }
    var g by rememberSaveable { mutableDoubleStateOf(0.0) }
    var l by rememberSaveable { mutableDoubleStateOf(0.0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ajouter un repas", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Hint("Hors plan : tu saisis les valeurs toi-même.")
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nom (restaurant, snack…)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField(kcal, "Calories (kcal)", Modifier.weight(1f)) { kcal = it }
                NumField(p, "Protéines (g)", Modifier.weight(1f)) { p = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField(g, "Glucides (g)", Modifier.weight(1f)) { g = it }
                NumField(l, "Lipides (g)", Modifier.weight(1f)) { l = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                GhostButton("Annuler", onClick = onDismiss)
                PrimaryButton("Ajouter au journal", enabled = kcal > 0, modifier = Modifier.weight(1f)) {
                    onAdd(name.trim(), kcal, p, g, l); onDismiss()
                }
            }
        }
    }
}
