package com.dailytracker.app.superapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dailytracker.app.miniapps.bluetoothtracker.BluetoothTrackerScreen
import com.dailytracker.app.miniapps.kinkeep.KinKeepMiniApp
import com.dailytracker.app.miniapps.officetracker.OfficeTrackerScreen
import com.dailytracker.app.ui.KinKeepViewModel

@Composable
fun SuperAppNavHost(
    superAppViewModel: SuperAppViewModel,
    kinKeepViewModel: KinKeepViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val pendingRoute by superAppViewModel.pendingNavigationRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) {
                popUpTo("dashboard")
            }
            superAppViewModel.consumePendingNavigationRoute()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier
    ) {
        composable("dashboard") {
            SuperAppDashboard(
                viewModel = superAppViewModel,
                onMiniAppClick = { item ->
                    navController.navigate(item.route)
                },
                onSettingsClick = {
                    navController.navigate("superapp_settings")
                }
            )
        }

        composable("kinkeep") {
            KinKeepMiniApp(
                viewModel = kinKeepViewModel,
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("bluetoothtracker") {
            BluetoothTrackerScreen(
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("officetracker") {
            OfficeTrackerScreen(
                onNavigateToHome = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("superapp_settings") {
            SuperAppSettingsScreen(
                viewModel = superAppViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
