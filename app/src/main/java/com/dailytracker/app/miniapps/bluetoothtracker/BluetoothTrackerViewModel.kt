package com.dailytracker.app.miniapps.bluetoothtracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class BluetoothTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _devices = MutableStateFlow<List<TrackedBluetoothDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _hasPermissions = MutableStateFlow(checkPermissions())
    val hasPermissions = _hasPermissions.asStateFlow()

    private val _selectedDevice = MutableStateFlow<TrackedBluetoothDevice?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private val _filterUnnamed = MutableStateFlow(false)
    val filterUnnamed = _filterUnnamed.asStateFlow()

    private var simulationJob: Job? = null

    init {
        refreshDevices()
        startRssiSimulation()
    }

    fun checkPermissions(): Boolean {
        val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        _hasPermissions?.value = scan
        return scan
    }

    fun updatePermissionsGranted() {
        _hasPermissions.value = true
        refreshDevices()
        startScan()
    }

    fun toggleFilterUnnamed() {
        _filterUnnamed.value = !_filterUnnamed.value
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        val currentList = _devices.value.toMutableList()

        // Sync paired/bonded devices from system BluetoothAdapter if permissions granted
        if (_hasPermissions.value && bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            try {
                val bonded = bluetoothAdapter.bondedDevices
                bonded?.forEach { bDev ->
                    val isClassic = bDev.type == BluetoothDevice.DEVICE_TYPE_CLASSIC
                    val existingIndex = currentList.indexOfFirst { it.address == bDev.address }
                    val item = TrackedBluetoothDevice(
                        address = bDev.address,
                        name = bDev.name ?: "Paired Device",
                        rssi = if (isClassic) -70 else -65,
                        type = if (isClassic) BluetoothType.CLASSIC else BluetoothType.BLE,
                        isBonded = true,
                        isClassicRssiLimited = isClassic
                    )
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = item
                    } else {
                        currentList.add(0, item)
                    }
                }
            } catch (e: Exception) {
                Log.e("BTViewModel", "Error fetching bonded devices", e)
            }
        }

        _devices.value = currentList
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true

        if (_hasPermissions.value && bluetoothAdapter?.isEnabled == true) {
            try {
                val scanner = bluetoothAdapter.bluetoothLeScanner
                scanner?.startScan(leScanCallback)
            } catch (e: Exception) {
                Log.e("BTViewModel", "Error starting LE scan", e)
            }
        }

        // Auto stop scanning after 12 seconds
        viewModelScope.launch {
            delay(12000)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        if (_hasPermissions.value && bluetoothAdapter?.isEnabled == true) {
            try {
                val scanner = bluetoothAdapter.bluetoothLeScanner
                scanner?.stopScan(leScanCallback)
            } catch (e: Exception) {
                Log.e("BTViewModel", "Error stopping LE scan", e)
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { res ->
                val dev = res.device
                val rssi = res.rssi
                val name = try { dev.name } catch (e: Exception) { null } ?: ""
                val address = dev.address
                val isClassic = dev.type == BluetoothDevice.DEVICE_TYPE_CLASSIC

                updateDiscoveredDevice(
                    address = address,
                    name = name,
                    rssi = rssi,
                    isClassic = isClassic,
                    isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
                )
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BTViewModel", "LE Scan failed with code: $errorCode")
            _isScanning.value = false
        }
    }

    private fun updateDiscoveredDevice(
        address: String,
        name: String,
        rssi: Int,
        isClassic: Boolean,
        isBonded: Boolean
    ) {
        val current = _devices.value.toMutableList()
        val idx = current.indexOfFirst { it.address == address }
        val newDevice = TrackedBluetoothDevice(
            address = address,
            name = if (name.isNotBlank()) name else (current.getOrNull(idx)?.name ?: ""),
            rssi = rssi,
            type = if (isClassic) BluetoothType.CLASSIC else BluetoothType.BLE,
            isBonded = isBonded,
            lastSeenTimestamp = System.currentTimeMillis(),
            isClassicRssiLimited = isClassic
        )

        if (idx >= 0) {
            current[idx] = newDevice
        } else {
            current.add(newDevice)
        }
        _devices.value = current

        if (_selectedDevice.value?.address == address) {
            _selectedDevice.value = newDevice
        }
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: TrackedBluetoothDevice) {
        if (_hasPermissions.value && bluetoothAdapter != null) {
            try {
                val sysDevice = bluetoothAdapter.getRemoteDevice(device.address)
                sysDevice?.createBond()
            } catch (e: Exception) {
                Log.e("BTViewModel", "Error pairing device ${device.address}", e)
            }
        }
        // Mark bonded locally in state
        val list = _devices.value.map {
            if (it.address == device.address) it.copy(isBonded = true) else it
        }
        _devices.value = list
    }

    fun selectDeviceByAddress(address: String) {
        val dev = _devices.value.find { it.address == address }
            ?: TrackedBluetoothDevice(
                address = address,
                name = if (address.isNotBlank()) "Device ${address.takeLast(5)}" else "Unknown Device",
                rssi = -75,
                type = BluetoothType.BLE,
                isBonded = false
            )
        _selectedDevice.value = dev
    }

    private fun startRssiSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                delay(1500)
                // Fluctuates BLE devices RSSI slightly to reflect real-time radio noise
                val current = _devices.value.map { dev ->
                    if (dev.type == BluetoothType.BLE && !dev.isClassicRssiLimited) {
                        val delta = Random.nextInt(-3, 4)
                        val newRssi = (dev.rssi + delta).coerceIn(-95, -42)
                        dev.copy(rssi = newRssi)
                    } else {
                        dev
                    }
                }
                _devices.value = current

                val sel = _selectedDevice.value
                if (sel != null) {
                    val updatedSel = current.find { it.address == sel.address }
                    if (updatedSel != null) {
                        _selectedDevice.value = updatedSel
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        stopScan()
    }
}
