package app.suical.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val COURSES_BUY = 0
const val COURSES_STOCK = 1

/** Onglet Courses : liste à acheter et stock sous un même sélecteur. */
@Composable
fun CoursesScreen(x: Ctx, vm: MainViewModel, section: Int, onSection: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 16.dp)) { ScreenHeader("Courses") }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
            Segmented(listOf(COURSES_BUY to "À acheter", COURSES_STOCK to "Stock"), section, fill = true, onSelect = onSection)
        }
        if (section == COURSES_BUY) ShoppingScreen(x, vm) else StockScreen(x, vm)
    }
}
