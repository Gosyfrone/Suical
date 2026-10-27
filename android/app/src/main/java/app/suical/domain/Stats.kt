package app.suical.domain

import java.time.LocalDate

/**
 * Nutriments d'une ligne du journal : ceux des ingrédients plus les valeurs saisies ou estimées (`custom`).
 * Une ligne sans rien des deux (ancienne portion prête) reprend le plat.
 */
fun Calc.entryNut(e: LogEntry): Nut {
    val d = s.dish(e.dishId)
    val base = nutOf(if (e.ing.isEmpty() && e.custom.isEmpty()) d?.let { effIng(it) } ?: emptyMap() else e.ing)
    return Nutrients.keys.associateWith { base.getValue(it) + (e.custom[it] ?: 0.0) }
}

/** Totaux nutritionnels par jour (clé = epochDay) pour les jours ayant au moins un repas noté. */
fun Calc.dailyTotals(fromDay: Long, toDay: Long): Map<Long, Nut> {
    val out = LinkedHashMap<Long, MutableMap<String, Double>>()
    for (e in s.log) {
        if (e.day < fromDay || e.day > toDay) continue
        val acc = out.getOrPut(e.day) { Nutrients.keys.associateWith { 0.0 }.toMutableMap() }
        for ((k, v) in entryNut(e)) acc[k] = acc.getValue(k) + v
    }
    return out
}

/** Derniers plats, aliments et saisies libres notés, du plus récent au plus ancien, sans doublon. */
fun recentEntries(log: List<LogEntry>, n: Int = 10): List<LogEntry> =
    log.sortedByDescending { it.t }.distinctBy {
        when {
            it.dishId.isNotEmpty() -> "d:" + it.dishId
            it.custom.isEmpty() && it.ing.size == 1 -> "f:" + it.ing.keys.first()
            else -> "n:" + it.name.lowercase()
        }
    }.take(n)

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

/** Maintenance déduite de la courbe de poids et de ce qui a été mangé. */
data class ObservedMaintenance(val kcal: Double, val kgPerWeek: Double, val intake: Double, val fromLog: Boolean)

/** Null s'il n'y a pas assez de pesées dans la fenêtre. Apport = repas notés (7 jours minimum), sinon le plan. */
fun Calc.observedMaintenance(today: Long, windowDays: Int = 28): ObservedMaintenance? {
    val trend = WeightStats.trendKgPerWeek(s.weights, today, windowDays) ?: return null
    val logged = periodStats(today - windowDays + 1, today)
    val fromLog = logged.loggedDays >= 7
    val intake = if (fromLog) logged.avgKcal else reachedKcal
    return ObservedMaintenance(WeightStats.observedMaintenance(intake, trend), trend, intake, fromLog)
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
