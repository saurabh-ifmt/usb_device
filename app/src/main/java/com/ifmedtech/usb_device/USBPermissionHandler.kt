package com.ifmedtech.usb_device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.app.PendingIntent
import androidx.annotation.RequiresApi

class USBPermissionHandler(
    private val context: Context,
    private val deviceListener: OnDeviceListener
) {
    private val ACTION_USB_PERMISSION = "com.ifmedtech.usb_device.USB_PERMISSION"
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB Broadcast Receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let { onDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let { deviceListener.onDeviceDetached(it) }
                }
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (device != null) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            deviceListener.onPermissionGranted(device)
                        } else {
                            deviceListener.onDevicePermissionDenied(device)
                        }
                    }
                }
            }
        }
    }

    /**
     * Register the USB receiver for listening to USB device events.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun registerReceiver() {
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Unregister the USB receiver to prevent memory leaks.
     */
    fun unregisterReceiver() {
        context.unregisterReceiver(usbReceiver)
    }

    /**
     * Called when a USB device is attached. Requests permission if not already granted.
     */
    private fun onDeviceAttached(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
        )
        if (!usbManager.hasPermission(device)) {
            usbManager.requestPermission(device, permissionIntent)
        }
        deviceListener.onDeviceAttached(device)
    }

    /**
     * Listener interface for USB device events.
     */
    interface OnDeviceListener {
        fun onDeviceAttached(device: UsbDevice)
        fun onDeviceDetached(device: UsbDevice)
        fun onPermissionGranted(device: UsbDevice)
        fun onDevicePermissionDenied(device: UsbDevice)
    }
}

