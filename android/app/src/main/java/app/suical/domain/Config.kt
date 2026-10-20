package app.suical.domain

import java.time.LocalDate

val WEEKDAYS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Une session de cuisine en lot, qui couvre certains jours du plan (indices 0..jours-1). */
data class Session(val id: String, val whenLabel: String, val days: List<Int>)

/**
 * Un levier = une quantité qu'on ajuste pour atteindre l'objectif calorique.
 * `scope` : "repas" (tous les repas contenant l'aliment) ou l'identifiant d'un plat.
 * `priority` : les leviers sont actionnés par priorité croissante, chacun jusqu'à sa borne (min ou max).
 */
data class Lever(
    val id: String,
    val label: String,
    val item: String,
    val scope: String,
    val base: Double,
    val min: Double,
    val max: Double,
    val priority: Int,
)

fun mondayEpochDay(d: LocalDate): Long = d.minusDays((d.dayOfWeek.value - 1).toLong()).toEpochDay()

/** Données personnelles pour estimer la dépense théorique (page Maintien). */
data class Profile(
    val male: Boolean = true,
    val age: Int = 25,
    val heightCm: Double = 175.0,
    /** Poids de repli, utilisé seulement tant qu'aucune pesée n'est enregistrée. */
    val weightKg: Double = 75.0,
    /** % de masse grasse, 0 = inconnu (formule de Mifflin-St Jeor au lieu de Katch-McArdle). */
    val bodyFatPct: Double = 0.0,
    val steps: Int = 8000,
    val sessionsPerWeek: Double = 4.0,
    val sessionMin: Double = 60.0,
    /** 0 = légère, 1 = modérée, 2 = intense. */
    val intensity: Int = 1,
)

/** Réglages du plan, modifiables dans Réglages > Plan. */
data class PlanConfig(
    /** Dépense calorique d'entretien, sert à estimer le déficit / surplus. */
    val maint: Int = 2725,
    /** Quantité de crème ou fromage blanc ajoutée aux plats « avec sauce ». */
    val sauceQty: Double = 60.0,
    /** Durée du plan en jours (1 à 28). */
    val days: Int = 5,
    /** Jour (epochDay) qui correspond au jour 1 du plan. Par défaut le lundi de la semaine en cours. */
    val startEpochDay: Long = mondayEpochDay(LocalDate.now()),
    val profile: Profile = Profile(),
    val remindersOn: Boolean = false,
    val reminderHour: Int = 18,
    val sessions: List<Session> = listOf(
        Session("s1", "Dimanche soir, au retour", listOf(0, 1, 2)),
        Session("s2", "Mercredi soir", listOf(3, 4)),
    ),
    val levers: List<Lever> = listOf(
        Lever("pates", "Pâtes par repas", "pates", "repas", 125.0, 85.0, 175.0, 1),
        Lever("avoine_bol", "Avoine du bol", "avoine", "bol_avoine", 60.0, 45.0, 100.0, 2),
        Lever("pb", "Beurre de cacahuète du bol", "beurre_cacahuete", "bol_avoine", 20.0, 10.0, 40.0, 3),
        Lever("amandes", "Amandes", "amandes", "collation", 20.0, 10.0, 40.0, 4),
    ),
) {
    /** Le plan se répète par blocs de semaines entières (un plan de 5 jours = une semaine de 7). */
    val period get() = (days + 6) / 7 * 7

    private val startWeekday get() = Math.floorMod(startEpochDay + 3, 7L).toInt() // 0 = lundi

    fun dayLabel(i: Int): String {
        val name = WEEKDAYS[(startWeekday + i) % 7]
        return if (days <= 7) name else "S${i / 7 + 1} ${name.take(3)}"
    }

    /** Index du jour du plan pour cette date, ou null si elle tombe hors du plan (ex. week-end). */
    fun indexOf(epochDay: Long): Int? {
        val idx = Math.floorMod(epochDay - startEpochDay, period.toLong()).toInt()
        return if (idx < days) idx else null
    }
}
