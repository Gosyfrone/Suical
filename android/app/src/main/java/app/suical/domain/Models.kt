package app.suical.domain

import java.time.Instant
import java.time.ZoneId

/** Unités : "g" et "ml" (valeurs pour 100), "pc" et "boîte" (valeurs par unité). */
val UNITS = listOf("g", "ml", "pc", "boîte")

data class Category(val id: String, val name: String)

val CATS = listOf(
    Category("frais", "Frais"),
    Category("viande", "Viande et poisson"),
    Category("surgele", "Surgelés"),
    Category("epicerie", "Épicerie"),
)

data class Food(
    val id: String,
    val name: String,
    val unit: String,
    val cat: String,
    val pack: Double,
    val packLabel: String,
    val price: Double,
    val nut: Map<String, Double>,
) {
    val perHundred get() = unit == "g" || unit == "ml"
    fun factor(q: Double) = if (perHundred) q / 100 else q
    fun n(key: String) = nut[key] ?: 0.0
}

const val GROUP_MEAL = "repas"
const val GROUP_DAILY = "jour"

/** Moments d'une journée dans le journal. Le plan a deux créneaux : midi (0) → [SLOT_LUNCH], soir (1) → [SLOT_DINNER]. */
const val SLOT_BREAKFAST = 0
const val SLOT_LUNCH = 1
const val SLOT_SNACK = 2
const val SLOT_DINNER = 3
val SLOTS = listOf(SLOT_BREAKFAST to "Petit-déjeuner", SLOT_LUNCH to "Midi", SLOT_SNACK to "Collation", SLOT_DINNER to "Soir")

fun planSlotToMeal(planSlot: Int) = if (planSlot == 0) SLOT_LUNCH else SLOT_DINNER

/** Moment le plus probable pour une heure donnée (repas notés avant l'arrivée des moments, saisie du jour). */
fun slotOfHour(hour: Int) = when {
    hour < 11 -> SLOT_BREAKFAST
    hour < 15 -> SLOT_LUNCH
    hour < 18 -> SLOT_SNACK
    else -> SLOT_DINNER
}

/** Moment par défaut d'un plat « tous les jours » qui n'en a pas encore : le petit-déjeuner se reconnaît à son nom. */
fun guessDailySlot(id: String, name: String) =
    if (id == "petit_dej" || name.lowercase().contains("petit")) SLOT_BREAKFAST else SLOT_SNACK

data class Dish(
    val id: String,
    val name: String,
    val group: String,
    val sauce: Boolean,
    val fresh: Boolean,
    val ing: Map<String, Double>,
    /** Pour un plat « tous les jours » : le moment où il est prévu dans le journal. */
    val slot: Int = SLOT_SNACK,
)

/**
 * Une ligne du journal, rangée par jour et par moment (pas par l'heure de saisie).
 * `ing` = copie des ingrédients mangés : la modifier ne touche pas au plat d'origine.
 * `ready` = une portion prête a été prise ; `fromStock` = les ingrédients ont été retirés du stock.
 */
data class LogEntry(
    val id: Long,
    val dishId: String,
    /** Moment de la saisie, sert seulement à ordonner les lignes d'un même moment. */
    val t: Long,
    val ready: Boolean,
    val ing: Map<String, Double>,
    /** Repas hors plan : nom libre et valeurs saisies à la main (dishId vide). */
    val name: String = "",
    val custom: Map<String, Double> = emptyMap(),
    val day: Long = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
    val slot: Int = slotOfHour(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).hour),
    val fromStock: Boolean = !ready && dishId.isNotEmpty() && custom.isEmpty(),
) {
    /** Quelque chose a été pris dans le stock (portion prête ou ingrédients). */
    val usesStock get() = ready || fromStock
}

data class Weight(val epochDay: Long, val kg: Double)

const val DEFAULT_LLM_PKG = "com.anthropic.claude"

/** Tout l'état de l'app. Immuable : les écrans le lisent, les calculs sont des fonctions pures dessus. */
data class AppState(
    val foods: Map<String, Food>,
    val dishes: List<Dish>,
    /** plan[jour][0 = midi, 1 = soir], "" = vide */
    val plan: List<List<String>>,
    val stock: Map<String, Double>,
    val ready: Map<String, Int>,
    val log: List<LogEntry>,
    val target: Int?,
    val sauce: String,
    val llmPkg: String = DEFAULT_LLM_PKG,
    val config: PlanConfig = PlanConfig(),
    val weights: List<Weight> = emptyList(),
) {
    fun dish(id: String) = dishes.firstOrNull { it.id == id }
    fun stockOf(id: String) = stock[id] ?: 0.0
}
