package com.dailytracker.app.miniapps.businesscard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun BusinessCardNavHost(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BusinessCardViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "card_list",
        modifier = modifier
    ) {
        composable("card_list") {
            BusinessCardListScreen(
                viewModel = viewModel,
                onAddCard = {
                    viewModel.startNewCapture()
                    navController.navigate("card_capture")
                },
                onOpenCard = { id -> navController.navigate("card_detail/$id") },
                onOpenSettings = { navController.navigate("card_settings") },
                onNavigateToHome = onNavigateToHome
            )
        }

        composable("card_capture") {
            BusinessCardCaptureScreen(
                viewModel = viewModel,
                onExtracted = { navController.navigate("card_review") },
                onCancel = { navController.popBackStack() }
            )
        }

        composable("card_review") {
            BusinessCardReviewScreen(
                viewModel = viewModel,
                onSaved = { navController.popBackStack("card_list", inclusive = false) },
                onCancel = { navController.popBackStack() }
            )
        }

        composable("card_detail/{cardId}") { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId")?.toLongOrNull() ?: -1L
            BusinessCardDetailScreen(
                viewModel = viewModel,
                cardId = cardId,
                onEdit = {
                    viewModel.loadCardIntoDraft(cardId)
                    navController.navigate("card_review")
                },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack("card_list", inclusive = false) }
            )
        }

        composable("card_settings") {
            BusinessCardSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
