package com.ifmedtech.usb_device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.charset.StandardCharsets

class USBSerialHelper(private val context: Context) {

    private val ACTION_USB_PERMISSION = "com.ifmedtech.usb_device.USB_PERMISSION"
    private var currentPort: UsbSerialPort? = null
    private var readJob: Job? = null

    /**
     * Get list of connected USB serial devices
     */
    fun getConnectedDevices(): List<UsbSerialDriver> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
    }

    /**
     * Connect to a specific device and read incoming data
     */
    suspend fun connectAndReadData(
        driver: UsbSerialDriver,
        jsonData: MutableList<String>
    ): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return withContext(Dispatchers.IO) {
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
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
                currentPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                val buffer = ByteArray(1024)
                jsonData.add("Connected to ${driver.device.deviceName}. Reading data...")

                readJob = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        try {
                            val len = currentPort?.read(buffer, 1) ?: break
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

    /**
     * Disconnect and stop reading
     */
    fun disconnect(jsonData: MutableList<String>) {
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
