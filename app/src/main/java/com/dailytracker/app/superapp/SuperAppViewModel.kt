package com.dailytracker.app.superapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SuperAppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("superapp_prefs", Context.MODE_PRIVATE)

    private val defaultOrder = listOf("kinkeep", "bluetoothtracker", "officetracker")

    val allMiniApps = listOf(
        SuperAppItem(
            id = "kinkeep",
            name = "KinKeep",
            category = "Family & Call Tracker",
            description = "Stay connected with family & friends",
            route = "kinkeep",
            badge = null
        ),
        SuperAppItem(
            id = "bluetoothtracker",
            name = "Device Tracker",
            category = "Hardware & Utilities",
            description = "Monitor nearby Bluetooth devices",
            route = "bluetoothtracker",
            badge = null
        ),
        SuperAppItem(
            id = "officetracker",
            name = "Office Tracker",
            category = "Workplace & Schedule",
            description = "Duty attendance & salary calculator",
            route = "officetracker",
            badge = null
        )
    )

    private val _miniAppOrder = MutableStateFlow(loadOrder())
    val miniAppOrder = _miniAppOrder.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "MONOCHROME") ?: "MONOCHROME")
    val themeMode = _themeMode.asStateFlow()

    private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
    val pendingNavigationRoute = _pendingNavigationRoute.asStateFlow()

    fun navigateToRoute(route: String) {
        _pendingNavigationRoute.value = route
    }

    fun consumePendingNavigationRoute() {
        _pendingNavigationRoute.value = null
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    private fun loadOrder(): List<String> {
        val saved = prefs.getString("mini_app_order", null) ?: return defaultOrder
        val list = saved.split(",").filter { it.isNotBlank() }
        val missing = defaultOrder.filter { !list.contains(it) }
        return list + missing
    }

    private fun saveOrder(order: List<String>) {
        _miniAppOrder.value = order
        prefs.edit().putString("mini_app_order", order.joinToString(",")).apply()
    }

    fun moveUp(appId: String) {
        val current = _miniAppOrder.value.toMutableList()
        val index = current.indexOf(appId)
        if (index > 0) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            saveOrder(current)
        }
    }

    fun moveDown(appId: String) {
        val current = _miniAppOrder.value.toMutableList()
        val index = current.indexOf(appId)
        if (index >= 0 && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            saveOrder(current)
        }
    }

    fun resetOrder() {
        saveOrder(defaultOrder)
    }
}
