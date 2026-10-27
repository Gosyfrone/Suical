package app.suical.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.suical.domain.LogEntry
import app.suical.domain.Nutrients
import app.suical.domain.SLOTS
import app.suical.domain.entryNut
import app.suical.domain.planSlotToMeal
import app.suical.domain.todayEpochDay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val longFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
private val yearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private const val DAY_MS = 86_400_000L

/** Jusqu'où le journal remonte, et jusqu'où il anticipe. */
private const val PAST_DAYS = 3650
private const val FUTURE_DAYS = 7

/** Un repas prévu (plan ou « tous les jours ») pas encore noté ce jour-là. */
private class Planned(val dish: Dish, val slot: Int)

/**
 * Journal : un jour à la fois, qu'on fait défiler d'un geste ou avec les flèches. Chaque jour regroupe ses repas
 * par moment ; les repas prévus attendent d'être validés et sont notés au bon jour, quel que soit le moment de la saisie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(x: Ctx, vm: MainViewModel, initialDay: Long, onDay: (Long) -> Unit) {
    val today = todayEpochDay()
    val first = today - PAST_DAYS
    val last = today + FUTURE_DAYS
    val pager = rememberPagerState(initialPage = (initialDay.coerceIn(first, last) - first).toInt()) { (last - first + 1).toInt() }
    val scope = rememberCoroutineScope()
    val shown = first + pager.currentPage
    fun go(day: Long) { scope.launch { pager.animateScrollToPage((day.coerceIn(first, last) - first).toInt()) } }
    LaunchedEffect(pager.settledPage) { onDay(first + pager.settledPage) }

    var picking by rememberSaveable { mutableStateOf(false) }
    val loggedDays = remember(x.s.log) { x.s.log.map { it.day }.toSet() }

    Column(Modifier.fillMaxSize()) {
        DayHeader(shown, today, first, last, onPick = { picking = true }, go = ::go)
        WeekStrip(shown, today, last, loggedDays, go = ::go)
        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 0, key = { it }) { page ->
            DayPage(x, vm, first + page)
        }
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = shown * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / DAY_MS in first..last
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({ state.selectedDateMillis?.let { go(it / DAY_MS) }; picking = false }) { Text("Aller") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DayHeader(day: Long, today: Long, first: Long, last: Long, onPick: () -> Unit, go: (Long) -> Unit) {
    val date = LocalDate.ofEpochDay(day)
    val relative = when (day - today) { 0L -> "Aujourd'hui"; -1L -> "Hier"; 1L -> "Demain"; else -> null }
    val title = relative ?: date.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE)).replaceFirstChar { it.uppercase() }
    val sub = when {
        relative != null -> date.format(longFmt)
        date.year != LocalDate.now().year -> date.format(yearFmt)
        else -> date.format(dateFmt)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ go(day - 1) }, enabled = day > first) { Icon(Icons.Default.KeyboardArrowLeft, "Jour précédent") }
        Column(Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            Hint(sub)
        }
        IconButton({ go(day + 1) }, enabled = day < last) { Icon(Icons.Default.KeyboardArrowRight, "Jour suivant") }
    }
}

/** Les 7 jours de la semaine affichée : un point sous les jours qui ont au moins un repas noté. */
@Composable
private fun WeekStrip(day: Long, today: Long, last: Long, logged: Set<Long>, go: (Long) -> Unit) {
    val monday = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val d = monday + i
                val selected = d == day
                val enabled = d <= last
                Column(
                    Modifier.weight(1f).padding(horizontal = 2.dp)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                        .then(if (d == today && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(enabled = enabled) { go(d) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        enabled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
                    }
                    Text("LMMJVSD"[i].toString(), fontSize = 11.sp, color = fg.copy(alpha = .7f), fontWeight = FontWeight.SemiBold)
                    Text(LocalDate.ofEpochDay(d).dayOfMonth.toString(), fontSize = 16.sp, color = fg, fontWeight = FontWeight.Bold)
                    Box(Modifier.padding(top = 3.dp).size(5.dp).background(
                        if (d in logged) (if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary) else Color.Transparent,
                        CircleShape))
                }
            }
        }
        if (day != today) {
            TextButton({ go(today) }, Modifier.align(Alignment.CenterHorizontally)) { Text("Revenir à aujourd'hui", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DayPage(x: Ctx, vm: MainViewModel, day: Long) {
    val entries = x.s.log.filter { it.day == day }
    val eaten = Nutrients.keys.associateWith { 0.0 }.toMutableMap()
    for (e in entries) for ((k, v) in x.c.entryNut(e)) eaten[k] = eaten.getValue(k) + v
    val goal = x.c.nutOf(x.c.weekAvgIng())
    val target = (x.s.target ?: (x.c.baseKcal / 50).roundToInt() * 50).toDouble()

    // Repas prévus : ceux du plan (midi, soir) et les plats « tous les jours », tant qu'ils ne sont pas notés.
    val planned = buildList {
        val idx = x.s.config.indexOf(day)
        if (idx != null) x.s.plan.getOrNull(idx)?.forEachIndexed { ps, id ->
            val d = x.s.dish(id) ?: return@forEachIndexed
            val slot = planSlotToMeal(ps)
            if (entries.none { it.slot == slot && it.dishId == d.id }) add(Planned(d, slot))
        }
        x.s.dishes.filter { it.group == GROUP_DAILY }.forEach { d ->
            if (entries.none { it.dishId == d.id }) add(Planned(d, d.slot))
        }
    }

    var adding by rememberSaveable { mutableStateOf<Int?>(null) }
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hero(eaten, goal, target) }
        item { WeightPanel(x, vm, day) }
        items(SLOTS, key = { it.first }) { (slot, label) ->
            SlotSection(x, vm, label, entries.filter { it.slot == slot }, planned.filter { it.slot == slot }, day,
                onAdd = { adding = slot }, onOpen = { opened = it.id })
        }
    }

    adding?.let { slot -> AddSheet(x, vm, day, slot) { adding = null } }
    opened?.let { id -> x.s.log.firstOrNull { it.id == id }?.let { e -> EntrySheet(x, vm, e) { opened = null } } }
}

@Composable
private fun SlotSection(
    x: Ctx, vm: MainViewModel, label: String, entries: List<LogEntry>, planned: List<Planned>, day: Long,
    onAdd: () -> Unit, onOpen: (LogEntry) -> Unit,
) {
    val kcal = entries.sumOf { x.c.entryNut(it).getValue("kcal") }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontSize = 12.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) Text(nf(kcal) + " kcal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(onAdd) { Icon(Icons.Default.Add, "Ajouter : $label", tint = MaterialTheme.colorScheme.primary) }
        }
        if (entries.isNotEmpty() || planned.isNotEmpty()) Panel {
            var firstRow = true
            for (e in entries) {
                if (!firstRow) Divider()
                firstRow = false
                EntryRow(x, e) { onOpen(e) }
            }
            for (p in planned) {
                if (!firstRow) Divider()
                firstRow = false
                PlannedRow(x, vm, p, day)
            }
        }
    }
}

