package com.ifmedtech.usb_device

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class UsbSerialManager(private val context: Context) {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var ioManager: SerialInputOutputManager? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    var onDataReceived: ((String) -> Unit)? = null

    fun connectToUsb(): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            println("No USB devices found.")
            return false
        }

        val driver: UsbSerialDriver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device) ?: return false

        serialPort = driver.ports[0] // Assuming a single port device
        serialPort?.apply {
            open(connection)
            setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        }

        startListening()
        return true
    }

    private fun startListening() {
        serialPort?.let { port ->
            ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    val received = String(data)
                    onDataReceived?.invoke(received)
                }

                override fun onRunError(e: Exception) {
                    e.printStackTrace()
                }
            })
            executor.submit(ioManager)
        }
    }

    fun sendData(data: String) {
        coroutineScope.launch {
            try {
                serialPort?.write(data.toByteArray(), 1000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        ioManager?.stop()
        serialPort?.close()
        coroutineScope.cancel()
    }
}
