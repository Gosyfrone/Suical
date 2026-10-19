package app.suical

import app.suical.data.Backup
import app.suical.domain.Calc
import app.suical.domain.Defaults
import app.suical.domain.Nutrients
import app.suical.llm.LlmImport
import app.suical.llm.Prompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private val fresh = Defaults.fresh()

    @Test fun defaultPlanIsPlausible() {
        val c = Calc.of(fresh)
        println("base kcal = ${c.baseKcal}")
        assertTrue(c.baseKcal in 2000.0..3200.0)
    }

    @Test fun targetIsReached() {
        for (t in listOf(2300, 2500, 2725, 3000)) {
            val c = Calc.of(fresh.copy(target = t))
            println("target $t -> ${c.reachedKcal} clamped=${c.clamped} lv=${c.lv}")
            if (!c.clamped) assertEquals(t.toDouble(), c.reachedKcal, 40.0)
        }
    }

    @Test fun stockLimitsDishes() {
        val c0 = Calc.of(fresh)
        val d = fresh.dish("poulet_brocoli")!!
        assertEquals(0, c0.possible(d).n)
        val s = fresh.copy(stock = fresh.stock + mapOf("pates" to 1000.0, "poulet" to 450.0, "brocoli" to 1000.0, "creme" to 1000.0))
        val p = Calc.of(s).possible(d)
        assertEquals(3, p.n)
        assertEquals("poulet", p.limit)
    }

    @Test fun backupRoundTrip() {
        val s = fresh.copy(target = 2600, sauce = "fb")
        val back = Backup.fromJson(Backup.toJson(s), fresh)
        assertEquals(s.foods, back.foods)
        assertEquals(s.dishes, back.dishes)
        assertEquals(s.plan, back.plan)
        assertEquals(2600, back.target)
        val cfg = s.config.copy(maint = 2500, sessions = s.config.sessions.take(1))
        assertEquals(cfg, Backup.fromJson(Backup.toJson(s.copy(config = cfg)), fresh).config)
        assertEquals("fb", back.sauce)
    }

    @Test fun llmImportAcceptsFencesAndFlatObjects() {
        val reply = "Voici :\n```json\n{\"oeufs\": {\"kcal\": 75, \"vc\": \"1,5\", \"bogus\": 3}}\n```"
        val r = LlmImport.parseFoodValues(reply, listOf("oeufs", "lait"))
        assertEquals(75.0, r.getValue("oeufs").getValue("kcal"), 0.0)
        assertEquals(1.5, r.getValue("oeufs").getValue("vc"), 0.0)
        assertTrue("bogus" !in r.getValue("oeufs"))
        val flat = LlmImport.parseFoodValues("{\"kcal\": 10, \"p\": 2}", listOf("lait"))
        assertEquals(2.0, flat.getValue("lait").getValue("p"), 0.0)
    }

    @Test fun promptListsEveryNutrient() {
        val p = Prompts.foodValues(listOf(fresh.foods.getValue("oeufs")))
        Nutrients.keys.forEach { assertTrue(it, "\"$it\"" in p) }
    }
}
