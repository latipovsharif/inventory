package io.proffi.inventory.scanner

import android.app.Activity
import android.content.Context
import com.google.zxing.integration.android.IntentIntegrator

class CameraScanner(
    private val context: Context,
    private val callback: ScannerCallback
) : BarcodeScanner {

    private var integrator: IntentIntegrator? = null

    override fun startScan() {
        if (context is Activity) {
            integrator = IntentIntegrator(context).apply {
                setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
                setPrompt("Наведите камеру на штрих-код")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            }
            integrator?.initiateScan()
        }
    }

    override fun stopScan() {
        // Camera scanner doesn't need explicit stop
    }

    override fun release() {
        integrator = null
    }

    companion object {
        fun parseResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): String? {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            return result?.contents
        }
    }
}
