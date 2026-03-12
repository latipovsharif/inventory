package io.proffi.inventory.scanner

import io.proffi.inventory.util.AppConfig

/**
 * Конфигурация SDK для различных устройств сканирования
 */
data class SdkConfig(
    val scannerType: ScannerType,
    val libraryFileName: String,
    val downloadUrl: String,
    val version: String,
)

object SdkLibraryConfig {

    private const val BASE_SDK_URL = "${AppConfig.BASE_URL}/downloads/scanner/sdk"


    /**
     * Конфигурации для всех поддерживаемых устройств
     */
    val AVAILABLE_SCANNERS = listOf(
        SdkConfig(
            scannerType = ScannerType.CAMERA,
            libraryFileName = "camera_scanner_builtin",
            downloadUrl = "", // Встроенный, не требует загрузки
            version = "1.0.0"
        ),
        SdkConfig(
            scannerType = ScannerType.UROVO_I6310,
            libraryFileName = "UROVO_i6310.jdk",
            downloadUrl = "${BASE_SDK_URL}/UROVO_i6310.jar",
            version = "2.0.0",
        )
    )

    /**
     * Получить конфигурацию для конкретного типа сканера
     */
    fun getConfig(scannerType: ScannerType): SdkConfig? {
        return AVAILABLE_SCANNERS.find { it.scannerType == scannerType }
    }

    /**
     * Проверить, требуется ли загрузка SDK для устройства
     */
    fun requiresDownload(scannerType: ScannerType): Boolean {
        val config = getConfig(scannerType) ?: return false
        return config.downloadUrl.isNotEmpty()
    }
}
