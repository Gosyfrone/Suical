package app.suical.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Nutriments d'une ligne du journal : valeurs saisies à la main, ou calculées depuis les ingrédients. */
fun Calc.entryNut(e: LogEntry): Nut {
    if (e.custom.isNotEmpty()) return Nutrients.keys.associateWith { e.custom[it] ?: 0.0 }
    val d = s.dish(e.dishId)
    return nutOf(e.ing.ifEmpty { d?.let { effIng(it) } ?: emptyMap() })
}

fun entryEpochDay(e: LogEntry, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(e.t).atZone(zone).toLocalDate().toEpochDay()

/** Totaux nutritionnels par jour (clé = epochDay) pour les jours ayant au moins un repas noté. */
fun Calc.dailyTotals(fromDay: Long, toDay: Long): Map<Long, Nut> {
    val out = LinkedHashMap<Long, MutableMap<String, Double>>()
    for (e in s.log) {
        val day = entryEpochDay(e)
        if (day < fromDay || day > toDay) continue
        val acc = out.getOrPut(day) { Nutrients.keys.associateWith { 0.0 }.toMutableMap() }
        for ((k, v) in entryNut(e)) acc[k] = acc.getValue(k) + v
    }
    return out
}

fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

object WeightStats {
    /**
     * Pente de la courbe de poids en kg par semaine (régression linéaire sur les `windowDays`
     * derniers jours). Null s'il y a moins de 3 pesées ou moins de 7 jours d'écart.
     */
    fun trendKgPerWeek(weights: List<Weight>, today: Long, windowDays: Int = 28): Double? {
        val pts = weights.filter { it.epochDay in (today - windowDays)..today }
        if (pts.size < 3 || pts.maxOf { it.epochDay } - pts.minOf { it.epochDay } < 7) return null
        val mx = pts.map { it.epochDay.toDouble() }.average()
        val my = pts.map { it.kg }.average()
        val den = pts.sumOf { (it.epochDay - mx) * (it.epochDay - mx) }
        if (den == 0.0) return null
        val slope = pts.sumOf { (it.epochDay - mx) * (it.kg - my) } / den
        return slope * 7
    }

    /** Maintenance réellement observée : apport moyen moins ce que la variation de poids représente (7700 kcal/kg). */
    fun observedMaintenance(intakeKcal: Double, kgPerWeek: Double): Double = intakeKcal - kgPerWeek * 7700 / 7
}

/** Résumé d'une période pour comparer deux fenêtres de même durée. */
data class PeriodStats(
    val loggedDays: Int,
    val avgKcal: Double,
    val avgProtein: Double,
    /** Dernier poids moins premier poids de la période (null s'il y a moins de 2 pesées). */
    val weightChange: Double?,
    val avgWeight: Double?,
)

fun Calc.periodStats(from: Long, to: Long): PeriodStats {
    val totals = dailyTotals(from, to)
    val n = totals.size
    fun avg(k: String) = if (n == 0) 0.0 else totals.values.sumOf { it.getValue(k) } / n
    val w = s.weights.filter { it.epochDay in from..to }.sortedBy { it.epochDay }
    return PeriodStats(
        loggedDays = n,
        avgKcal = avg("kcal"),
        avgProtein = avg("p"),
        weightChange = if (w.size >= 2) w.last().kg - w.first().kg else null,
        avgWeight = if (w.isEmpty()) null else w.map { it.kg }.average(),
    )
}
