package app.suical.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.material3.TextButton
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

private class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Plats", Icons.Default.Home),
    Tab("Semaine", Icons.Default.DateRange),
    Tab("Stock", Icons.Default.List),
    Tab("Courses", Icons.Default.ShoppingCart),
    Tab("Aliments", Icons.Default.Create),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val x by vm.ctx.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var stats by rememberSaveable { mutableStateOf(false) }
    var maint by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it)
        }
    }
    BackHandler(settings || stats || maint) { settings = false; stats = false; maint = false }
    val sub = settings || stats || maint

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (sub) IconButton({ settings = false; stats = false; maint = false }) { Icon(Icons.Default.ArrowBack, "Retour") }
                },
                title = {
                    Column {
                        Text(if (settings) "Réglages" else if (stats) "Statistiques" else if (maint) "Maintien" else "Suical", fontWeight = FontWeight.ExtraBold)
                        x?.let {
                            if (!sub) Hint("Objectif ${nf((it.s.target ?: (it.c.baseKcal / 50).toInt() * 50).toDouble())} kcal · plan à ${nf(it.c.nutOf(it.c.weekAvgIng()).getValue("kcal"))} kcal")
                        }
                    }
                },
                actions = {
                    if (!sub) {
                        TextButton({ maint = true }) { Text("Maintien") }
                        TextButton({ stats = true }) { Text("Stats") }
                        IconButton({ settings = true }) { Icon(Icons.Default.Settings, "Réglages") }
                    }
                },
            )
        },
        bottomBar = {
            if (!sub) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondary,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface),
                        icon = { Icon(t.icon, null) }, label = { Text(t.label, maxLines = 1) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val ctx = x
            if (ctx != null) when {
                settings -> SettingsScreen(ctx, vm)
                stats -> StatsScreen(ctx, vm)
                maint -> MaintenanceScreen(ctx, vm)
                tab == 0 -> DishesScreen(ctx, vm)
                tab == 1 -> WeekScreen(ctx, vm)
                tab == 2 -> StockScreen(ctx, vm)
                tab == 3 -> ShoppingScreen(ctx, vm)
                else -> FoodsScreen(ctx, vm)
            }
        }
    }
}
