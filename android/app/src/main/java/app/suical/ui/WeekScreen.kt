package app.suical.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.GROUP_MEAL
import app.suical.llm.Change
import app.suical.llm.LlmBridge
import app.suical.llm.LlmImport
import app.suical.llm.Prompts
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WeekScreen(x: Ctx, vm: MainViewModel) {
    val context = LocalContext.current
    val avg = x.c.nutOf(x.c.weekAvgIng())
    val meals = x.s.dishes.filter { it.group == GROUP_MEAL }
    var changes by remember { mutableStateOf<List<Change>?>(null) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item { GoalBlock(x, vm) }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Plats sans sauce", fontWeight = FontWeight.Bold)
                    Hint("${nf(x.s.config.sauceQty)} g dans les pâtes")
                }
                Segmented(listOf("creme" to "Crème 4 %", "fb" to "Fromage blanc"), x.s.sauce) { vm.setSauce(it) }
            }
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Analyser avec l'IA") { vm.sendToLlm(context, Prompts.weekReview(x.c)) }
                GhostButton("Coller les propositions") {
                    try { changes = LlmImport.parseChanges(LlmBridge.paste(context)) } catch (e: Exception) { vm.say(e.message ?: "Réponse illisible") }
                }
            }
            SectionTitle("Sessions de cuisine")
        }

        for (session in x.s.config.sessions) item(key = session.id) {
            val p = x.c.sessionPlan(session)
            val what = p.batch.entries.joinToString(" + ") { (id, n) -> (x.s.dish(id)?.name ?: id) + " ×$n" }.ifEmpty { "Rien à préparer" }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Hint(session.whenLabel + " · couvre " + session.days.filter { it < x.s.plan.size }.joinToString(", ") { x.s.config.dayLabel(it).lowercase() })
                        Text(what, fontWeight = FontWeight.Bold)
                    }
                    PrimaryButton("Cuisiné", enabled = p.missing.isEmpty() && p.batch.isNotEmpty()) { vm.cook(session) }
                }
                Hint(
                    if (p.missing.isEmpty()) "Stock suffisant"
                    else "Manque : " + p.missing.joinToString(", ") { (x.s.foods[it.foodId]?.name?.lowercase() ?: it.foodId) + " (" + fmtQty(x.s.foods[it.foodId], it.lack) + ")" },
                    error = p.missing.isNotEmpty())
                if (p.fresh.isNotEmpty()) Hint("À cuisiner sur le moment : " + p.fresh.joinToString(", ") { it.first.name.lowercase() + " (" + x.s.config.dayLabel(it.second).lowercase() + ")" })
                Column(Modifier.padding(top = 4.dp)) {
                    var open by remember { mutableStateOf(false) }
                    Text((if (open) "▾ " else "▸ ") + "Quantités à préparer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickableNoRipple { open = !open })
                    if (open) {
                        p.need.entries.sortedByDescending { it.value }.forEachIndexed { i, (k, q) ->
                            Text("${i + 1}. " + fmtQty(x.s.foods[k], q) + " — " + (x.s.foods[k]?.name?.lowercase() ?: k), fontSize = 14.sp)
                        }
                        Hint("Ajoute la crème ou le fromage blanc au moment de réchauffer, pas avant.", Modifier.padding(top = 4.dp))
                    }
                }
                Divider()
            }
        }

        item { SectionTitle("Planning") }
        for (i in x.s.plan.indices) item(key = "day$i") {
            val t = x.c.nutOf(x.c.dayIng(i))
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(x.s.config.dayLabel(i), fontWeight = FontWeight.Bold)
                        listOf("Midi", "Soir").forEachIndexed { j, lab ->
                            Choice(listOf("" to "—") + meals.map { it.id to it.name }, x.s.plan[i][j], label = lab) { vm.setPlan(i, j, it) }
                        }
                    }
                    Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                        Text(nf(t.getValue("kcal")) + " kcal", fontWeight = FontWeight.Bold)
                        Hint(nf(t.getValue("p")) + " g prot.")
                    }
                }
                NutPanel(t, "Détail du jour", withMacros = false)
                Divider()
            }
        }

        item {
            SectionTitle("Moyenne par jour sur le plan")
            MacroRow(avg)
            NutPanel(avg, "Détail des micronutriments", withMacros = false)
        }
    }

    changes?.let { list -> ChangesDialog(x, list, { changes = null }) { vm.applyChanges(it) } }
}

