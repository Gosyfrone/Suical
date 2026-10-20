package app.suical.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.Metabolism
import app.suical.domain.Profile
import app.suical.domain.TheoreticalMaintenance
import app.suical.domain.goalKcal
import app.suical.domain.observedMaintenance
import app.suical.domain.todayEpochDay
import kotlin.math.abs
import kotlin.math.roundToInt

/** (écart par rapport au maintien, nom) */
private val GOALS = listOf(
    -500 to "Sèche",
    -300 to "Sèche douce",
    0 to "Maintien",
    250 to "Prise lente",
    400 to "Prise de masse",
)

private fun round5(v: Double) = (v / 5).roundToInt() * 5

@Composable
fun MaintenanceScreen(x: Ctx, vm: MainViewModel) {
    val cfg = x.s.config
    val p = cfg.profile
    val save = { n: Profile -> vm.saveConfig(cfg.copy(profile = n)) }
    val lastWeight = x.s.weights.maxByOrNull { it.epochDay }?.kg
    val theo = Metabolism.theoretical(p, lastWeight ?: p.weightKg)
    val obs = x.c.observedMaintenance(todayEpochDay())
    val theoR = round5(theo.total)
    val obsR = obs?.let { round5(it.kcal) }
    val mixed = if (obsR != null) round5((theoR + obsR) / 2.0) else theoR

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            SectionTitle("Ton maintien")
            Hint("Deux estimations : une théorique (tes données ci-dessous) et une observée (ton poids et ce que tu manges). Les deux ensemble sont plus fiables qu'une seule.")
            Estimate("Théorique", theoR, "Calculée avec ton métabolisme, tes pas et tes séances.")
            if (obs != null && obsR != null) {
                Estimate("Observée", obsR,
                    "Sur 28 jours : ${nf(obs.kgPerWeek, 2)} kg par semaine avec ${nf(obs.intake)} kcal par jour " +
                        (if (obs.fromLog) "(repas notés)." else "(plan suivi)."))
            } else {
                Hint("Observée : il faut au moins 3 pesées sur 7 jours, dans les 28 derniers jours (voir Stats).", Modifier.padding(top = 8.dp))
            }
            Text("Réglé actuellement : ${nf(cfg.maint.toDouble())} kcal", Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Théorique", enabled = cfg.maint != theoR) { vm.saveConfig(cfg.copy(maint = theoR)) }
                if (obsR != null) {
                    GhostButton("Observée", enabled = cfg.maint != obsR) { vm.saveConfig(cfg.copy(maint = obsR)) }
                    GhostButton("Moyenne", enabled = cfg.maint != mixed) { vm.saveConfig(cfg.copy(maint = mixed)) }
                }
            }
            if (obsR != null) Hint("Moyenne des deux : ${nf(mixed.toDouble())} kcal.")

            SectionTitle("Sèche, maintien, prise")
            for ((delta, name) in GOALS) {
                val kcal = goalKcal(cfg.maint.toDouble(), delta)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Hint(if (delta == 0) "Poids stable"
                        else "${if (delta > 0) "+" else "−"}${nf(abs(delta).toDouble())} kcal, ~${nf(abs(delta) * 7 / 7700.0, 2)} kg par semaine")
                    }
                    Text(nf(kcal.toDouble()) + " kcal", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    GhostButton("Objectif", enabled = x.s.target != kcal) { vm.setTarget(kcal) }
                }
            }
            Hint("« Objectif » règle l'objectif calorique du plan (onglet Semaine). Les leviers ajustent les quantités dans leurs limites.")

            SectionTitle("Tes données")
            Segmented(listOf(true to "Homme", false to "Femme"), p.male) { save(p.copy(male = it)) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(p.age.toDouble(), "Âge", Modifier.weight(1f)) { save(p.copy(age = it.toInt())) }
                NumField(p.heightCm, "Taille (cm)", Modifier.weight(1f)) { save(p.copy(heightCm = it)) }
            }
            if (lastWeight == null) {
                NumField(p.weightKg, "Poids (kg)", Modifier.fillMaxWidth().padding(top = 8.dp)) { save(p.copy(weightKg = it)) }
                Hint("Une fois ton poids enregistré dans Stats, la dernière pesée est reprise automatiquement.")
            } else {
                Hint("Poids : ${nf(lastWeight, 1)} kg (dernière pesée).", Modifier.padding(top = 8.dp))
            }
            NumField(p.bodyFatPct, "Masse grasse en % (facultatif)", Modifier.fillMaxWidth().padding(top = 8.dp)) { save(p.copy(bodyFatPct = it)) }
            Hint("Laisse 0 si tu ne la connais pas.")

            SectionTitle("Activité")
            NumField(p.steps.toDouble(), "Pas par jour (moyenne)", Modifier.fillMaxWidth()) { save(p.copy(steps = it.toInt())) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(p.sessionsPerWeek, "Séances par semaine", Modifier.weight(1f)) { save(p.copy(sessionsPerWeek = it)) }
                NumField(p.sessionMin, "Minutes par séance", Modifier.weight(1f)) { save(p.copy(sessionMin = it)) }
            }
            Row(Modifier.padding(top = 8.dp)) { Segmented(Metabolism.INTENSITIES, p.intensity) { save(p.copy(intensity = it)) } }
            Hint("Légère : cardio facile. Modérée : musculation classique. Intense : musculation lourde, HIIT.")

            Breakdown(theo)
        }
    }
}

@Composable
private fun Estimate(title: String, kcal: Int, detail: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(nf(kcal.toDouble()) + " kcal", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Hint(detail)
    }
}

@Composable
private fun Breakdown(t: TheoreticalMaintenance) {
    SectionTitle("Détail du calcul théorique")
    for ((label, v) in listOf(
        "Métabolisme de base (${t.formula})" to t.bmr,
        "Digestion et quotidien" to t.daily,
        "Marche" to t.steps,
        "Séances" to t.training,
    )) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text(nf(v) + " kcal", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
    Hint("La marche compte les pas au-delà de 3 000 par jour. Ce sont des estimations : si l'observée s'en écarte beaucoup, fie-toi plutôt à elle.", Modifier.padding(top = 4.dp))
}
