package com.dailytracker.app.miniapps.businesscard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BusinessCardScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BusinessCardNavHost(
        onNavigateToHome = onNavigateToHome,
        modifier = modifier
    )
}
