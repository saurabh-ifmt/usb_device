package com.ifmedtech.usb_device

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var usbHelper: USBSerialHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbHelper = USBSerialHelper(this)

        setContent {
            USBSerialReaderApp()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun USBSerialReaderApp() {
        val jsonData = remember { mutableStateListOf<String>() }
        val scope = rememberCoroutineScope()

        var isConnected by remember { mutableStateOf(false) }
        var selectedDriver by remember { mutableStateOf<UsbSerialDriver?>(null) }
        var drivers by remember { mutableStateOf(usbHelper.getConnectedDevices()) }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("USB Serial Reader") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Refresh Device List Button
                Button(onClick = { drivers = usbHelper.getConnectedDevices() }) {
                    Text("Refresh Devices")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dropdown for Device Selection
                Text("Select Device:")
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }) {
                        Text(selectedDriver?.device?.deviceName ?: "Select USB Device")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (drivers.isEmpty()) {
                            DropdownMenuItem(text = { Text("No devices found") }, onClick = {})
                        } else {
                            drivers.forEach { driver ->
                                DropdownMenuItem(
                                    text = { Text(driver.device.deviceName) },
                                    onClick = {
                                        selectedDriver = driver
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connect and Disconnect Buttons
                Row {
                    Button(
                        onClick = {
                            selectedDriver?.let { driver ->
                                scope.launch {
                                    isConnected = usbHelper.connectAndReadData(driver, jsonData)
                                }
                            }
                        },
                        enabled = !isConnected && selectedDriver != null
                    ) {
                        Text("Connect")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            usbHelper.disconnect(jsonData)
                            isConnected = false
                        },
                        enabled = isConnected
                    ) {
                        Text("Disconnect")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Data Display
                Text("Incoming Data:", style = MaterialTheme.typography.headlineSmall)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(jsonData) { data ->
                        Text(
                            text = data,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
