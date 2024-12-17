package com.ifmedtech.usb_device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
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
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val ACTION_USB_PERMISSION = "com.ifmedtech.usb_device.USB_PERMISSION"
    private var currentPort: UsbSerialPort? = null
    private var readJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            USBSerialReaderApp()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun USBSerialReaderApp() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val jsonData = remember { mutableStateListOf<String>() }
        val scope = rememberCoroutineScope()
        var isConnected by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Arduino Serial Reader") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Row {
                    // Start Connection Button
                    Button(
                        onClick = {
                            scope.launch {
                                isConnected = connectAndReadData(usbManager, jsonData)
                            }
                        },
                        enabled = !isConnected
                    ) {
                        Text("Connect")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Disconnect Button
                    Button(
                        onClick = {
                            disconnect(jsonData)
                            isConnected = false
                        },
                        enabled = isConnected
                    ) {
                        Text("Disconnect")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Incoming Data:", style = MaterialTheme.typography.headlineSmall)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(jsonData) { data ->
                        Text(
                            text = data,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 10.dp).width(100.dp)
                        )
                    }
                }
            }
        }
    }

    private suspend fun connectAndReadData(
        usbManager: UsbManager,
        jsonData: MutableList<String>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) {
                jsonData.add("No USB devices detected.")
                return@withContext false
            }

            val driver: UsbSerialDriver = availableDrivers[0]
            val permissionIntent = PendingIntent.getBroadcast(
                this@MainActivity, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )

            if (!usbManager.hasPermission(driver.device)) {
                usbManager.requestPermission(driver.device, permissionIntent)
                return@withContext false
            }

            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                jsonData.add("Unable to open USB connection.")
                return@withContext false
            }

            currentPort = driver.ports[0]
            try {
                currentPort?.open(connection)
                currentPort?.setParameters(
                    115200,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )

                val buffer = ByteArray(1024)
                jsonData.add("Connected. Reading data...")

                readJob = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        try {
                            val len = currentPort?.read(buffer, 1000) ?: break
                            if (len > 0) {
                                val data = String(buffer, 0, len, StandardCharsets.UTF_8).trim()
                                if (data.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        jsonData.add(data)
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            withContext(Dispatchers.Main) {
                                jsonData.add("Disconnected: ${e.message}")
                            }
                            break
                        }
                    }

                }
            } catch (e: Exception) {
                jsonData.add("Error: ${e.message}")
                currentPort?.close()
                return@withContext false
            }
            true
        }
    }

    private fun disconnect(jsonData: MutableList<String>) {
        readJob?.cancel()
        readJob = null
        try {
            currentPort?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentPort = null
        }
        jsonData.clear()
        jsonData.add("Disconnected.")
    }

}
