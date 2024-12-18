package com.ifmedtech.usb_device

import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.launch
import androidx.compose.material3.Button

class MainActivity : ComponentActivity() {
    private lateinit var usbHelper: USBSerialHelper
    // Variable to track connection status
    var isConnected by mutableStateOf(false) // Track the connection state

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize usbHelper and pass the callback to update isConnected
        usbHelper = USBSerialHelper(this, object : USBSerialHelper.OnDeviceListener {
            override fun onDeviceAttached(device: UsbDevice) {
                // Device attached, update isConnected state
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Device attached: ${device.deviceName}", Toast.LENGTH_SHORT).show()
                    // Update isConnected to true in the composable
                    isConnected = true
                }
            }

            override fun onDeviceDetached(device: UsbDevice) {
                // Device detached, update isConnected state
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Device detached: ${device.deviceName}", Toast.LENGTH_SHORT).show()
                    // Update isConnected to false in the composable
                    isConnected = false
                }
            }

            override fun onDevicePermissionDenied(device: UsbDevice) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Permission denied for device: ${device.deviceName}", Toast.LENGTH_SHORT).show()
                    // Update isConnected to false if permission is denied
                    isConnected = false
                }
            }

            override fun onPermissionGranted(device: UsbDevice) {
                runOnUiThread {
                    // Update isConnected to true when permission is granted
                    isConnected = true
                    Toast.makeText(this@MainActivity, "Permission Already Granted", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // Set the content of the activity and pass usbHelper to the composable
        setContent {
            USBSerialReaderApp(usbHelper = usbHelper, isConnected) // Pass usbHelper here
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun USBSerialReaderApp(usbHelper: USBSerialHelper, isConnected: Boolean) {
    val jsonData = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

//    var isConnected by remember { mutableStateOf(initialIsConnected) }
    var selectedDriver by remember { mutableStateOf<UsbSerialDriver?>(null) }
    var drivers by remember { mutableStateOf(usbHelper.getConnectedDevices()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("USB Serial Reader") })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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

                // Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Read Button
                    Button(
                        onClick = {
                            if (isConnected && selectedDriver != null) {
                                scope.launch {
                                    // Use selectedDriver here instead of 'driver'
                                    usbHelper.readData(selectedDriver!!, jsonData)
                                    jsonData.add("Reading data from the device...")
                                }
                            } else {
                                jsonData.add("Device is not connected or no device selected.")
                            }
                        },
                        enabled = isConnected && selectedDriver != null,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Text("Read")
                    }

                    // Clear Data Button
                    Button(
                        onClick = { usbHelper.clearData(jsonData) },
                        enabled = jsonData.isNotEmpty(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text("Clear")
                    }

                    // Disconnect Button
                    Button(
                        onClick = {
                            usbHelper.disconnectDevice()
//                            isConnected = false
                            jsonData.add("Device disconnected.")
                        },
                        enabled = isConnected,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Text("Disconnect")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Data Display
                Text("Incoming Data:", style = MaterialTheme.typography.headlineSmall)

                LazyColumn(modifier = Modifier.fillMaxHeight()) {
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
    )
}

