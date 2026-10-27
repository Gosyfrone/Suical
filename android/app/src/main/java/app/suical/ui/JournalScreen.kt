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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.suical.domain.dayTarget
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

/** Fenêtre d'ajout ouverte sur un moment, éventuellement directement sur un plat prévu à ajuster. */
private class AddRequest(val slot: Int, val start: AddStep = AddStep.Browse, val stock: Boolean = false)

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
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable(onClick = onPick))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint(sub, Modifier.clickable(onClick = onPick))
                if (day != today) Text("  ·  Aujourd'hui ›", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { go(today) }.padding(vertical = 4.dp))
            }
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
    }
}

@Composable
private fun DayPage(x: Ctx, vm: MainViewModel, day: Long) {
    val entries = x.s.log.filter { it.day == day }
    val eaten = Nutrients.keys.associateWith { 0.0 }.toMutableMap()
    for (e in entries) for ((k, v) in x.c.entryNut(e)) eaten[k] = eaten.getValue(k) + v
    val goal = x.c.nutOf(x.c.weekAvgIng())
    val target = x.c.dayTarget(day).total

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

    var adding by remember { mutableStateOf<AddRequest?>(null) }
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    // Un jour passé n'affiche pas ses repas prévus non notés : ils ne servent plus qu'à rattraper un oubli.
    val past = day < todayEpochDay()
    var showPlanned by rememberSaveable(day) { mutableStateOf(false) }
    val shownPlanned = if (past && !showPlanned) emptyList() else planned

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hero(eaten, goal, target) }
        item { StepsPanel(x, vm, day) }
        item { WeightPanel(x, vm, day) }
        items(SLOTS, key = { it.first }) { (slot, label) ->
            SlotSection(x, vm, label, entries.filter { it.slot == slot }, shownPlanned.filter { it.slot == slot }, day,
                onAdd = { adding = AddRequest(slot) }, onOpen = { opened = it.id },
                onEdit = { p -> adding = AddRequest(p.slot, AddStep.Items(p.dish.id, p.dish.name, x.c.effIng(p.dish)), stock = true) })
        }
        if (past && planned.isNotEmpty()) item {
            val n = planned.size
            TextButton({ showPlanned = !showPlanned }, Modifier.fillMaxWidth()) {
                Text(if (showPlanned) "Masquer les repas prévus"
                    else "$n ${if (n > 1) "repas prévus non notés" else "repas prévu non noté"} · Afficher", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    adding?.let { r -> AddSheet(x, vm, day, r.slot, r.start, r.stock) { adding = null } }
    opened?.let { id -> x.s.log.firstOrNull { it.id == id }?.let { e -> EntrySheet(x, vm, e) { opened = null } } }
}

@Composable
private fun SlotSection(
    x: Ctx, vm: MainViewModel, label: String, entries: List<LogEntry>, planned: List<Planned>, day: Long,
    onAdd: () -> Unit, onOpen: (LogEntry) -> Unit, onEdit: (Planned) -> Unit,
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
                PlannedRow(x, vm, p, day) { onEdit(p) }
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
            Hint(listOfNotNull(foodQty(x, e), stockLabel(e), adjusted(x, e)).joinToString(" · "))
        }
        Text(nf(x.c.entryNut(e).getValue("kcal")) + " kcal", fontWeight = FontWeight.Bold)
    }
}

/** Quantité d'un aliment noté seul (« 3 pièces »). */
private fun foodQty(x: Ctx, e: LogEntry): String? {
    if (e.dishId.isNotEmpty() || e.custom.isNotEmpty() || e.ing.size != 1) return null
    val (id, q) = e.ing.entries.first()
    return fmtQty(x.s.foods[id], q)
}

/** Un plat dont les ingrédients ont été changés pour ce repas. */
private fun adjusted(x: Ctx, e: LogEntry): String? {
    val d = x.s.dish(e.dishId) ?: return null
    return if (e.ing.isNotEmpty() && e.ing != x.c.effIng(d)) "ajusté" else null
}

private fun stockLabel(e: LogEntry) = when {
    e.ready -> "Portion prête utilisée"
    e.fromStock -> "Pris dans le stock"
    e.custom.isNotEmpty() && e.ing.isNotEmpty() -> "Estimé"
    e.custom.isNotEmpty() -> "Saisie libre"
    else -> "Hors stock"
}

