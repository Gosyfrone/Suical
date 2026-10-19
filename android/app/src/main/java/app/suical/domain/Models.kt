package app.suical.domain

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

data class Dish(
    val id: String,
    val name: String,
    val group: String,
    val sauce: Boolean,
    val fresh: Boolean,
    val ing: Map<String, Double>,
)

/** `ing` = ingrédients consommés (vide pour une portion déjà préparée). */
data class LogEntry(
    val id: Long,
    val dishId: String,
    val t: Long,
    val ready: Boolean,
    val ing: Map<String, Double>,
    /** Repas hors plan : nom libre et valeurs saisies à la main (dishId vide). */
    val name: String = "",
    val custom: Map<String, Double> = emptyMap(),
)

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
