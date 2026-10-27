package app.suical.domain

/** Données de départ (identiques à la version web). */
object Defaults {
    /** Valeurs dans l'ordre de [Nutrients.all]. */
    private fun f(id: String, name: String, unit: String, cat: String, pack: Number, packLabel: String, price: Number, vararg n: Number) =
        Food(id, name, unit, cat, pack.toDouble(), packLabel, price.toDouble(),
            Nutrients.all.mapIndexed { i, nu -> nu.key to n[i].toDouble() }.toMap())

    private fun d(id: String, name: String, group: String, sauce: Boolean, fresh: Boolean, vararg ing: Pair<String, Number>) =
        Dish(id, name, group, sauce, fresh, ing.associate { it.first to it.second.toDouble() })

    val foods: List<Food> = listOf(
        f("oeufs", "Œufs", "pc", "frais", 12, "boîte de 12", 3.80, 72, 6.3, 0.4, 5, 0, 0, 80, 0.5, 0.2, 24, 0.45, 28, 0.9, 6, 0.65, 69, 25, 15),
        f("fromage_blanc", "Fromage blanc 0 %", "g", "frais", 1000, "pot de 1 kg", 2.10, 47, 7.5, 4, 0.2, 0, 0.5, 2, 0, 0, 15, 0.4, 120, 0.1, 10, 0.4, 150, 15, 3),
        f("creme", "Crème 4 %", "ml", "frais", 200, "brique de 20 cl", 1.10, 70, 3, 5, 4, 0, 0.5, 35, 0.1, 0.3, 5, 0.3, 100, 0.05, 10, 0.3, 130, 10, 2),
        f("lait", "Lait entier", "ml", "frais", 1000, "bouteille de 1 L", 1.25, 65, 3.2, 4.8, 3.6, 0, 1, 32, 0.1, 0.3, 5, 0.45, 120, 0.05, 11, 0.4, 150, 20, 2),
        f("bananes", "Bananes", "pc", "frais", 6, "6 bananes", 2.00, 107, 1.3, 24, 0.4, 2.6, 10, 4, 0.1, 0.6, 24, 0, 6, 0.3, 32, 0.2, 430, 0, 1),
        f("poulet", "Filets de poulet", "g", "viande", 2500, "barquette de 2,5 kg", 24.99, 115, 23, 0, 1.3, 0, 0, 9, 0.2, 0.3, 4, 0.3, 5, 0.5, 28, 0.7, 350, 5, 20),
        f("macreuse", "Macreuse hachée", "g", "viande", 1000, "1 kg à la boucherie", 14.00, 125, 21, 0, 4.5, 0, 0, 0, 0.2, 1, 7, 2.5, 6, 2.3, 22, 5, 330, 3, 18),
        f("lieu", "Lieu noir", "g", "viande", 1000, "sachet de 1 kg", 10.00, 82, 19, 0, 0.8, 0, 0, 10, 0.3, 0, 7, 3, 10, 0.3, 30, 0.4, 350, 100, 35),
        f("foie", "Foie de volaille", "g", "viande", 500, "barquette de 500 g", 3.00, 120, 17, 0.7, 5, 0, 18, 3300, 0.7, 1, 590, 17, 8, 9, 19, 2.7, 230, 3, 55),
        f("thon", "Thon au naturel", "boîte", "epicerie", 1, "1 boîte de 140 g", 1.90, 120, 27, 0, 1, 0, 0, 16, 0.3, 0, 4, 2.6, 12, 1.2, 28, 0.6, 250, 20, 83),
        f("epinards", "Épinards en galets", "g", "surgele", 1000, "sachet de 1 kg", 2.30, 25, 3, 1.5, 0.4, 2.5, 12, 470, 2, 450, 120, 0, 130, 2, 75, 0.5, 350, 2, 1),
        f("poivrons", "Poivrons en lanières", "g", "surgele", 1000, "sachet de 1 kg", 3.50, 30, 1, 5, 0.3, 1.8, 120, 120, 1.5, 5, 30, 0, 8, 0.4, 12, 0.2, 200, 0, 0),
        f("brocoli", "Brocoli", "g", "surgele", 1000, "sachet de 1 kg", 2.80, 30, 3, 3, 0.4, 3, 60, 30, 1.4, 100, 70, 0, 45, 0.7, 18, 0.4, 250, 0, 1),
        f("edamame", "Edamame", "g", "surgele", 500, "sachet de 500 g", 3.20, 120, 11, 4, 5, 5, 6, 8, 0.7, 27, 310, 0, 63, 2.3, 64, 1.4, 440, 0, 1),
        f("fruits_rouges", "Fruits rouges", "g", "surgele", 1000, "sachet de 1 kg", 6.50, 45, 1, 8, 0.4, 5, 60, 5, 1, 8, 25, 0, 30, 0.8, 18, 0.3, 200, 0, 0),
        f("pates", "Pâtes", "g", "epicerie", 1000, "paquet de 1 kg", 1.30, 355, 12.5, 71, 1.5, 3, 0, 0, 0.1, 0.1, 18, 0, 20, 1.3, 50, 1.3, 220, 2, 20),
        f("avoine", "Flocons d'avoine", "g", "epicerie", 1000, "paquet de 1 kg", 1.80, 370, 13, 58, 7, 10, 0, 0, 0.6, 2, 56, 0, 54, 4.5, 140, 3.5, 400, 1, 10),
        f("beurre_cacahuete", "Beurre de cacahuète", "g", "epicerie", 350, "pot de 350 g", 3.00, 600, 25, 13, 50, 6, 0, 0, 9, 0.5, 90, 0, 45, 1.8, 170, 2.8, 560, 0, 4),
        f("amandes", "Amandes", "g", "epicerie", 250, "sachet de 250 g", 3.20, 600, 21, 7, 52, 11, 0, 0, 25, 0, 45, 0, 260, 3.7, 270, 3.1, 730, 0, 3),
        f("coulis", "Coulis de tomate", "g", "epicerie", 700, "bocal de 700 g", 1.30, 30, 1.4, 5, 0.3, 1.5, 8, 35, 1.5, 5, 10, 0, 15, 0.9, 18, 0.2, 330, 0, 0),
        f("huile", "Huile d'olive vierge extra", "ml", "epicerie", 750, "bouteille de 75 cl", 7.50, 820, 0, 0, 91, 0, 0, 0, 13, 55, 0, 0, 0, 0.5, 0, 0, 0, 0, 0),
        f("whey", "Whey", "g", "epicerie", 1000, "sachet de 1 kg", 30.00, 390, 78, 7, 6, 0, 0, 0, 0, 0, 10, 1.5, 450, 1, 70, 2, 500, 30, 10),
    )

