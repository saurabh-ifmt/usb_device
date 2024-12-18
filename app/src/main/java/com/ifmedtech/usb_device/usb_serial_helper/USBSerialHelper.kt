package com.ifmedtech.usb_device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import java.io.IOException

class USBSerialHelper(private val context: Context) {

    private val ACTION_USB_PERMISSION = "com.ifmedtech.usb_device.USB_PERMISSION"
    private var currentPort: UsbSerialPort? = null
    private var readJob: Job? = null

    /**
     * Get list of connected USB serial devices.
     */
    fun getConnectedDevices(): List<UsbSerialDriver> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
    }

    /**
     * Connect to a specific USB device and handle data through callbacks.
     */
    suspend fun connect(
        driver: UsbSerialDriver,
        onDataReceived: (buffer: ByteArray, length: Int) -> Unit,
        onError: (message: String) -> Unit
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
                onError("Unable to open USB connection.")
                return@withContext false
            }

            currentPort = driver.ports[0]
            try {
                currentPort?.open(connection)
                currentPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                readJob = CoroutineScope(Dispatchers.IO).launch {
                    val buffer = ByteArray(1024)
                    while (isActive) {
                        try {
                            val len = currentPort?.read(buffer, 1000) ?: break
                            if (len > 0) {
                                onDataReceived(buffer, len)
                            }
                        } catch (e: IOException) {
                            onError("Disconnected: ${e.message}")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                onError("Error: ${e.message}")
                currentPort?.close()
                return@withContext false
            }
            true
        }
    }

    /**
     * Disconnect and stop reading.
     */
    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try {
            currentPort?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentPort = null
        }
    }

}
