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

/** Réglages du plan : maintenance, sauce, sessions de cuisine, leviers de l'objectif calorique. */
@Composable
fun PlanSettings(x: Ctx, vm: MainViewModel) {
    val cfg = x.s.config
    val save: (PlanConfig) -> Unit = vm::saveConfig

    SectionTitle("Plan")
    NumField(cfg.maint.toDouble(), "Maintenance (kcal par jour)", Modifier.fillMaxWidth()) { save(cfg.copy(maint = it.toInt())) }
    Hint("Sert à estimer le déficit ou le surplus de l'objectif calorique.")
    NumField(cfg.sauceQty, "Sauce des plats concernés (g ou ml)", Modifier.fillMaxWidth()) { save(cfg.copy(sauceQty = it)) }
    NumField(cfg.days.toDouble(), "Durée du plan (jours, 1 à 28)", Modifier.fillMaxWidth()) {
        if (it.toInt() in 1..28) save(cfg.copy(days = it.toInt()))
    }
    Hint("Jour 1 du plan : ${cfg.dayLabel(0).lowercase()}. Au-delà de 7 jours, le plan se répète par semaines entières.")
    GhostButton("Le plan commence aujourd'hui") { save(cfg.copy(startEpochDay = todayEpochDay())) }

    SectionTitle("Rappels")
    Hint("Une notification par jour si un stock est bas, et la veille d'une session de cuisine.")
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Activer les rappels", Modifier.weight(1f))
        Switch(cfg.remindersOn, { on ->
            save(cfg.copy(remindersOn = on))
            if (on && Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
    }
    if (cfg.remindersOn) NumField(cfg.reminderHour.toDouble(), "Heure du rappel (0 à 23)", Modifier.fillMaxWidth()) {
        if (it.toInt() in 0..23) save(cfg.copy(reminderHour = it.toInt()))
    }

    SectionTitle("Sessions de cuisine")
    Hint("Une session prépare en lot les plats des jours cochés.")
    for (s in cfg.sessions) key(s.id) { SessionEditor(s, cfg, save) }
    GhostButton("Ajouter une session") {
        val id = uniqueId("s", cfg.sessions.map { it.id }.toSet())
        save(cfg.copy(sessions = cfg.sessions + Session(id, "Nouvelle session", emptyList())))
    }

    SectionTitle("Leviers de l'objectif")
    Hint("Quand tu fixes un objectif calorique, l'app ajuste ces quantités, dans l'ordre de priorité (1 d'abord), chacune entre son minimum et son maximum.")
    for (l in cfg.levers.sortedBy { it.priority }) key(l.id) { LeverEditor(x, l, cfg, save) }
    GhostButton("Ajouter un levier") {
        val id = uniqueId("lv", cfg.levers.map { it.id }.toSet())
        val food = x.s.foods.values.firstOrNull() ?: return@GhostButton
        val next = (cfg.levers.maxOfOrNull { it.priority } ?: 0) + 1
        save(cfg.copy(levers = cfg.levers + Lever(id, "Nouveau levier", food.id, "repas", 100.0, 50.0, 150.0, next)))
    }
}

@Composable
private fun SessionEditor(s: Session, cfg: PlanConfig, save: (PlanConfig) -> Unit) {
    fun update(n: Session) = save(cfg.copy(sessions = cfg.sessions.map { if (it.id == s.id) n else it }))
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(s.whenLabel, "Quand", Modifier.fillMaxWidth()) { update(s.copy(whenLabel = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until cfg.days) {
                val name = cfg.dayLabel(i)
                FilterChip(i in s.days, { update(s.copy(days = if (i in s.days) s.days - i else (s.days + i).sorted())) },
                    label = { Text(if (cfg.days <= 7) name.take(3) else name) })
            }
        }
        DangerButton("Supprimer cette session") { save(cfg.copy(sessions = cfg.sessions.filter { it.id != s.id })) }
        Divider()
    }
}

@Composable
private fun LeverEditor(x: Ctx, l: Lever, cfg: PlanConfig, save: (PlanConfig) -> Unit) {
    fun update(n: Lever) = save(cfg.copy(levers = cfg.levers.map { if (it.id == l.id) n else it }))
    val scopes = listOf("repas" to "Tous les repas contenant l'aliment") +
        x.s.dishes.map { it.id to it.name }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(l.label, "Nom", Modifier.fillMaxWidth()) { update(l.copy(label = it)) }
        Choice(x.s.foods.values.map { it.id to it.name }, l.item, label = "Aliment ajusté") { update(l.copy(item = it)) }
        Choice(scopes, l.scope, label = "Dans quels plats") { update(l.copy(scope = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(l.base, "Base", Modifier.weight(1f)) { update(l.copy(base = it)) }
            NumField(l.min, "Min", Modifier.weight(1f)) { update(l.copy(min = it)) }
            NumField(l.max, "Max", Modifier.weight(1f)) { update(l.copy(max = it)) }
            NumField(l.priority.toDouble(), "Priorité", Modifier.weight(1f)) { update(l.copy(priority = it.toInt())) }
        }
        DangerButton("Supprimer ce levier") { save(cfg.copy(levers = cfg.levers.filter { it.id != l.id })) }
        Divider()
    }
}
