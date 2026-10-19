package app.suical.data

import app.suical.domain.AppState
import app.suical.domain.DEFAULT_LLM_PKG
import app.suical.domain.Dish
import app.suical.domain.Food
import app.suical.domain.LogEntry
import app.suical.domain.Lever
import app.suical.domain.PlanConfig
import app.suical.domain.Session
import app.suical.domain.Weight
import app.suical.domain.Nutrients
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export / import JSON de tout l'état. Le format est celui de la version web (« suical-v2 »),
 * donc un export du site s'importe tel quel dans l'app, et inversement.
 */
object Backup {
    fun configToJson(c: PlanConfig): JSONObject = JSONObject().apply {
        put("maint", c.maint)
        put("sauceQty", c.sauceQty)
        put("days", c.days)
        put("start", c.startEpochDay)
        put("remindersOn", c.remindersOn)
        put("reminderHour", c.reminderHour)
        put("sessions", JSONArray().also { a ->
            c.sessions.forEach { s ->
                a.put(JSONObject().put("id", s.id).put("when", s.whenLabel).put("days", JSONArray(s.days)))
            }
        })
        put("levers", JSONArray().also { a ->
            c.levers.forEach { l ->
                a.put(JSONObject().put("id", l.id).put("label", l.label).put("item", l.item).put("scope", l.scope)
                    .put("base", l.base).put("min", l.min).put("max", l.max).put("priority", l.priority))
            }
        })
    }

    /** Champs absents ou illisibles : valeurs par défaut. */
    fun configFromJson(o: JSONObject?): PlanConfig {
        val d = PlanConfig()
        if (o == null) return d
        return PlanConfig(
            maint = o.optInt("maint", d.maint),
            sauceQty = o.optDouble("sauceQty", d.sauceQty),
            days = o.optInt("days", d.days).coerceIn(1, 28),
            startEpochDay = o.optLong("start", d.startEpochDay),
            remindersOn = o.optBoolean("remindersOn", d.remindersOn),
            reminderHour = o.optInt("reminderHour", d.reminderHour).coerceIn(0, 23),
            sessions = o.optJSONArray("sessions")?.let { a ->
                List(a.length()) { i ->
                    val s = a.getJSONObject(i)
                    val days = s.optJSONArray("days")
                    Session(s.getString("id"), s.optString("when", ""), List(days?.length() ?: 0) { days!!.getInt(it) })
                }
            } ?: d.sessions,
            levers = o.optJSONArray("levers")?.let { a ->
                List(a.length()) { i ->
                    val l = a.getJSONObject(i)
                    Lever(l.getString("id"), l.optString("label", ""), l.optString("item", ""), l.optString("scope", "repas"),
                        l.optDouble("base"), l.optDouble("min"), l.optDouble("max"), l.optInt("priority", i + 1))
                }
            } ?: d.levers,
        )
    }

    fun toJson(s: AppState): String {
        val o = JSONObject()
        o.put("v", 2)
        o.put("items", JSONObject().also { items ->
            for (f in s.foods.values) items.put(f.id, JSONObject().apply {
                put("name", f.name); put("unit", f.unit); put("cat", f.cat)
                put("pack", f.pack); put("packLabel", f.packLabel); put("price", f.price)
                put("nut", JSONObject().also { n -> Nutrients.keys.forEach { k -> n.put(k, f.n(k)) } })
            })
        })
        o.put("dishes", JSONArray().also { arr ->
            for (d in s.dishes) arr.put(JSONObject().apply {
                put("id", d.id); put("name", d.name); put("group", d.group)
                put("sauce", d.sauce); put("fresh", d.fresh)
                put("ing", JSONObject().also { i -> d.ing.forEach { (k, q) -> i.put(k, q) } })
            })
        })
        o.put("plan", JSONArray().also { p -> s.plan.forEach { day -> p.put(JSONArray(day)) } })
        o.put("stock", JSONObject().also { st -> s.stock.forEach { (k, q) -> st.put(k, q) } })
        o.put("ready", JSONObject().also { r -> s.ready.forEach { (k, n) -> r.put(k, n) } })
        o.put("log", JSONArray().also { arr ->
            for (e in s.log) arr.put(JSONObject().apply {
                put("dish", e.dishId); put("t", e.t)
                if (e.ready) put("ready", true) else put("ing", JSONObject().also { i -> e.ing.forEach { (k, q) -> i.put(k, q) } })
                if (e.name.isNotEmpty()) put("name", e.name)
                if (e.custom.isNotEmpty()) put("custom", JSONObject().also { i -> e.custom.forEach { (k, q) -> i.put(k, q) } })
            })
        })
        if (s.target != null) o.put("target", s.target)
        o.put("sauce", s.sauce)
        o.put("config", configToJson(s.config))
        o.put("weights", JSONArray().also { a -> s.weights.forEach { a.put(JSONObject().put("day", it.epochDay).put("kg", it.kg)) } })
        return o.toString(1)
    }

