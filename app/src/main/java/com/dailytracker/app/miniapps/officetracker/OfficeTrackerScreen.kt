package com.dailytracker.app.miniapps.officetracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OfficeTrackerScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    OfficeTrackerNavHost(
        onNavigateToHome = onNavigateToHome,
        modifier = modifier
    )
}
