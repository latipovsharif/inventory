package io.proffi.inventory.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.device.ScanManager
import android.util.Log

class UrovoScanner(
    private val context: Context,
    private val callback: ScannerCallback
) : BarcodeScanner {

    private var scanManager: ScanManager? = null
    private var isReceiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val barcode = intent?.getByteArrayExtra("barocode")
            val barcodeLen = intent?.getIntExtra("length", 0) ?: 0
            val temp = intent?.getByteArrayExtra("barcodeStr")
            val scanResult = intent?.getStringExtra("data")

            try {
                val barcodeStr = when {
                    !scanResult.isNullOrEmpty() -> scanResult
                    temp != null && temp.isNotEmpty() -> String(temp)
                    barcode != null && barcodeLen > 0 -> String(barcode, 0, barcodeLen)
                    else -> null
                }

                if (!barcodeStr.isNullOrEmpty()) {
                    callback.onScanResult(barcodeStr.trim())
                } else {
                    callback.onScanError("Пустой штрих-код")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing scan result", e)
                callback.onScanError("Ошибка обработки: ${e.message}")
            }
        }
    }

    override fun startScan() {
        try {
            if (scanManager == null) {
                scanManager = ScanManager()
            }

            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(SCAN_ACTION)
                    priority = IntentFilter.SYSTEM_HIGH_PRIORITY
                }
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                isReceiverRegistered = true
            }

            scanManager?.openScanner()
            scanManager?.switchOutputMode(0) // 0 = broadcast mode
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Urovo scanner", e)
            callback.onScanError("Ошибка запуска сканера: ${e.message}")
        }
    }

    override fun stopScan() {
        try {
            scanManager?.closeScanner()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Urovo scanner", e)
        }
    }

    override fun release() {
        try {
            stopScan()

            if (isReceiverRegistered) {
                context.unregisterReceiver(scanReceiver)
                isReceiverRegistered = false
            }

            scanManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Urovo scanner", e)
        }
    }

    companion object {
        private const val TAG = "UrovoScanner"
        private const val SCAN_ACTION = "android.intent.ACTION_DECODE_DATA"
    }
}
