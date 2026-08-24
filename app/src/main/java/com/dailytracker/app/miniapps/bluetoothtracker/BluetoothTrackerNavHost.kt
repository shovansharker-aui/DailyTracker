package com.dailytracker.app.miniapps.bluetoothtracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun BluetoothTrackerNavHost(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BluetoothTrackerViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "bt_dashboard",
        modifier = modifier
    ) {
        composable("bt_dashboard") {
            BluetoothTrackerDashboard(
                viewModel = viewModel,
                onNavigateToHome = onNavigateToHome,
                onNavigateToSettings = {
                    navController.navigate("bt_settings")
                },
                onDeviceClick = { address ->
                    navController.navigate("bt_tracking/$address")
                }
            )
        }

        composable("bt_settings") {
            BluetoothTrackerSettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "bt_tracking/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address") ?: ""
            LaunchedEffect(address) {
                viewModel.selectDeviceByAddress(address)
            }

            val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

            selectedDevice?.let { dev ->
                DeviceTrackingScreen(
                    device = dev,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToHome = onNavigateToHome
                )
            }
        }
    }
}
