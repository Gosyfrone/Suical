package app.suical.data

import app.suical.domain.Food
import app.suical.domain.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Recherche d'un produit par code-barres sur Open Food Facts (seul appel réseau de l'app, à la demande). */
object OpenFoodFacts {
    /** Nutriment -> (clé Open Food Facts, facteur pour passer des grammes à l'unité de l'app). */
    private val MAP = mapOf(
        "p" to ("proteins" to 1.0), "g" to ("carbohydrates" to 1.0), "l" to ("fat" to 1.0), "fib" to ("fiber" to 1.0),
        "vc" to ("vitamin-c" to 1e3), "va" to ("vitamin-a" to 1e6), "ve" to ("vitamin-e" to 1e3),
        "vk" to ("vitamin-k" to 1e6), "b9" to ("vitamin-b9" to 1e6), "b12" to ("vitamin-b12" to 1e6),
        "ca" to ("calcium" to 1e3), "fe" to ("iron" to 1e3), "mg" to ("magnesium" to 1e3), "zn" to ("zinc" to 1e3),
        "k" to ("potassium" to 1e3), "io" to ("iodine" to 1e6), "se" to ("selenium" to 1e6),
    )

    /** @return null si le produit est inconnu. Les micronutriments absents de l'étiquette restent à 0. */
    suspend fun fetch(barcode: String): Food? = withContext(Dispatchers.IO) {
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
            "?fields=product_name,product_name_fr,brands,product_quantity,product_quantity_unit,nutriments")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Suical-Android/1.0")
        try {
            if (conn.responseCode != 200) return@withContext null
            parse(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    /** Transforme la réponse JSON en aliment (id à fixer par l'appelant). Séparé de [fetch] pour être testable. */
    fun parse(json: String): Food? {
        val root = JSONObject(json)
        if (root.optInt("status") != 1) return null
        val p = root.optJSONObject("product") ?: return null
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val name = p.optString("product_name_fr").ifBlank { p.optString("product_name") }.ifBlank { return null }
        val brand = p.optString("brands").substringBefore(",").trim()

        val nut = HashMap<String, Double>()
        for (k in Nutrients.keys) nut[k] = 0.0
        nut["kcal"] = n.optDouble("energy-kcal_100g", Double.NaN).takeIf { !it.isNaN() }
            ?: n.optDouble("energy-kj_100g", 0.0) / 4.184
        for ((k, v) in MAP) {
            val (offKey, factor) = v
            val x = n.optDouble(offKey + "_100g", Double.NaN)
            if (!x.isNaN() && x >= 0) nut[k] = Math.round(x * factor * 100) / 100.0
        }
        val qty = p.optDouble("product_quantity", 0.0)
        val unit = if (p.optString("product_quantity_unit") == "ml") "ml" else "g"
        return Food(
            id = "", name = if (brand.isNotEmpty() && !name.contains(brand, true)) "$name ($brand)" else name,
            unit = unit, cat = "epicerie",
            pack = if (qty > 0) qty else 1000.0,
            packLabel = if (qty > 0) "${qty.toLong()} $unit" else "",
            price = 0.0, nut = nut,
        )
    }
}
