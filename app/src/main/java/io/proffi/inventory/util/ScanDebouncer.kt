package io.proffi.inventory.util

/**
 * Suppresses duplicate barcode reads caused by hardware scanners that emit the
 * same code twice on a single trigger pull. A repeat of the *same* barcode
 * within [windowMs] is treated as a duplicate and ignored.
 *
 * The window is short enough that deliberate re-scans of the same item (to add
 * quantity) still pass, since a human cannot re-trigger the gun that fast.
 */
class ScanDebouncer(private val windowMs: Long = 400L) {

    private var lastBarcode: String? = null
    private var lastTimeMs: Long = 0L

    /** Returns true if [barcode] should be ignored as a duplicate fire. */
    fun isDuplicate(barcode: String): Boolean {
        val now = System.currentTimeMillis()
        val duplicate = barcode == lastBarcode && (now - lastTimeMs) < windowMs
        if (!duplicate) {
            lastBarcode = barcode
            lastTimeMs = now
        }
        return duplicate
    }

    fun reset() {
        lastBarcode = null
        lastTimeMs = 0L
    }
}