@Composable
private fun PlannedRow(x: Ctx, vm: MainViewModel, p: Planned, day: Long, onEdit: () -> Unit) {
    val d = p.dish
    val ready = x.s.ready[d.id] ?: 0
    val possible = x.c.possible(d).n
    val nut = x.c.nutOf(x.c.effIng(d))
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("PRÉVU", fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            Text(d.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                ready > 0 -> Hint("${nf(nut.getValue("kcal"))} kcal · $ready ${if (ready > 1) "portions prêtes" else "portion prête"}")
                possible >= 1 -> Hint("${nf(nut.getValue("kcal"))} kcal · pris dans le stock")
                else -> Hint("${nf(nut.getValue("kcal"))} kcal · pas en stock")
            }
        }
        IconButton(onEdit) { Icon(Icons.Default.Edit, "Ajuster ${d.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        GhostButton("Valider") { vm.logDish(d, day, p.slot, fromStock = true) }
    }
}

/**
 * Pesée du jour affiché. Sans pesée ce jour, le champ reste vide (la dernière pesée n'est qu'une indication),
 * pour ne jamais enregistrer par erreur le poids d'un autre jour.
 */
@Composable
private fun WeightPanel(x: Ctx, vm: MainViewModel, day: Long) {
    val w = x.s.weights.firstOrNull { it.epochDay == day }
    val before = x.s.weights.filter { it.epochDay < day }.maxByOrNull { it.epochDay }?.kg
    var text by remember(day, w?.kg) { mutableStateOf(w?.kg?.let { plain(it) } ?: "") }
    val input = text.replace(',', '.').toDoubleOrNull()
    val valid = input != null && input in 20.0..400.0
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Poids", fontWeight = FontWeight.SemiBold)
                Hint(when {
                    w != null -> "Pesée enregistrée"
                    before != null -> "Dernière : ${nf(before, 1)} kg"
                    else -> "Pas de pesée ce jour"
                })
            }
            OutlinedTextField(text, { t -> if (t.all { it.isDigit() || it == '.' || it == ',' }) text = t }, Modifier.width(96.dp),
                label = { Text("kg") }, placeholder = { Text(before?.let { nf(it, 1) } ?: "", color = MaterialTheme.colorScheme.outline) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.width(8.dp))
            if (w != null && input == w.kg) TextButton({ vm.deleteWeight(day) }) { Text("Retirer") }
            else PrimaryButton(if (w == null) "OK" else "Modifier", enabled = valid) { vm.setWeight(day, input!!) }
        }
    }
}

/** Détail d'une ligne du journal : moment, ingrédients de ce repas, prise dans le stock, suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(x: Ctx, vm: MainViewModel, e: LogEntry, onDismiss: () -> Unit) {
    val name = x.s.dish(e.dishId)?.name ?: e.name.ifEmpty { "Repas" }
    var ing by remember(e.id, e.ing) { mutableStateOf(e.ing) }
    val changed = ing != e.ing
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            MacroRow(x.c.entryNut(e.copy(ing = ing)))
            Choice(SLOTS, e.slot, Modifier.fillMaxWidth(), label = "Moment") { vm.moveEntry(e, it) }
            if (e.ing.isNotEmpty()) {
                IngredientsEditor(x, ing) { ing = it }
                if (e.dishId.isNotEmpty()) Hint("Ne change que ce repas, pas le plat.")
                if (e.custom.isNotEmpty()) Hint("Plus ${nf(e.custom["kcal"] ?: 0.0)} kcal estimés par l'IA, non modifiables ici.")
            }
            if (e.ing.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pris dans mon stock", fontWeight = FontWeight.SemiBold)
                        Hint(if (e.ready) "Une portion prête a été utilisée" else if (e.fromStock) "Les ingrédients ont été retirés du stock" else "Le stock n'a pas été touché")
                    }
                    Switch(e.usesStock, { vm.setEntryStock(e, it) }, enabled = !changed)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                DangerButton("Supprimer") { vm.removeEntry(e); onDismiss() }
                PrimaryButton(if (changed) "Enregistrer" else "OK", enabled = !changed || ing.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    if (changed) vm.setEntryIng(e, ing)
                    onDismiss()
                }
            }
        }
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
