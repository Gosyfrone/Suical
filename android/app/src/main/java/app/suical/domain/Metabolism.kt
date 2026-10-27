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
    const val KCAL_PER_STEP_PER_KG = 0.0004

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

/**
 * Objectif calorique d'un jour : l'objectif réglé, plus ce que les pas de ce jour ajoutent ou retirent
 * par rapport à la moyenne du profil (celle sur laquelle repose le maintien).
 */
data class DayTarget(val base: Double, val steps: Int?, val stepsKcal: Double) {
    val total get() = base + stepsKcal
}

/**
 * Le jour même, la journée n'est pas finie : seuls les pas au-delà de la moyenne comptent (jamais de malus le matin).
 * Les jours passés comptent dans les deux sens. Ajustement arrondi à 10 kcal.
 */
fun Calc.dayTarget(day: Long, today: Long = todayEpochDay()): DayTarget {
    val base = (s.target ?: (baseKcal / 50).roundToInt() * 50).toDouble()
    val steps = s.steps[day]
    if (!s.config.stepsAdjust || steps == null || day > today) return DayTarget(base, steps, 0.0)
    val p = s.config.profile
    val kg = s.weights.filter { it.epochDay <= day }.maxByOrNull { it.epochDay }?.kg ?: p.weightKg
    val diff = (steps - p.steps).let { if (day == today) it.coerceAtLeast(0) else it }
    val kcal = (diff * Metabolism.KCAL_PER_STEP_PER_KG * kg / 10).roundToInt() * 10.0
    return DayTarget(base, steps, kcal)
}

/** Moyenne des pas sur les jours connus de la période, null s'il y en a moins de 7. */
fun AppState.avgSteps(from: Long, to: Long): Int? {
    val v = steps.filterKeys { it in from..to }.values
    return if (v.size < 7) null else v.average().roundToInt()
}

/** Les kcal par jour pour un écart donné au maintien, arrondies à 50. */
fun goalKcal(maint: Double, delta: Int): Int = ((maint + delta) / 50).roundToInt() * 50