@Composable
private fun EntryRow(x: Ctx, e: LogEntry, onClick: () -> Unit) {
    val name = x.s.dish(e.dishId)?.name ?: e.name.ifEmpty { "Repas" }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Hint(stockLabel(e))
        }
        Text(nf(x.c.entryNut(e).getValue("kcal")) + " kcal", fontWeight = FontWeight.Bold)
    }
}

private fun stockLabel(e: LogEntry) = when {
    e.ready -> "Portion prête utilisée"
    e.fromStock -> "Pris dans le stock"
    e.custom.isNotEmpty() -> "Saisie libre"
    else -> "Hors stock"
}

@Composable
private fun PlannedRow(x: Ctx, vm: MainViewModel, p: Planned, day: Long) {
    val d = p.dish
    val ready = x.s.ready[d.id] ?: 0
    val possible = x.c.possible(d).n
    val nut = x.c.nutOf(x.c.effIng(d))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("PRÉVU", fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            Text(d.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                ready > 0 -> Hint("${nf(nut.getValue("kcal"))} kcal · $ready ${if (ready > 1) "portions prêtes" else "portion prête"}")
                possible >= 1 -> Hint("${nf(nut.getValue("kcal"))} kcal · pris dans le stock")
                else -> Hint("${nf(nut.getValue("kcal"))} kcal · pas assez de stock", error = true)
            }
        }
        AccentButton("Valider", modifier = Modifier.padding(start = 8.dp)) { vm.logDish(d, day, p.slot, fromStock = true) }
    }
}

