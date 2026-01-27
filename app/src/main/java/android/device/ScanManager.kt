package android.device

/**
 * Mock класс для ScanManager от Urovo.
 * Этот класс позволяет приложению компилироваться без реального SDK.
 *
 * Когда установлен настоящий SDK из app/libs/, этот файл будет игнорирован.
 *
 * ВАЖНО: Этот класс НЕ функционален и используется только для компиляции!
 */
@Suppress("unused", "UNUSED_PARAMETER")
class ScanManager {

    fun openScanner(): Boolean {
        return false
    }

    fun closeScanner(): Boolean {
        return false
    }

    fun switchOutputMode(mode: Int): Boolean {
        return false
    }

    fun getScannerState(): Int {
        return 0
    }

    fun lockTrigger(): Boolean {
        return false
    }

    fun unlockTrigger(): Boolean {
        return false
    }

    companion object {
        const val MODE_BROADCAST = 0
        const val MODE_DIRECT = 1
    }
}
