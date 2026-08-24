package com.dailytracker.app.miniapps.bluetoothtracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BluetoothTrackerScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BluetoothTrackerNavHost(
        onNavigateToHome = onNavigateToHome,
        modifier = modifier
    )
}
