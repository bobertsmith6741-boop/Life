package com.mockpilot.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mockpilot.ui.map.MapScreen
import com.mockpilot.ui.places.PlacesScreen
import com.mockpilot.ui.routes.RouteBuilderScreen
import com.mockpilot.ui.schedule.ScheduleScreen
import com.mockpilot.ui.selftest.SelfTestScreen
import com.mockpilot.ui.theme.MockPilotTheme
import com.mockpilot.ui.wizard.SetupWizardScreen
import dagger.hilt.android.AndroidEntryPoint

object Dest {
    const val MAP = "map"
    const val PLACES = "places"
    const val ROUTES = "routes"
    const val SCHEDULE = "schedule"
    const val SELFTEST = "selftest"
    const val WIZARD = "wizard"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Dest.MAP, "Map", Icons.Filled.Map),
    Tab(Dest.PLACES, "Places", Icons.Filled.Place),
    Tab(Dest.ROUTES, "Routes", Icons.Filled.Route),
    Tab(Dest.SCHEDULE, "Schedule", Icons.Filled.Schedule),
    Tab(Dest.SELFTEST, "Verify", Icons.Filled.VerifiedUser),
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MockPilotTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()

    // Request the runtime permissions we need up front.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* results surfaced live by the Setup wizard */ }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Dest.WIZARD) {
                NavigationBar {
                    val currentDest = backStackEntry?.destination
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDest?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.MAP,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.MAP) {
                MapScreen(onOpenSetup = { navController.navigate(Dest.WIZARD) })
            }
            composable(Dest.PLACES) { PlacesScreen() }
            composable(Dest.ROUTES) { RouteBuilderScreen() }
            composable(Dest.SCHEDULE) { ScheduleScreen() }
            composable(Dest.SELFTEST) { SelfTestScreen() }
            composable(Dest.WIZARD) {
                SetupWizardScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
