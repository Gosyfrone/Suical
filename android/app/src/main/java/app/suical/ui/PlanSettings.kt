package app.suical.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import app.suical.domain.todayEpochDay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.suical.domain.Lever
import app.suical.domain.PlanConfig
import app.suical.domain.Session

private fun uniqueId(prefix: String, taken: Set<String>): String {
    var i = 1
    while (prefix + i in taken) i++
    return prefix + i
}

/** Réglages du plan : maintenance, sauce, rappels, sessions de cuisine, leviers de l'objectif. */
@Composable
fun PlanSettings(x: Ctx, vm: MainViewModel) {
    val cfg = x.s.config
    val save: (PlanConfig) -> Unit = vm::saveConfig

    SectionTitle("Plan")
    Panel {
        NumField(cfg.maint.toDouble(), "Maintenance (kcal par jour)", Modifier.fillMaxWidth()) { save(cfg.copy(maint = it.toInt())) }
        Hint("Sert à estimer le déficit ou le surplus. L'onglet Suivi > Maintien peut la calculer pour toi.", Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(cfg.days.toDouble(), "Durée (jours)", Modifier.weight(1f)) {
                if (it.toInt() in 1..28) save(cfg.copy(days = it.toInt()))
            }
            NumField(cfg.sauceQty, "Sauce (g ou ml)", Modifier.weight(1f)) { save(cfg.copy(sauceQty = it)) }
        }
        Hint("Jour 1 du plan : ${cfg.dayLabel(0).lowercase()}. Au-delà de 7 jours, le plan se répète par semaines entières.", Modifier.padding(top = 4.dp, bottom = 8.dp))
        GhostButton("Le plan commence aujourd'hui", Modifier.fillMaxWidth()) { save(cfg.copy(startEpochDay = todayEpochDay())) }
    }

    SectionTitle("Rappels")
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Activer les rappels", fontWeight = FontWeight.Bold)
                Hint("Stock bas, et la veille d'une session de cuisine.")
            }
            Switch(cfg.remindersOn, { on ->
                save(cfg.copy(remindersOn = on))
                if (on && Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        if (cfg.remindersOn) NumField(cfg.reminderHour.toDouble(), "Heure du rappel (0 à 23)", Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (it.toInt() in 0..23) save(cfg.copy(reminderHour = it.toInt()))
        }
    }

    SectionTitle("Sessions de cuisine")
    Hint("Une session prépare en lot les plats des jours cochés.", Modifier.padding(start = 4.dp, bottom = 8.dp))
    for (s in cfg.sessions) key(s.id) { Panel(Modifier.padding(bottom = 10.dp)) { SessionEditor(s, cfg, save) } }
    GhostButton("Ajouter une session", Modifier.fillMaxWidth()) {
        val id = uniqueId("s", cfg.sessions.map { it.id }.toSet())
        save(cfg.copy(sessions = cfg.sessions + Session(id, "Nouvelle session", emptyList())))
    }

    SectionTitle("Leviers de l'objectif")
    Hint("Quand tu fixes un objectif calorique, l'app ajuste ces quantités, dans l'ordre de priorité (1 d'abord), chacune entre son minimum et son maximum.",
        Modifier.padding(start = 4.dp, bottom = 8.dp))
    for (l in cfg.levers.sortedBy { it.priority }) key(l.id) { Panel(Modifier.padding(bottom = 10.dp)) { LeverEditor(x, l, cfg, save) } }
    GhostButton("Ajouter un levier", Modifier.fillMaxWidth()) {
        val id = uniqueId("lv", cfg.levers.map { it.id }.toSet())
        val food = x.s.foods.values.firstOrNull() ?: return@GhostButton
        val next = (cfg.levers.maxOfOrNull { it.priority } ?: 0) + 1
        save(cfg.copy(levers = cfg.levers + Lever(id, "Nouveau levier", food.id, "repas", 100.0, 50.0, 150.0, next)))
    }
}

@Composable
private fun SessionEditor(s: Session, cfg: PlanConfig, save: (PlanConfig) -> Unit) {
    fun update(n: Session) = save(cfg.copy(sessions = cfg.sessions.map { if (it.id == s.id) n else it }))
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(s.whenLabel, "Quand", Modifier.fillMaxWidth()) { update(s.copy(whenLabel = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until cfg.days) {
                val name = cfg.dayLabel(i)
                FilterChip(i in s.days, { update(s.copy(days = if (i in s.days) s.days - i else (s.days + i).sorted())) },
                    label = { Text(if (cfg.days <= 7) name.take(3) else name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
            }
        }
        DangerButton("Supprimer cette session", Modifier.fillMaxWidth()) { save(cfg.copy(sessions = cfg.sessions.filter { it.id != s.id })) }
    }
}

@Composable
private fun LeverEditor(x: Ctx, l: Lever, cfg: PlanConfig, save: (PlanConfig) -> Unit) {
    fun update(n: Lever) = save(cfg.copy(levers = cfg.levers.map { if (it.id == l.id) n else it }))
    val scopes = listOf("repas" to "Tous les repas contenant l'aliment") +
        x.s.dishes.map { it.id to it.name }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(l.label, "Nom", Modifier.fillMaxWidth()) { update(l.copy(label = it)) }
        FoodPickerField("Aliment ajusté", x.s.foods.values, l.item) { update(l.copy(item = it.id)) }
        Choice(scopes, l.scope, label = "Dans quels plats") { update(l.copy(scope = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(l.base, "Base", Modifier.weight(1f)) { update(l.copy(base = it)) }
            NumField(l.min, "Min", Modifier.weight(1f)) { update(l.copy(min = it)) }
            NumField(l.max, "Max", Modifier.weight(1f)) { update(l.copy(max = it)) }
            NumField(l.priority.toDouble(), "Priorité", Modifier.weight(1f)) { update(l.copy(priority = it.toInt())) }
        }
        DangerButton("Supprimer ce levier", Modifier.fillMaxWidth()) { save(cfg.copy(levers = cfg.levers.filter { it.id != l.id })) }
    }
}
