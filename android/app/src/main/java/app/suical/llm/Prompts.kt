package app.suical.llm

import app.suical.domain.AppState
import app.suical.domain.Calc
import app.suical.domain.Food
import app.suical.domain.GROUP_DAILY
import app.suical.domain.Nutrients
import java.util.Locale

/**
 * Tous les prompts envoyés à l'IA sont construits ici. Ils sont dérivés de [Nutrients] :
 * ajouter un nutriment l'ajoute automatiquement à la demande ET à l'import de la réponse.
 */
object Prompts {

    private fun nf(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(Locale.US, v)

    private fun per(f: Food) = when (f.unit) {
        "g" -> "pour 100 g"
        "ml" -> "pour 100 ml"
        "pc" -> "par pièce"
        else -> "par ${f.unit}${if (f.packLabel.isNotBlank()) " (" + f.packLabel + ")" else ""}"
    }

    private fun nutrientKeys() = Nutrients.all.joinToString(", ") { "${it.key} = ${it.label} en ${it.unit}" }
    private fun nutrientTemplate() = Nutrients.keys.joinToString(", ") { "\"$it\": 0" }

    /**
     * Demande les valeurs nutritionnelles d'un ou plusieurs aliments, réponse attendue en JSON.
     * Avec `fromPhoto`, l'IA lit une photo d'étiquette jointe au message.
     */
    fun foodValues(foods: List<Food>, fromPhoto: Boolean = false): String = buildString {
        if (fromPhoto) {
            appendLine("La photo jointe montre le tableau nutritionnel d'un produit. Lis-le et donne-moi ses valeurs nutritionnelles. Complète les micronutriments absents de l'étiquette avec ta meilleure estimation pour ce type de produit (Ciqual / USDA).")
        } else {
            appendLine("Je tiens un suivi nutritionnel. Donne-moi les valeurs nutritionnelles de chaque aliment ci-dessous (sources : Ciqual ou USDA, aliment cru / tel qu'acheté sauf indication).")
        }
        appendLine()
        appendLine("Aliments (identifiant | nom | base de calcul) :")
        foods.forEach { appendLine("- ${it.id} | ${it.name} | ${per(it)}") }
        appendLine()
        appendLine("Nutriments attendus (clé = unité) :")
        appendLine(nutrientKeys())
        appendLine()
        appendLine("Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour ni bloc de code, de la forme :")
        appendLine("{\"${foods.first().id}\": {${nutrientTemplate()}}, ...}")
        appendLine("Une entrée par aliment, avec toutes les clés, des nombres uniquement (pas d'unité, point comme séparateur décimal). Si une valeur est inconnue, donne ta meilleure estimation plutôt que 0.")
    }

    /** Demande une analyse de la semaine, avec des changements que l'app peut appliquer en un tap. */
    fun weekReview(c: Calc): String = buildString {
        val s = c.s
        val cfg = s.config
        val avg = c.nutOf(c.weekAvgIng())
        appendLine("Voici mon plan de repas sur ${cfg.days} jour(s) (valeurs moyennes par jour, poids crus). Analyse-le comme un diététicien sportif.")
        appendLine()
        appendLine("Objectif : ${s.target ?: "pas d'objectif défini"} kcal/jour (maintenance estimée ~${cfg.maint} kcal).")
        appendLine("Plan réel : ${nf(avg.getValue("kcal"))} kcal, ${nf(avg.getValue("p"))} g protéines, ${nf(avg.getValue("g"))} g glucides, ${nf(avg.getValue("l"))} g lipides.")
        appendLine()
        appendLine("Micronutriments par jour (valeur, % de la référence ANSES homme adulte) :")
        Nutrients.micros.forEach {
            val v = avg.getValue(it.key)
            appendLine("- ${it.label} : ${nf(v)} ${it.unit} (${Math.round(v / it.ref!! * 100)} %)")
        }
        appendLine()
        appendLine("Plats (identifiant | nom | ingrédients en id:quantité) :")
        s.dishes.forEach { d ->
            val used = s.plan.sumOf { day -> day.count { it == d.id } }
            appendLine("- ${d.id} | ${d.name} | " + d.ing.entries.joinToString(", ") { "${it.key}:${nf(it.value)}" } +
                (if (d.group == GROUP_DAILY) " | tous les jours" else " | $used fois dans le plan"))
        }
        appendLine()
        appendLine("Aliments disponibles (identifiant | nom | unité) :")
        s.foods.values.forEach { appendLine("- ${it.id} | ${it.name} | ${it.unit}") }
        appendLine()
        appendLine("Dis-moi : 1) les carences ou excès importants, 2) trois changements concrets avec leur effet chiffré, 3) ce qui est déjà bien. Réponse courte et directe.")
        appendLine()
        appendLine("Termine ta réponse par un bloc JSON valide (et rien après) pour que mon app applique les changements. `qty` = nouvelle quantité de cet aliment dans ce plat (0 pour le retirer), dans l'unité de l'aliment. N'utilise que les identifiants ci-dessus :")
        appendLine("{\"changes\": [{\"dish\": \"<id plat>\", \"food\": \"<id aliment>\", \"qty\": 100, \"why\": \"raison courte\"}]}")
    }

    /** Demande une recette à partir d'un souhait libre, en s'appuyant sur les aliments et le stock. */
    fun recipe(s: AppState, request: String): String = buildString {
        appendLine("Crée-moi une recette pour mon suivi nutritionnel. Ma demande : « ${request.trim()} ».")
        appendLine()
        appendLine("Aliments que j'ai déjà (identifiant | nom | unité | kcal ${"pour 100 g/ml ou par pièce"} | stock actuel) :")
        s.foods.values.forEach {
            appendLine("- ${it.id} | ${it.name} | ${it.unit} | ${nf(it.n("kcal"))} | ${nf(s.stockOf(it.id))}")
        }
        appendLine()
        appendLine("Privilégie ces aliments (surtout ceux en stock). Si un aliment indispensable manque, ajoute-le dans `new_foods` avec ses valeurs.")
        appendLine("Les quantités sont pour UNE portion, en grammes (g), millilitres (ml) ou nombre de pièces selon l'unité de l'aliment, poids crus.")
        appendLine("Nutriments pour `nut` (clé = unité) : ${nutrientKeys()}")
        appendLine()
        appendLine("Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour ni bloc de code :")
        appendLine("{\"name\": \"Nom du plat\", \"group\": \"repas\", \"sauce\": false, \"fresh\": false, \"ing\": {\"<id aliment>\": 100}, \"new_foods\": {\"<id_court_sans_accent>\": {\"name\": \"…\", \"unit\": \"g\", \"cat\": \"frais|viande|surgele|epicerie\", \"pack\": 1000, \"price\": 3.5, \"nut\": {${nutrientTemplate()}}}}}")
        appendLine("`group` vaut \"repas\" (midi ou soir) ou \"jour\" (tous les jours). `fresh` = true si le plat se cuisine sur le moment plutôt qu'en lot. `new_foods` peut être vide.")
    }
}
