package app.suical.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Aujourd'hui", NavIcons.Home),
    Tab("Semaine", NavIcons.Calendar),
    Tab("Suivi", NavIcons.Chart),
    Tab("Courses", NavIcons.Cart),
    Tab("Plus", NavIcons.More),
)
private const val TAB_TODAY = 0
private const val TAB_WEEK = 1
private const val TAB_SUIVI = 2
private const val TAB_COURSES = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val x by vm.ctx.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var suivi by rememberSaveable { mutableIntStateOf(SUIVI_STATS) }
    var courses by rememberSaveable { mutableIntStateOf(COURSES_BUY) }
    var food by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it)
        }
    }
    val back = { if (food != null) food = null else sub = null }
    BackHandler(sub != null) { back() }

    val ctx0 = x
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            val s = sub
            if (s != null) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton({ back() }) { Icon(Icons.Default.ArrowBack, "Retour") } },
                title = { Text(if (s == SUB_ALIMENTS && food != null) ctx0?.s?.foods?.get(food)?.name ?: "Aliment" else subTitle(s), fontWeight = FontWeight.ExtraBold, maxLines = 1) },
            )
        },
        bottomBar = {
            if (sub == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondary,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        icon = { Icon(t.icon, null) }, label = { Text(t.label, maxLines = 1, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val ctx = x
            if (ctx != null) when (sub) {
                SUB_PLATS -> PlatsScreen(ctx, vm)
                SUB_ALIMENTS -> FoodsScreen(ctx, vm, food) { food = it }
                SUB_REGLAGES -> SettingsScreen(ctx, vm)
                else -> when (tab) {
                    TAB_TODAY -> TodayScreen(ctx, vm)
                    TAB_WEEK -> WeekScreen(ctx, vm) { tab = TAB_SUIVI; suivi = SUIVI_MAINT }
                    TAB_SUIVI -> SuiviScreen(ctx, vm, suivi) { suivi = it }
                    TAB_COURSES -> CoursesScreen(ctx, vm, courses) { courses = it }
                    else -> PlusScreen(ctx) { sub = it; food = null }
                }
            }
        }
    }
}
