package com.dailytracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailytracker.app.superapp.SuperAppNavHost
import com.dailytracker.app.superapp.SuperAppViewModel
import com.dailytracker.app.ui.KinKeepViewModel
import com.dailytracker.app.ui.theme.DailyTrackerTheme

class MainActivity : ComponentActivity() {
    private val kinKeepViewModel: KinKeepViewModel by viewModels()
    private val superAppViewModel: SuperAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val themeMode by superAppViewModel.themeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            DailyTrackerTheme(themeMode = themeMode, darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SuperAppNavHost(
                        superAppViewModel = superAppViewModel,
                        kinKeepViewModel = kinKeepViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val contactId = intent?.getLongExtra("contact_id", -1L) ?: -1L
        if (contactId > 0L) {
            kinKeepViewModel.openContactDetailsById(contactId)
            superAppViewModel.navigateToRoute("kinkeep")
        }
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (!navigateTo.isNullOrBlank()) {
            superAppViewModel.navigateToRoute(navigateTo)
        }
    }
}
