package app.suical.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icônes de la barre du bas : tracés au trait, comme dans la maquette. Teintées par `Icon`. */
object NavIcons {
    private fun stroke(name: String, d: String, width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Home = stroke("home", "M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Calendar = stroke("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 10h18M8 3v4M16 3v4")
    val Chart = stroke("chart", "M3 20h18M6 16v-5M11 16V6M16 16v-8")
    val Cart = stroke("cart", "M2 3h3l2.5 12h11l2-8H6M8 20h.01M18 20h.01", 2.4f)
    val More = stroke("more", "M5 12h.01M12 12h.01M19 12h.01", 3.6f)
}
