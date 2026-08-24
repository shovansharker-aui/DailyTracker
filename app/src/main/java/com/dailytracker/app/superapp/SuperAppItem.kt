package com.dailytracker.app.superapp

data class SuperAppItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val route: String,
    val badge: String? = null
)
