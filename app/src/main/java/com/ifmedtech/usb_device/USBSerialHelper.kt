package com.ifmedtech.usb_device

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*

@RequiresApi(Build.VERSION_CODES.O)
class USBSerialHelper(private val context: Context, private val deviceListener: OnDeviceListener) {

    private var currentPort: UsbSerialPort? = null
    private var readJob: Job? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionHandler = USBPermissionHandler(context, deviceListener)

    init {
        permissionHandler.registerReceiver()
    }

    fun getConnectedDevices(): List<UsbSerialDriver> {
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
    }

    fun readData(driver: UsbSerialDriver, jsonData: MutableList<String>) {
        // Try to find the driver that matches the given device
        val foundDriver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull { it.device == driver.device }

        if (foundDriver != null) {
            // Open the connection to the device
            val connection = usbManager.openDevice(foundDriver.device)
            if (connection == null) {
                // If connection fails, notify permission denial
                deviceListener.onDevicePermissionDenied(foundDriver.device)
                return
            }

            // Initialize the serial port and try to open it
            currentPort = foundDriver.ports[0]
            try {
                // Set serial parameters: baud rate, data bits, stop bits, parity
                currentPort?.open(connection)
                currentPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                // Notify that the device is ready and permission is granted
                deviceListener.onPermissionGranted(foundDriver.device)
            } catch (e: Exception) {
                // If an error occurs, notify permission denial and close the port
                deviceListener.onDevicePermissionDenied(foundDriver.device)
                currentPort?.close()
                return
            }

            // Read data from the serial port in a background coroutine
            val buffer = ByteArray(1024)  // Buffer to store incoming data

            // Launch a coroutine to continuously read data from the device
            readJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        // Try to read data from the serial port
                        val len = currentPort?.read(buffer, 0, buffer.size) ?: break
                        if (len > 0) {
                            val data = String(buffer, 0, len).trim()
                            if (data.isNotEmpty()) {
                                // If data is read, update the UI on the main thread
                                withContext(Dispatchers.Main) {
                                    jsonData.add(data)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // If an error occurs while reading, notify and stop reading
                        withContext(Dispatchers.Main) {
                            jsonData.add("Disconnected: ${e.message}")
                        }
                        break
                    }
                }
            }
        } else {
            // If no matching driver is found, notify permission denial
            deviceListener.onDevicePermissionDenied(driver.device)
        }
    }

    fun clearData(jsonData: MutableList<String>) {
        jsonData.clear()
    }

    fun disconnectDevice() {
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

    fun unregisterReceiver() {
        permissionHandler.unregisterReceiver()
    }

    interface OnDeviceListener {
        fun onDeviceAttached(device: UsbDevice)
        fun onDeviceDetached(device: UsbDevice)
        fun onDevicePermissionDenied(device: UsbDevice)
        fun onPermissionGranted(device: UsbDevice)
    }
}
