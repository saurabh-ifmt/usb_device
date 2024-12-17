package com.ifmedtech.usb_device

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun UsbSerialScreen(context: Context, usbManager: UsbSerialManager) {
    var connected by remember { mutableStateOf(false) }
    var receivedData by remember { mutableStateOf("") }
    var sendText by remember { mutableStateOf("") }

    // Handle data received from USB
    LaunchedEffect(Unit) {
        usbManager.onDataReceived = { data ->
            receivedData = data
        }
    }

    Column {
        // Button to connect to USB
        Button(onClick = {
            connected = usbManager.connectToUsb()
        }) {
            Text(if (connected) "Connected" else "Connect to USB")
        }

        // Display received data
        Text("Received Data: $receivedData")

        // TextField to input data to send
        TextField(
            value = sendText,
            onValueChange = { sendText = it },
            label = { Text("Data to Send") }
        )

        // Button to send data
        Button(onClick = {
            if (connected) usbManager.sendData(sendText)
        }) {
            Text("Send Data")
        }
    }
}
