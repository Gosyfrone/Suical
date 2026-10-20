package app.suical.domain

import kotlin.math.roundToInt

/** Dépense théorique détaillée, en kcal par jour. */
data class TheoreticalMaintenance(
    val bmr: Double,
    /** Digestion et mouvements du quotidien hors marche (BMR × 1,2 − BMR). */
    val daily: Double,
    val steps: Double,
    val training: Double,
    val formula: String,
) {
    val total get() = bmr + daily + steps + training
}

object Metabolism {
    /** MET par intensité de séance : légère, modérée (muscu classique), intense (muscu lourde, HIIT). */
    private val MET = doubleArrayOf(3.5, 5.0, 7.0)
    val INTENSITIES = listOf(0 to "Légère", 1 to "Modérée", 2 to "Intense")

    /** Pas déjà couverts par le facteur de base (déplacements dans la maison, etc.). */
    private const val BASE_STEPS = 3000
    private const val KCAL_PER_STEP_PER_KG = 0.0004

    /** Katch-McArdle si la masse grasse est connue, sinon Mifflin-St Jeor. */
    fun bmr(p: Profile, kg: Double): Pair<Double, String> =
        if (p.bodyFatPct in 3.0..60.0) {
            (370 + 21.6 * kg * (1 - p.bodyFatPct / 100)) to "Katch-McArdle"
        } else {
            (10 * kg + 6.25 * p.heightCm - 5 * p.age + if (p.male) 5 else -161) to "Mifflin-St Jeor"
        }

    fun theoretical(p: Profile, kg: Double): TheoreticalMaintenance {
        val (bmr, formula) = bmr(p, kg)
        val steps = (p.steps - BASE_STEPS).coerceAtLeast(0) * KCAL_PER_STEP_PER_KG * kg
        // Dépense nette : on retire le MET de repos (1), déjà compté dans le BMR.
        val perSession = (MET[p.intensity.coerceIn(0, 2)] - 1) * kg * p.sessionMin / 60
        return TheoreticalMaintenance(bmr, bmr * 0.2, steps, perSession * p.sessionsPerWeek / 7, formula)
    }
}

/** Les kcal par jour pour un écart donné au maintien, arrondies à 50. */
fun goalKcal(maint: Double, delta: Int): Int = ((maint + delta) / 50).roundToInt() * 50
