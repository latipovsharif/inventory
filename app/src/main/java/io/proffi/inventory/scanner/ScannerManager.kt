package io.proffi.inventory.scanner

import android.content.Context

interface BarcodeScanner {
    fun startScan()
    fun stopScan()
    fun release()
}

interface ScannerCallback {
    fun onScanResult(barcode: String)
    fun onScanError(error: String)
}

class ScannerManager(
    private val context: Context,
    private val callback: ScannerCallback
) {
    private var currentScanner: BarcodeScanner? = null

    fun initScanner(type: ScannerType): BarcodeScanner {
        currentScanner?.release()

        currentScanner = when (type) {
            ScannerType.CAMERA -> CameraScanner(context, callback)
            ScannerType.UROVO_I6310 -> UrovoScanner(context, callback)
        }

        return currentScanner!!
    }

    fun getCurrentScanner(): BarcodeScanner? = currentScanner

    fun release() {
        currentScanner?.release()
        currentScanner = null
    }
}
