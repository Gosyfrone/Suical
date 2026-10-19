package app.suical.domain

/**
 * Référentiel des nutriments suivis. Pour en ajouter un : une ligne ici.
 * Il apparaît ensuite tout seul dans l'éditeur d'aliments, les totaux, la sauvegarde
 * et les prompts envoyés à l'IA. `ref` = apport de référence journalier (null = macro).
 */
data class Nutrient(val key: String, val label: String, val unit: String, val ref: Double?)

object Nutrients {
    val all = listOf(
        Nutrient("kcal", "Énergie", "kcal", null),
        Nutrient("p", "Protéines", "g", null),
        Nutrient("g", "Glucides", "g", null),
        Nutrient("l", "Lipides", "g", null),
        Nutrient("fib", "Fibres", "g", 30.0),
        Nutrient("vc", "Vitamine C", "mg", 110.0),
        Nutrient("va", "Vitamine A", "µg", 750.0),
        Nutrient("ve", "Vitamine E", "mg", 10.5),
        Nutrient("vk", "Vitamine K1", "µg", 79.0),
        Nutrient("b9", "Folates (B9)", "µg", 330.0),
        Nutrient("b12", "Vitamine B12", "µg", 4.0),
        Nutrient("ca", "Calcium", "mg", 1000.0),
        Nutrient("fe", "Fer", "mg", 11.0),
        Nutrient("mg", "Magnésium", "mg", 420.0),
        Nutrient("zn", "Zinc", "mg", 14.0),
        Nutrient("k", "Potassium", "mg", 3500.0),
        Nutrient("io", "Iode", "µg", 150.0),
        Nutrient("se", "Sélénium", "µg", 70.0),
    )
    val micros = all.filter { it.ref != null }
    val keys = all.map { it.key }
    val byKey = all.associateBy { it.key }
}
