package com.dailytracker.app.miniapps.officetracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun OfficeTrackerNavHost(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfficeTrackerViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "office_dashboard",
        modifier = modifier
    ) {
        composable("office_dashboard") {
            OfficeTrackerDashboard(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate("holiday_settings")
                },
                onNavigateToHome = onNavigateToHome
            )
        }

        composable("holiday_settings") {
            GovtHolidaySettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("office_list") {
            OfficeDetailListView(
                viewModel = viewModel,
                onBackToCalendar = {
                    navController.popBackStack()
                },
                onNavigateToHome = onNavigateToHome
            )
        }
    }
}
