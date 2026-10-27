package app.suical

import app.suical.data.Backup
import app.suical.data.OpenFoodFacts
import app.suical.domain.Calc
import app.suical.domain.Defaults
import app.suical.domain.LogEntry
import app.suical.domain.SLOT_BREAKFAST
import app.suical.domain.SLOT_DINNER
import app.suical.domain.SLOT_LUNCH
import app.suical.domain.Metabolism
import app.suical.domain.PlanConfig
import app.suical.domain.Profile
import app.suical.domain.goalKcal
import app.suical.domain.Weight
import app.suical.domain.WeightStats
import app.suical.domain.dailyTotals
import app.suical.domain.periodStats
import app.suical.domain.entryNut
import app.suical.llm.LlmImport
import app.suical.reminders.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeaturesTest {
    private val fresh = Defaults.fresh()
    private val monday = LocalDate.of(2026, 10, 19).toEpochDay() // un lundi

    // ---- plan de durée libre ----
    @Test fun planIndexFollowsWeekdays() {
        val cfg = PlanConfig(days = 5, startEpochDay = monday)
        assertEquals(0, cfg.indexOf(monday))
        assertEquals(4, cfg.indexOf(monday + 4))
        assertNull(cfg.indexOf(monday + 5)) // samedi : hors plan
        assertEquals(0, cfg.indexOf(monday + 7))
        assertEquals("Lundi", cfg.dayLabel(0))
        assertEquals("Vendredi", cfg.dayLabel(4))
    }

    @Test fun longPlanRepeatsByWholeWeeks() {
        val cfg = PlanConfig(days = 10, startEpochDay = monday)
        assertEquals(14, cfg.period)
        assertEquals(9, cfg.indexOf(monday + 9))
        assertNull(cfg.indexOf(monday + 10))
        assertEquals(0, cfg.indexOf(monday + 14))
        assertEquals("S2 Mar", cfg.dayLabel(8))
    }

    @Test fun fiveDayPlanKeepsSevenDayDivisor() {
        // Même résultat que la version avec jours fixes : consommation répartie sur 7 jours.
        val c = Calc.of(fresh.copy(config = PlanConfig(days = 5, startEpochDay = monday)))
        val total = c.weekAvgIng().getValue("pates") * 5 // 125 g x 2 repas x 5 jours
        assertEquals(1250.0, total, 0.001)
        assertEquals(1250.0 / 7, c.daily.getValue("pates"), 0.001)
    }

    @Test fun longerPlanChangesAverages() {
        val plan = fresh.plan + fresh.plan + listOf(listOf("", ""))
        val s = fresh.copy(plan = plan, config = PlanConfig(days = 11, startEpochDay = monday))
        assertEquals(11, Calc.of(s).s.plan.size)
        assertEquals(14, s.config.period)
    }

    // ---- journal, repas hors plan ----
    @Test fun extraMealCountsInDailyTotals() {
        val day = monday
        val t = day * 86_400_000L + 12 * 3_600_000L
        val extra = LogEntry(1, "", t, false, emptyMap(), "Pizza", mapOf("kcal" to 800.0, "p" to 30.0))
        val s = fresh.copy(log = listOf(extra))
        val c = Calc.of(s)
        assertEquals(800.0, c.entryNut(extra).getValue("kcal"), 0.0)
        val totals = c.dailyTotals(day - 1, day + 1).values.single()
        assertEquals(800.0, totals.getValue("kcal"), 0.0)
        assertEquals(0.0, totals.getValue("fib"), 0.0)
    }

    @Test fun readyPortionWithoutSnapshotFallsBackToDish() {
        val e = LogEntry(1, "poulet_brocoli", monday * 86_400_000L + 3_600_000L, true, emptyMap())
        val c = Calc.of(fresh)
        assertTrue(c.entryNut(e).getValue("kcal") > 400)
    }

    // ---- comparaison de périodes ----
    @Test fun periodStatsAveragesLoggedDaysOnly() {
        fun extra(day: Long, kcal: Double) =
            LogEntry(0, "", day * 86_400_000L + 12 * 3_600_000L, false, emptyMap(), "x", mapOf("kcal" to kcal, "p" to kcal / 20))
        val s = fresh.copy(
            log = listOf(extra(monday, 2000.0), extra(monday, 500.0), extra(monday + 2, 3000.0), extra(monday + 9, 9999.0)),
            weights = listOf(Weight(monday, 81.0), Weight(monday + 3, 80.4), Weight(monday + 20, 70.0)),
        )
        val p = Calc.of(s).periodStats(monday, monday + 6)
        assertEquals(2, p.loggedDays)                     // 2 jours notés sur 7
        assertEquals(2750.0, p.avgKcal, 0.001)            // (2500 + 3000) / 2
        assertEquals(-0.6, p.weightChange!!, 0.001)
        assertEquals(80.7, p.avgWeight!!, 0.001)
        val empty = Calc.of(s).periodStats(monday - 30, monday - 1)
        assertEquals(0, empty.loggedDays)
        assertNull(empty.weightChange)
    }

    // ---- poids ----
    @Test fun weightTrendIsLinearRegression() {
        val today = monday + 20
        val w = (0..20 step 2).map { Weight(monday + it, 80.0 - 0.1 * it) } // -0,7 kg/semaine
        assertEquals(-0.7, WeightStats.trendKgPerWeek(w, today)!!, 0.001)
    }

    @Test fun weightTrendNeedsEnoughData() {
        val today = monday + 10
        assertNull(WeightStats.trendKgPerWeek(listOf(Weight(monday, 80.0), Weight(monday + 8, 79.0)), today))
        assertNull(WeightStats.trendKgPerWeek(listOf(Weight(today, 80.0), Weight(today - 1, 80.0), Weight(today - 2, 80.0)), today))
    }

    @Test fun observedMaintenance() {
        // 2500 kcal/jour et -0,7 kg/semaine => ~ 2500 + 770 = 3270
        assertEquals(3270.0, WeightStats.observedMaintenance(2500.0, -0.7), 0.001)
    }

    // ---- rappels ----
    @Test fun reminderAnnouncesSessionTheDayBefore() {
        val s = fresh.copy(config = PlanConfig(startEpochDay = monday))
        val c = Calc.of(s)
        val sunday = monday - 1
        val lines = Reminders.messages(c, sunday)
        assertTrue(lines.toString(), lines.any { it.startsWith("Demain : dimanche soir") })
        val wednesday = monday + 2
        assertTrue(Reminders.messages(c, wednesday).any { it.startsWith("Demain : mercredi soir") })
        assertTrue(Reminders.messages(c, monday + 1).none { it.startsWith("Demain") })
    }

    @Test fun reminderListsLowStockOnlyForUsedFoods() {
        val s = fresh.copy(stock = fresh.stock + ("pates" to 10.0) + ("whey" to 100000.0))
        val lines = Reminders.messages(Calc.of(s), monday + 1)
        val low = lines.single { it.startsWith("Stock bas") }
        assertTrue(low, "pâtes" in low)
        assertTrue(low, "whey" !in low)
    }

    // ---- Open Food Facts ----
    @Test fun openFoodFactsParsing() {
        val json = """{"code":"1","status":1,"product":{"product_name":"Skyr","brands":"Danone, Autre",
            "product_quantity":450,"product_quantity_unit":"g",
            "nutriments":{"energy-kcal_100g":57,"proteins_100g":10.5,"carbohydrates_100g":4,"fat_100g":0.2,
            "fiber_100g":0,"calcium_100g":0.12,"vitamin-b12_100g":0.0000012}}}"""
        val f = OpenFoodFacts.parse(json)!!
        assertEquals("Skyr (Danone)", f.name)
        assertEquals(450.0, f.pack, 0.0)
        assertEquals("g", f.unit)
        assertEquals(57.0, f.n("kcal"), 0.0)
        assertEquals(10.5, f.n("p"), 0.0)
        assertEquals(120.0, f.n("ca"), 0.001)   // g -> mg
        assertEquals(1.2, f.n("b12"), 0.001)    // g -> µg
        assertEquals(0.0, f.n("fe"), 0.0)       // absent de l'étiquette
    }

    @Test fun openFoodFactsUnknownProduct() {
        assertNull(OpenFoodFacts.parse("""{"status":0,"status_verbose":"product not found"}"""))
    }

    @Test fun openFoodFactsEnergyFallsBackToKilojoules() {
        val f = OpenFoodFacts.parse("""{"status":1,"product":{"product_name":"X","nutriments":{"energy-kj_100g":418.4}}}""")!!
        assertEquals(100.0, f.n("kcal"), 0.01)
    }

    // ---- IA ----
    @Test fun recipeParsing() {
        val reply = """Voici :
            {"name":"Bowl poulet","group":"repas","sauce":true,"fresh":true,
             "ing":{"poulet":150,"pates":"120","inconnu":0},
             "new_foods":{"quinoa":{"name":"Quinoa","unit":"g","cat":"epicerie","pack":500,"price":3,"nut":{"kcal":368,"p":14}}}}"""
        val r = LlmImport.parseRecipe(reply)
        assertEquals("Bowl poulet", r.dish.name)
        assertEquals(mapOf("poulet" to 150.0, "pates" to 120.0), r.dish.ing)
        assertTrue(r.dish.sauce && r.dish.fresh)
        assertEquals("quinoa", r.newFoods.single().id)
        assertEquals(368.0, r.newFoods.single().n("kcal"), 0.0)
        assertEquals(0.0, r.newFoods.single().n("fe"), 0.0)
    }

    @Test fun recipeWithoutIngredientsIsRejected() {
        val e = runCatching { LlmImport.parseRecipe("""{"name":"Vide","ing":{}}""") }.exceptionOrNull()
        assertNotNull(e)
    }

    @Test fun changesParsingAfterProse() {
        val reply = """Ton plan manque de fibres. Je propose :
            {"changes":[{"dish":"bolognaise","food":"epinards","qty":120,"why":"fibres"},
                        {"dish":"x","food":"y","qty":"0"}, {"dish":"","food":"y","qty":3}, {"dish":"a","food":"b"}]}"""
        val ch = LlmImport.parseChanges(reply)
        assertEquals(2, ch.size)
        assertEquals(120.0, ch[0].qty, 0.0)
        assertEquals("fibres", ch[0].why)
        assertEquals(0.0, ch[1].qty, 0.0)
    }

    // ---- journal par jour et par moment ----
    @Test fun legacyEntryGetsDayAndSlotFromItsTime() {
        val noon = LocalDate.ofEpochDay(monday).atTime(12, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val e = LogEntry(1, "poulet_brocoli", noon, false, mapOf("pates" to 125.0))
        assertEquals(monday, e.day)
        assertEquals(SLOT_LUNCH, e.slot)
        assertTrue(e.fromStock)
        assertFalse(LogEntry(2, "", noon, false, emptyMap(), "Pizza", mapOf("kcal" to 800.0)).usesStock)
    }

    @Test fun entryLoggedForAPastDayCountsOnThatDay() {
        // Saisi aujourd'hui (t), mais rangé au lundi : c'est le jour qui compte, pas l'heure de saisie.
        val e = LogEntry(1, "", System.currentTimeMillis(), false, emptyMap(), "Kebab", mapOf("kcal" to 900.0),
            day = monday, slot = SLOT_DINNER, fromStock = false)
        val totals = Calc.of(fresh.copy(log = listOf(e))).dailyTotals(monday, monday)
        assertEquals(900.0, totals.getValue(monday).getValue("kcal"), 0.0)
    }

    @Test fun backupKeepsDaySlotAndStockFlags() {
        val e = LogEntry(0, "poulet_brocoli", 1_000, true, mapOf("pates" to 125.0), day = monday + 3, slot = SLOT_DINNER, fromStock = false)
        val back = Backup.fromJson(Backup.toJson(fresh.copy(log = listOf(e))), fresh).log.single()
        assertEquals(monday + 3, back.day)
        assertEquals(SLOT_DINNER, back.slot)
        assertTrue(back.ready)
        assertEquals(125.0, back.ing.getValue("pates"), 0.0)
        assertEquals(SLOT_BREAKFAST, Backup.fromJson(Backup.toJson(fresh), fresh).dish("petit_dej")!!.slot)
    }

    // ---- sauvegarde ----
    @Test fun backupKeepsWeightsExtrasAndPlanLength() {
        val extra = LogEntry(0, "", 1_000, false, emptyMap(), "Pizza", mapOf("kcal" to 800.0))
        val s = fresh.copy(
            weights = listOf(Weight(monday, 80.5), Weight(monday + 1, 80.2)),
            log = listOf(extra),
            plan = fresh.plan + listOf(listOf("poulet_brocoli", "")),
            config = PlanConfig(days = 6, startEpochDay = monday, remindersOn = true, reminderHour = 7),
        )
        val back = Backup.fromJson(Backup.toJson(s), fresh)
        assertEquals(s.weights, back.weights)
        assertEquals("Pizza", back.log.single().name)
        assertEquals(800.0, back.log.single().custom.getValue("kcal"), 0.0)
        assertEquals(6, back.plan.size)
        assertEquals(6, back.config.days)
        assertTrue(back.config.remindersOn)
        assertEquals(7, back.config.reminderHour)
        assertEquals(monday, back.config.startEpochDay)
    }

    // ---- maintien théorique ----
    @Test fun theoreticalMaintenanceUsesMifflinThenKatch() {
        val p = Profile(male = true, age = 30, heightCm = 180.0, steps = 3000, sessionsPerWeek = 0.0)
        val t = Metabolism.theoretical(p, 80.0)
        assertEquals("Mifflin-St Jeor", t.formula)
        assertEquals(1780.0, t.bmr, 0.01) // 800 + 1125 - 150 + 5
        assertEquals(t.bmr * 1.2, t.total, 0.01) // ni pas en plus ni séance
        assertEquals("Katch-McArdle", Metabolism.theoretical(p.copy(bodyFatPct = 15.0), 80.0).formula)
    }

    @Test fun stepsAndTrainingAddToMaintenance() {
        val base = Profile(steps = 3000, sessionsPerWeek = 0.0)
        val t0 = Metabolism.theoretical(base, 80.0).total
        assertTrue(Metabolism.theoretical(base.copy(steps = 10000), 80.0).total > t0 + 200)
        // 4 séances modérées d'1 h : (5-1) MET x 80 kg x 1 h x 4 / 7
        assertEquals(t0 + 4 * 80.0 * 4 / 7, Metabolism.theoretical(base.copy(sessionsPerWeek = 4.0), 80.0).total, 0.01)
    }

    @Test fun goalsRoundTo50AndProfileSurvivesBackup() {
        assertEquals(2450, goalKcal(2725.0, -300))
        val s = fresh.copy(config = PlanConfig(profile = Profile(male = false, age = 31, steps = 12000, intensity = 2)))
        assertEquals(s.config.profile, Backup.fromJson(Backup.toJson(s), fresh).config.profile)
    }
}
