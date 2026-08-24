package com.dailytracker.app.miniapps.bluetoothtracker

import kotlin.math.pow

enum class BluetoothType {
    BLE,
    CLASSIC,
    UNKNOWN
}

data class TrackedBluetoothDevice(
    val address: String,
    val name: String,
    val rssi: Int = -100,
    val type: BluetoothType = BluetoothType.BLE,
    val isBonded: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isClassicRssiLimited: Boolean = false
) {
    val displayName: String
        get() = name.ifBlank { "Unknown Device (${address.takeLast(5)})" }

    /**
     * Calculates approximate relative distance using the Log-Distance Path Loss Model:
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     * TxPower is assumed ~ -59 dBm at 1 meter, path loss exponent n ~ 2.2
     */
    val approximateDistanceMeters: Double
        get() {
            if (rssi == 0 || rssi <= -100) return 15.0
            val txPower = -59
            val pathLossExponent = 2.2
            val ratio = (txPower - rssi) / (10.0 * pathLossExponent)
            return 10.0.pow(ratio).coerceIn(0.1, 30.0)
        }

    val proximityCategory: String
        get() = when {
            isClassicRssiLimited -> "Classic BT (Fixed Signal)"
            rssi > -60 -> "Immediate (< 1m)"
            rssi > -75 -> "Near (1 - 3m)"
            rssi > -88 -> "Far (3 - 10m)"
            else -> "Weak Signal (> 10m)"
        }

    val signalPercentage: Int
        get() {
            if (rssi <= -100) return 0
            if (rssi >= -50) return 100
            return ((rssi + 100) * 2).coerceIn(0, 100)
        }
}
