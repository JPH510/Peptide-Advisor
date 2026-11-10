package com.peptideadvisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.peptideadvisor.ui.ask.AskScreen
import com.peptideadvisor.ui.log.CalculatorScreen
import com.peptideadvisor.ui.log.LogScreen
import com.peptideadvisor.ui.log.VialDetailScreen
import com.peptideadvisor.ui.settings.SettingsScreen
import com.peptideadvisor.ui.theme.PeptideAdvisorTheme
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.display
import com.peptideadvisor.ui.vendors.VendorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeptideAdvisorTheme { RootScaffold() }
        }
    }
}

private sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Ask : Destination("ask", "Ask", Icons.Filled.Search)
    data object Log : Destination("log", "Log", Icons.AutoMirrored.Filled.List)
    data object Vendors : Destination("vendors", "Vendors", Icons.Filled.CheckCircle)
}

private val tabs = listOf(Destination.Ask, Destination.Log, Destination.Vendors)

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_VIAL = "vial/{vialId}"
private const val ROUTE_CALCULATOR = "calculator/{vialId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScaffold() {
    val palette = Theme.palette
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val isTab = tabs.any { it.route == route }
    val title = when (route) {
        Destination.Ask.route -> "Ask"
        Destination.Log.route -> "Log"
        Destination.Vendors.route -> "Vendors"
        ROUTE_VIAL -> "Vial"
        ROUTE_CALCULATOR -> "Reconstitution"
        ROUTE_SETTINGS -> "Settings"
        else -> ""
    }

    Scaffold(
        containerColor = palette.bench,
        topBar = {
            TopAppBar(
                title = { Text(title, style = display(20)) },
                navigationIcon = {
                    if (!isTab) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = palette.ink,
                            )
                        }
                    }
                },
                actions = {
                    if (route == Destination.Ask.route) {
                        IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = palette.ink,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.bench,
                    titleContentColor = palette.ink,
                ),
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = palette.paper) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Destination.Ask.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.amber,
                                selectedTextColor = palette.amber,
                                unselectedIconColor = palette.steel,
                                unselectedTextColor = palette.steel,
                                indicatorColor = palette.hairline,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Ask.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Ask.route) { AskScreen() }

            composable(Destination.Log.route) {
                LogScreen(
                    onOpenVial = { navController.navigate("vial/$it") },
                    // 0 means "not tied to a vial" — a scratch calculation.
                    onOpenCalculator = { navController.navigate("calculator/0") },
                )
            }

            composable(Destination.Vendors.route) { VendorScreen() }

            composable(
                ROUTE_VIAL,
                arguments = listOf(navArgument("vialId") { type = NavType.LongType }),
            ) { entry ->
                val vialId = entry.arguments?.getLong("vialId") ?: return@composable
                VialDetailScreen(
                    vialId = vialId,
                    onOpenCalculator = { navController.navigate("calculator/$it") },
                )
            }

            composable(
                ROUTE_CALCULATOR,
                arguments = listOf(navArgument("vialId") { type = NavType.LongType }),
            ) { entry ->
                val vialId = entry.arguments?.getLong("vialId") ?: 0L

                if (vialId == 0L) {
                    CalculatorScreen()
                } else {
                    val logViewModel: LogViewModel = viewModel(factory = AppViewModelFactory)
                    val vials by logViewModel.vials.collectAsStateWithLifecycle()
                    val vial = vials.firstOrNull { it.vial.id == vialId }
                    CalculatorScreen(
                        initialPeptideMg = vial?.vial?.peptideMg ?: 5.0,
                        initialDiluentMl = vial?.vial?.diluentMl ?: 2.0,
                        initialDoseMcg = vial?.lastDoseMcg ?: 250.0,
                        onCommit = { diluentMl ->
                            logViewModel.setMix(vialId, diluentMl)
                            navController.popBackStack()
                        },
                    )
                }
            }

            composable(ROUTE_SETTINGS) { SettingsScreen() }
        }
    }
}