    /** Lève une exception si le texte n'est pas un export lisible. */
    fun fromJson(text: String, base: AppState): AppState {
        val o = JSONObject(text)
        val items = o.optJSONObject("items") ?: throw IllegalArgumentException("Pas d'aliments dans l'export")
        val foods = LinkedHashMap<String, Food>()
        for (id in items.keys()) {
            val f = items.getJSONObject(id)
            val n = f.optJSONObject("nut")
            foods[id] = Food(
                id, f.optString("name", id), f.optString("unit", "g"), f.optString("cat", "epicerie"),
                f.optDouble("pack", 1.0), f.optString("packLabel", ""), f.optDouble("price", 0.0),
                Nutrients.keys.associateWith { k -> n?.optDouble(k, 0.0) ?: 0.0 },
            )
        }
        val dishes = ArrayList<Dish>()
        o.optJSONArray("dishes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val ing = d.optJSONObject("ing")
                dishes += Dish(
                    d.getString("id"), d.optString("name", d.getString("id")), d.optString("group", "repas"),
                    d.optBoolean("sauce"), d.optBoolean("fresh"),
                    ing?.keys()?.asSequence()?.associateWith { ing.getDouble(it) } ?: emptyMap(),
                )
            }
        }
        var plan = base.plan
        o.optJSONArray("plan")?.let { arr ->
            if (arr.length() > 0) plan = List(arr.length()) { d ->
                val row = arr.getJSONArray(d)
                List(2) { s -> row.optString(s, "") }
            }
        }
        val stockO = o.optJSONObject("stock")
        val readyO = o.optJSONObject("ready")
        val log = ArrayList<LogEntry>()
        o.optJSONArray("log")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val ing = e.optJSONObject("ing")
                log += LogEntry(
                    0, e.optString("dish", ""), e.optLong("t"), e.optBoolean("ready"),
                    ing?.keys()?.asSequence()?.associateWith { ing.getDouble(it) } ?: emptyMap(),
                    e.optString("name", ""),
                    e.optJSONObject("custom")?.let { c -> c.keys().asSequence().associateWith { c.getDouble(it) } } ?: emptyMap(),
                )
            }
        }
        return AppState(
            foods = foods,
            dishes = dishes.ifEmpty { base.dishes },
            plan = plan,
            stock = foods.keys.associateWith { stockO?.optDouble(it, 0.0)?.coerceAtLeast(0.0) ?: 0.0 },
            ready = readyO?.keys()?.asSequence()?.associateWith { readyO.getInt(it) }?.filterValues { it > 0 } ?: emptyMap(),
            log = log,
            target = if (o.has("target") && !o.isNull("target")) o.getInt("target") else null,
            sauce = if (o.optString("sauce") == "fb") "fb" else "creme",
            llmPkg = base.llmPkg.ifBlank { DEFAULT_LLM_PKG },
            config = (if (o.has("config")) configFromJson(o.optJSONObject("config")) else base.config).copy(days = plan.size),
            weights = o.optJSONArray("weights")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Weight(it.getLong("day"), it.getDouble("kg")) } } } ?: emptyList(),
        )
    }
}