/** Propositions de l'IA : on décoche celles qu'on ne veut pas, puis on applique. */
@Composable
private fun ChangesDialog(x: Ctx, list: List<Change>, onDismiss: () -> Unit, onApply: (List<Change>) -> Unit) {
    val checked = remember { mutableStateListOf<Boolean>().apply { addAll(list.map { true }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propositions de l'IA") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEachIndexed { i, ch ->
                    val dish = x.s.dish(ch.dishId)
                    val food = x.s.foods[ch.foodId]
                    val known = dish != null && food != null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked[i] && known, { checked[i] = it }, enabled = known)
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(
                                if (!known) "Plat ou aliment inconnu"
                                else (dish!!.name + " : " + food!!.name.lowercase() + " " +
                                    (dish.ing[food.id]?.let { nf(it) } ?: "0") + " → " + if (ch.qty <= 0) "retiré" else fmtQty(food, ch.qty)),
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (ch.why.isNotBlank()) Hint(ch.why)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ onApply(list.filterIndexed { i, _ -> checked[i] }); onDismiss() }) { Text("Appliquer") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun GoalBlock(x: Ctx, vm: MainViewModel) {
    val cur = x.s.target ?: ((x.c.baseKcal / 50).roundToInt() * 50)
    var slider by remember(cur) { mutableFloatStateOf(cur.toFloat()) }
    val diff = slider.roundToInt() - x.s.config.maint
    val status = when {
        abs(diff) <= 75 -> "Proche de ta maintenance (~${nf(x.s.config.maint.toDouble())} kcal)."
        diff < 0 -> "Déficit d'environ ${nf(-diff.toDouble())} kcal par jour, soit ~${nf(-diff * 7 / 7700.0, 1)} kg par semaine."
        else -> "Surplus d'environ ${nf(diff.toDouble())} kcal par jour, soit ~${nf(diff * 7 / 7700.0, 1)} kg par semaine."
    }
    val avg = x.c.nutOf(x.c.weekAvgIng())
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Objectif calorique", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(nf(slider.toDouble()) + " kcal", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(slider, { slider = (it / 50).roundToInt() * 50f }, valueRange = 2200f..3500f, steps = 25,
            onValueChangeFinished = { vm.setTarget(slider.roundToInt()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Hint("Sèche"); Hint("Maintien"); Hint("Prise")
        }
        Text(status, Modifier.padding(top = 8.dp), fontSize = 14.sp)
        if (x.c.clamped) Hint("Au bout des leviers : la page atteint ~${nf(x.c.reachedKcal)} kcal.", error = true)
        Column(Modifier.padding(vertical = 8.dp)) {
            for (l in x.s.config.levers) {
                if (l.item !in x.s.foods) continue
                val v = x.c.lv.getValue(l.id)
                val dd = v - l.base
                Row(Modifier.fillMaxWidth()) {
                    Text(l.label, Modifier.weight(1f), fontSize = 14.sp)
                    Text(nf(v) + " g", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (dd == 0.0) "base" else (if (dd > 0) "+" else "−") + nf(abs(dd)) + " g",
                        Modifier.padding(start = 10.dp), fontSize = 13.sp,
                        color = when {
                            dd < 0 -> MaterialTheme.colorScheme.error
                            dd > 0 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
            }
        }
        MacroRow(avg)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Hint("Recettes, stock et courses calés sur ~${nf(avg.getValue("kcal"))} kcal par jour.", Modifier.weight(1f))
            if (x.s.target != null) androidx.compose.material3.TextButton({ vm.setTarget(null) }) { Text("Revenir à la base") }
        }
    }
}
