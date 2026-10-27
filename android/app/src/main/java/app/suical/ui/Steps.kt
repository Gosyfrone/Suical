package app.suical.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.Metabolism
import app.suical.domain.dayTarget
import app.suical.health.HealthSteps
import kotlin.math.abs

/** Lance la connexion à Health Connect : autorisation, ou installation si besoin. */
@Composable
fun rememberConnectSteps(vm: MainViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(HealthSteps.permissionContract()) { vm.syncSteps(force = true) }
    return {
        when (HealthSteps.availability(context)) {
            HealthSteps.Availability.READY -> launcher.launch(HealthSteps.PERMISSIONS)
            HealthSteps.Availability.NEEDS_INSTALL ->
                runCatching { context.startActivity(HealthSteps.installIntent()) }.onFailure { vm.say("Installe Health Connect depuis le Play Store") }
            HealthSteps.Availability.UNSUPPORTED -> vm.say("Health Connect n'est pas disponible sur ce téléphone")
        }
    }
}

/** Pas du jour affiché et ce qu'ils changent à l'objectif. */
@Composable
fun StepsPanel(x: Ctx, vm: MainViewModel, day: Long) {
    val connected by vm.stepsConnected.collectAsState()
    val connect = rememberConnectSteps(vm)
    val t = x.c.dayTarget(day)
    val ref = x.s.config.profile.steps
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pas", fontWeight = FontWeight.SemiBold)
                Hint(when {
                    t.steps == null -> if (connected) "Pas de données ce jour" else "Relie ta montre via Health Connect"
                    !x.s.config.stepsAdjust -> "Ajustement de l'objectif désactivé"
                    t.stepsKcal > 0 -> "+${nf(t.stepsKcal)} kcal à l'objectif (moyenne : ${nf(ref.toDouble())} pas)"
                    t.stepsKcal < 0 -> "−${nf(abs(t.stepsKcal))} kcal à l'objectif (moyenne : ${nf(ref.toDouble())} pas)"
                    else -> "Objectif inchangé (moyenne : ${nf(ref.toDouble())} pas)"
                })
            }
            Text(t.steps?.let { nf(it.toDouble()) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (!connected) GhostButton("Connecter Health Connect", Modifier.fillMaxWidth().padding(top = 10.dp), onClick = connect)
    }
}

/** Réglages : connexion, synchro et ajustement de l'objectif. */
@Composable
fun StepsSettings(x: Ctx, vm: MainViewModel) {
    val connected by vm.stepsConnected.collectAsState()
    val connect = rememberConnectSteps(vm)
    val cfg = x.s.config
    val kg = x.s.weights.maxByOrNull { it.epochDay }?.kg ?: cfg.profile.weightKg
    SectionTitle("Pas (Health Connect)")
    Panel {
        Hint(if (connected) "Connecté : les pas des 30 derniers jours sont repris à chaque ouverture de l'app."
            else "Ta montre écrit ses pas dans Health Connect (dans Zepp : Profil > Ajouter des comptes > Health Connect). Suical les lit, sans rien écrire.")
        if (connected) GhostButton("Synchroniser maintenant", Modifier.fillMaxWidth().padding(top = 10.dp)) { vm.syncSteps(force = true) }
        else PrimaryButton("Connecter Health Connect", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), onClick = connect)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ajuster l'objectif selon les pas", fontWeight = FontWeight.SemiBold)
                Hint("Chaque 1 000 pas au-dessus de ta moyenne (${nf(cfg.profile.steps.toDouble())}, Suivi > Maintien) ajoute ~${nf(Metabolism.kcalPerStep(cfg.profile, kg) * 1000, 0)} kcal, " +
                    "en dessous en retire (d'après ton poids et ta taille). Le jour même, seuls les pas en plus comptent.")
            }
            Switch(cfg.stepsAdjust, { vm.saveConfig(cfg.copy(stepsAdjust = it)) })
        }
    }
}