    val dishes: List<Dish> = listOf(
        d("poulet_brocoli", "Poulet et brocoli", GROUP_MEAL, true, false, "pates" to 125, "poulet" to 150, "brocoli" to 150),
        d("poulet_poivrons", "Poulet et poivrons", GROUP_MEAL, true, false, "pates" to 125, "poulet" to 150, "poivrons" to 150),
        d("bolognaise", "Bolognaise enrichie", GROUP_MEAL, false, false, "pates" to 125, "macreuse" to 150, "coulis" to 175, "epinards" to 75, "poivrons" to 50, "huile" to 5),
        d("lieu_edamame", "Lieu et edamame", GROUP_MEAL, true, false, "pates" to 125, "lieu" to 150, "edamame" to 150),
        d("foie_epinards", "Foie de volaille et épinards", GROUP_MEAL, true, true, "pates" to 125, "foie" to 150, "epinards" to 100, "huile" to 5),
        d("pates_thon", "Pâtes thon-tomate", GROUP_MEAL, false, true, "pates" to 125, "thon" to 1, "coulis" to 175, "epinards" to 75, "huile" to 5),
        d("petit_dej", "Petit-déjeuner", GROUP_DAILY, false, true, "avoine" to 50, "fromage_blanc" to 150, "oeufs" to 2).copy(slot = SLOT_BREAKFAST),
        d("bol_avoine", "Bol d'avoine", GROUP_DAILY, false, true, "avoine" to 60, "lait" to 200, "beurre_cacahuete" to 20, "bananes" to 1, "fruits_rouges" to 100),
        d("collation", "Collation du soir", GROUP_DAILY, false, true, "fromage_blanc" to 150, "whey" to 15, "amandes" to 20),
    )

    val plan: List<List<String>> = listOf(
        listOf("poulet_brocoli", "bolognaise"),
        listOf("poulet_brocoli", "bolognaise"),
        listOf("poulet_brocoli", "foie_epinards"),
        listOf("poulet_poivrons", "lieu_edamame"),
        listOf("poulet_poivrons", "lieu_edamame"),
    )

    fun fresh() = AppState(
        foods = foods.associateBy { it.id },
        dishes = dishes,
        plan = plan,
        stock = foods.associate { it.id to 0.0 },
        ready = emptyMap(),
        log = emptyList(),
        target = null,
        sauce = "creme",
    )
}
