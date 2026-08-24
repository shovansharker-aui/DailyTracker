package com.dailytracker.app.miniapps.kinkeep

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailytracker.app.ui.KinKeepApp
import com.dailytracker.app.ui.KinKeepViewModel

@Composable
fun KinKeepMiniApp(
    viewModel: KinKeepViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    KinKeepApp(
        viewModel = viewModel,
        onNavigateToHome = onNavigateToHome,
        modifier = modifier
    )
}
