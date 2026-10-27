package app.suical.llm

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.suical.domain.Dish
import app.suical.domain.Food
import app.suical.domain.Nutrients
import org.json.JSONObject

/** Envoi d'un prompt vers une app IA et récupération de sa réponse. */
object LlmBridge {

    /**
     * Copie le prompt dans le presse-papier (filet de sécurité) puis l'envoie par un partage Android
     * ciblé sur `pkg`. Si l'app n'est pas installée (ou `pkg` vide), ouvre le sélecteur d'applications.
     * Avec `image`, la photo est jointe au message.
     * @return true si l'app ciblée a été ouverte directement.
     */
    fun send(ctx: Context, text: String, pkg: String, image: Uri? = null): Boolean {
        copy(ctx, text)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = if (image != null) "image/jpeg" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (image != null) {
                putExtra(Intent.EXTRA_STREAM, image)
                clipData = ClipData.newRawUri("photo", image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (pkg.isNotBlank()) {
            try {
                ctx.startActivity(Intent(share).setPackage(pkg))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        ctx.startActivity(Intent.createChooser(share, "Envoyer à…"))
        return false
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Suical", text))
    }

    fun paste(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
    }
}

/** Un élément estimé par l'IA qui n'est pas dans mes aliments : `nut` vaut pour `qty` (grammes, ou portions si `grams` est faux). */
data class EstimatedItem(val name: String, val qty: Double, val grams: Boolean, val nut: Map<String, Double>) {
    /** Valeurs pour une autre quantité, à proportion. */
    fun nutFor(q: Double): Map<String, Double> = if (qty <= 0) nut else nut.mapValues { it.value * q / qty }
}

/** Repas estimé par l'IA : `ing` = mes aliments et leurs quantités, `others` = le reste. */
data class MealEstimate(val name: String, val ing: Map<String, Double>, val others: List<EstimatedItem>, val note: String = "")

/** Une recette proposée par l'IA, pas encore enregistrée. */
data class RecipeDraft(val dish: Dish, val newFoods: List<Food>)

/** Un changement de quantité proposé par l'IA : `qty` = nouvelle quantité (0 = retirer l'aliment). */
data class Change(val dishId: String, val foodId: String, val qty: Double, val why: String)

/** Lecture tolérante des réponses de [Prompts]. */
object LlmImport {

    /** Extrait le premier objet JSON d'un texte (ignore les ``` et le texte autour). */
    private fun jsonObject(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("Aucun JSON dans le presse-papier")
        return JSONObject(text.substring(start, end + 1))
    }

    private fun number(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.replace(',', '.').trim().toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() && it >= 0 }

    private fun readNutrients(o: JSONObject): Map<String, Double> {
        val m = LinkedHashMap<String, Double>()
        for (k in Nutrients.keys) if (o.has(k)) number(o.opt(k))?.let { m[k] = it }
        return m
    }

    /**
     * @param expected identifiants demandés. Si la réponse est un objet plat (un seul aliment, sans
     * l'identifiant en clé) et qu'un seul aliment était attendu, on l'associe à celui-ci.
     * @return identifiant -> (clé du nutriment -> valeur), uniquement les clés connues.
     */
    fun parseFoodValues(text: String, expected: List<String>): Map<String, Map<String, Double>> {
        val root = jsonObject(text)
        val out = LinkedHashMap<String, Map<String, Double>>()
        if (expected.size == 1 && root.keys().asSequence().any { it in Nutrients.byKey }) {
            out[expected[0]] = readNutrients(root)
        } else {
            for (id in root.keys()) {
                val o = root.optJSONObject(id) ?: continue
                if (id in expected) out[id] = readNutrients(o)
            }
        }
        val usable = out.filterValues { it.isNotEmpty() }
        if (usable.isEmpty()) throw IllegalArgumentException("Aucune valeur reconnue dans la réponse")
        return usable
    }

    /** Recette : `id` des plats et aliments vides, à fixer par l'appelant (il connaît les identifiants déjà pris). */
    fun parseRecipe(text: String): RecipeDraft {
        val o = jsonObject(text)
        val name = o.optString("name").trim().ifEmpty { throw IllegalArgumentException("Recette sans nom") }
        val ingO = o.optJSONObject("ing") ?: throw IllegalArgumentException("Recette sans ingrédients")
        val ing = LinkedHashMap<String, Double>()
        for (k in ingO.keys()) number(ingO.opt(k))?.let { if (it > 0) ing[k] = it }
        if (ing.isEmpty()) throw IllegalArgumentException("Recette sans ingrédients")

        val foods = ArrayList<Food>()
        o.optJSONObject("new_foods")?.let { nf ->
            for (id in nf.keys()) {
                val f = nf.optJSONObject(id) ?: continue
                val n = readNutrients(f.optJSONObject("nut") ?: JSONObject())
                val unit = f.optString("unit").takeIf { it in listOf("g", "ml", "pc", "boîte") } ?: "g"
                val cat = f.optString("cat").takeIf { it in listOf("frais", "viande", "surgele", "epicerie") } ?: "epicerie"
                foods += Food(id, f.optString("name", id), unit, cat, number(f.opt("pack"))?.takeIf { it > 0 } ?: 1000.0,
                    "", number(f.opt("price")) ?: 0.0, Nutrients.keys.associateWith { n[it] ?: 0.0 })
            }
        }
        val group = if (o.optString("group") == "jour") "jour" else "repas"
        return RecipeDraft(Dish("", name, group, o.optBoolean("sauce"), o.optBoolean("fresh"), ing), foods)
    }

    /** Estimation de [Prompts.mealEstimate]. Un identifiant d'aliment inconnu est gardé seulement s'il vient avec ses valeurs. */
    fun parseMealEstimate(text: String, known: Set<String>): MealEstimate {
        val o = jsonObject(text)
        val arr = o.optJSONArray("items") ?: throw IllegalArgumentException("Aucun élément dans la réponse")
        val ing = LinkedHashMap<String, Double>()
        val others = ArrayList<EstimatedItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val food = item.optString("food")
            val qty = number(item.opt("qty"))
            if (food in known && qty != null && qty > 0) {
                ing[food] = (ing[food] ?: 0.0) + qty
                continue
            }
            val nut = readNutrients(item.optJSONObject("nut") ?: continue)
            if (nut.isEmpty()) continue
            val grams = number(item.opt("grams"))?.takeIf { g -> g > 0 }
            val name = item.optString("name").trim().ifEmpty { food.ifEmpty { "Élément" } }
            others += EstimatedItem(name, grams ?: 1.0, grams != null, Nutrients.keys.associateWith { k -> nut[k] ?: 0.0 })
        }
        if (ing.isEmpty() && others.isEmpty()) throw IllegalArgumentException("Aucun élément exploitable dans la réponse")
        return MealEstimate(o.optString("name").trim().ifEmpty { "Repas estimé" }, ing, others, o.optString("note").trim())
    }

    /** Changements proposés par [Prompts.weekReview]. */
    fun parseChanges(text: String): List<Change> {
        val arr = jsonObject(text).optJSONArray("changes") ?: throw IllegalArgumentException("Aucune proposition dans la réponse")
        val out = ArrayList<Change>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val dish = c.optString("dish")
            val food = c.optString("food")
            val qty = number(c.opt("qty")) ?: continue
            if (dish.isNotEmpty() && food.isNotEmpty()) out += Change(dish, food, qty, c.optString("why"))
        }
        if (out.isEmpty()) throw IllegalArgumentException("Aucune proposition exploitable")
        return out
    }
}
