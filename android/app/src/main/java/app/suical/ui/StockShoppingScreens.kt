package app.suical.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.suical.domain.CATS

@Composable
fun StockScreen(x: Ctx, vm: MainViewModel) {
    val empty = x.s.stock.values.all { it == 0.0 }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        if (empty) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Ton stock est vide. Ajoute un format de chaque produit d'un coup, ou saisis les quantités une par une.")
                Gap()
                PrimaryButton("Ajouter un format de chaque") { vm.addOnePackOfEach() }
            }
        }
        for (cat in CATS) {
            val foods = x.s.foods.values.filter { it.cat == cat.id }
            if (foods.isEmpty()) continue
            item(key = "cat-${cat.id}") { SectionTitle(cat.name) }
            items(foods, key = { it.id }) { f ->
                val dl = x.c.daysLeft(f.id)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold)
                            Hint(
                                when {
                                    dl.isInfinite() -> "Non utilisé"
                                    dl < 1 -> "Moins d'un jour"
                                    else -> "~${nf(Math.floor(dl))} jours"
                                },
                                error = dl < 3,
                            )
                        }
                        NumField(x.s.stockOf(f.id), unitLabel(f.unit), Modifier.padding(horizontal = 8.dp).weight(0.9f)) { vm.setStock(f.id, it) }
                        GhostButton("+ " + if (f.perHundred) fmtQty(f, f.pack) else nf(f.pack)) { vm.addPack(f) }
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
fun ShoppingScreen(x: Ctx, vm: MainViewModel) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    val rows = x.c.buyList(days)
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Couvrir", Modifier.padding(end = 12.dp))
                Choice(listOf(7, 14, 21, 28).map { it to if (it == 7) "1 semaine" else "${it / 7} semaines" }, days, Modifier.weight(1f)) { days = it }
            }
        }
        if (rows.isEmpty()) {
            item { Hint("Ton stock couvre déjà cette durée.") }
            return@LazyColumn
        }
        var total = 0.0
        for (cat in CATS) {
            val rs = rows.filter { x.s.foods.getValue(it.foodId).cat == cat.id }
            if (rs.isEmpty()) continue
            item(key = "cat-${cat.id}") { SectionTitle(cat.name) }
            items(rs, key = { it.foodId }) { r ->
                val f = x.s.foods.getValue(r.foodId)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold)
                            Hint("${r.packs} × ${f.packLabel}, en stock : ${fmtQty(f, x.s.stockOf(f.id))}")
                        }
                        Text(eur(r.cost), fontWeight = FontWeight.Bold)
                    }
                    Divider()
                }
            }
            total += rs.sumOf { it.cost }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                Text("Total estimé", Modifier.weight(1f))
                Text(eur(total), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Gap(12)
            PrimaryButton("J'ai fait ces courses") { vm.shoppingDone(days) }
            Hint("Prix à ajuster dans l'onglet Aliments quand tu connais ceux de ton magasin.",
                Modifier.padding(top = 12.dp))
        }
    }
}