/** Pesée du jour affiché : le champ part de la pesée du jour, sinon de la dernière connue avant. */
@Composable
private fun WeightPanel(x: Ctx, vm: MainViewModel, day: Long) {
    val w = x.s.weights.firstOrNull { it.epochDay == day }
    val before = x.s.weights.filter { it.epochDay < day }.maxByOrNull { it.epochDay }?.kg
    var input by remember(day, w?.kg) { mutableDoubleStateOf(w?.kg ?: before ?: 0.0) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Poids", fontWeight = FontWeight.SemiBold)
                Hint(if (w != null) "Pesée enregistrée" else "Pas de pesée ce jour")
            }
            NumField(input, "kg", Modifier.width(96.dp)) { input = it }
            Spacer(Modifier.width(8.dp))
            if (w != null && input == w.kg) TextButton({ vm.deleteWeight(day) }) { Text("Retirer") }
            else PrimaryButton(if (w == null) "OK" else "Modifier", enabled = input in 20.0..400.0) { vm.setWeight(day, input) }
        }
    }
}

/** Détail d'une ligne du journal : moment, prise dans le stock, suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(x: Ctx, vm: MainViewModel, e: LogEntry, onDismiss: () -> Unit) {
    val name = x.s.dish(e.dishId)?.name ?: e.name.ifEmpty { "Repas" }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            MacroRow(x.c.entryNut(e))
            Choice(SLOTS, e.slot, Modifier.fillMaxWidth(), label = "Moment") { vm.moveEntry(e, it) }
            if (e.ing.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pris dans mon stock", fontWeight = FontWeight.SemiBold)
                        Hint(if (e.ready) "Une portion prête a été utilisée" else if (e.fromStock) "Les ingrédients ont été retirés du stock" else "Le stock n'a pas été touché")
                    }
                    Switch(e.usesStock, { vm.setEntryStock(e, it) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                DangerButton("Supprimer") { vm.removeEntry(e); onDismiss() }
                PrimaryButton("OK", modifier = Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

private const val ADD_DISH = 0
private const val ADD_FREE = 1

/** Ajout à un moment du jour : un de mes plats, ou une saisie libre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(x: Ctx, vm: MainViewModel, day: Long, slot: Int, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(ADD_DISH) }
    val slotLabel = SLOTS.first { it.first == slot }.second
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ajouter", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Hint("$slotLabel · ${LocalDate.ofEpochDay(day).format(longFmt)}")
            Segmented(listOf(ADD_DISH to "Un de mes plats", ADD_FREE to "Saisie libre"), mode, fill = true) { mode = it }
            if (mode == ADD_DISH) DishChoice(x, onDismiss) { d, fromStock -> vm.logDish(d, day, slot, fromStock); onDismiss() }
            else FreeEntry(onDismiss) { n, k, p, g, l -> vm.addExtra(n, k, p, g, l, day, slot); onDismiss() }
        }
    }
}

@Composable
private fun DishChoice(x: Ctx, onDismiss: () -> Unit, onPick: (Dish, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var fromStock by rememberSaveable { mutableStateOf(false) }
    SearchField(query, { query = it }, "Chercher un plat")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Prendre dans mon stock", fontWeight = FontWeight.SemiBold)
            Hint("Retire une portion prête ou les ingrédients")
        }
        Switch(fromStock, { fromStock = it })
    }
    val list = x.s.dishes.filter { matchesQuery(it.name, query) }
    Column(Modifier.heightIn(max = 360.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))) {
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { d ->
                Row(Modifier.fillMaxWidth().clickable { onPick(d, fromStock) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Hint(nf(x.c.nutOf(x.c.effIng(d)).getValue("kcal")) + " kcal")
                }
            }
        }
        if (list.isEmpty()) Hint("Aucun plat ne correspond.", Modifier.padding(16.dp))
    }
    GhostButton("Annuler", Modifier.fillMaxWidth(), onClick = onDismiss)
}

/** Repas hors plan : valeurs saisies à la main, comptées dans les stats sans toucher au stock. */
@Composable
private fun FreeEntry(onDismiss: () -> Unit, onAdd: (String, Double, Double, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableDoubleStateOf(0.0) }
    var p by rememberSaveable { mutableDoubleStateOf(0.0) }
    var g by rememberSaveable { mutableDoubleStateOf(0.0) }
    var l by rememberSaveable { mutableDoubleStateOf(0.0) }
    Hint("Tu saisis les valeurs toi-même.")
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
        PrimaryButton("Ajouter au journal", enabled = kcal > 0, modifier = Modifier.weight(1f)) { onAdd(name.trim(), kcal, p, g, l) }
    }
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
