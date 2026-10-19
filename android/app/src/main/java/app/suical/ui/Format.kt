package app.suical.ui

import app.suical.domain.Food
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val fr = Locale.FRANCE

/** Nombre à la française avec au plus `d` décimales. */
fun nf(v: Double, d: Int = 0): String =
    NumberFormat.getNumberInstance(fr).apply { maximumFractionDigits = d; minimumFractionDigits = 0 }.format(v)

fun eur(v: Double): String = NumberFormat.getCurrencyInstance(fr).format(v)

fun unitLabel(u: String) = when (u) { "pc" -> "pièces"; "boîte" -> "boîtes"; else -> u }

fun fmtQty(f: Food?, v: Double): String {
    if (f == null) return nf(v)
    return when (f.unit) {
        "g" -> if (v >= 1000) nf(v / 1000, 2) + " kg" else nf(v) + " g"
        "ml" -> if (v >= 1000) nf(v / 1000, 2) + " L" else nf(v) + " ml"
        "pc" -> nf(v) + if (v > 1) " pièces" else " pièce"
        else -> nf(v) + " " + f.unit + if (v > 1) "s" else ""
    }
}

/** Texte éditable d'un nombre : sans décimales inutiles. */
fun plain(v: Double): String = if (v == 0.0) "0" else BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

fun parseNum(s: String): Double = s.replace(',', '.').toDoubleOrNull() ?: 0.0
