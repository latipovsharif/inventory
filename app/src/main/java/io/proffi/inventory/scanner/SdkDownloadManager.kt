package io.proffi.inventory.scanner

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Состояние загрузки SDK
 */
sealed class DownloadState {
    object Idle : DownloadState()
    object Checking : DownloadState()
    object AlreadyDownloaded : DownloadState()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Success(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Менеджер для загрузки SDK библиотек с сервера
 */
class SdkDownloadManager(private val context: Context) {

    private val sdkDirectory: File by lazy {
        File(context.filesDir, "scanner_sdk").apply {
            if (!exists()) mkdirs()
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)  // Таймаут подключения к серверу
        .readTimeout(5, TimeUnit.SECONDS)    // Таймаут чтения данных
        .writeTimeout(30, TimeUnit.SECONDS)   // Таймаут записи данных
        .build()

    /**
     * Проверить, загружен ли SDK
     */
    fun isSdkDownloaded(config: SdkConfig): Boolean {
        if (config.downloadUrl.isEmpty()) return true // Встроенный

        val file = File(sdkDirectory, config.libraryFileName)
        return file.exists()
    }

    /**
     * Получить путь к загруженному SDK
     */
    fun getSdkPath(config: SdkConfig): String? {
        if (!isSdkDownloaded(config)) return null
        return File(sdkDirectory, config.libraryFileName).absolutePath
    }

    /**
     * Загрузить SDK с сервера
     */
    fun downloadSdk(config: SdkConfig): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Checking)

            // Проверяем, не загружен ли уже
            if (isSdkDownloaded(config)) {
                Log.i(TAG, "SDK ${config.libraryFileName} already downloaded")
                emit(DownloadState.AlreadyDownloaded)
                emit(DownloadState.Success(getSdkPath(config)!!))
                return@flow
            }

            // Если встроенный (камера)
            if (config.downloadUrl.isEmpty()) {
                emit(DownloadState.Success("builtin"))
                return@flow
            }

            Log.i(TAG, "Starting download of ${config.libraryFileName} from ${config.downloadUrl}")

            // Создаем запрос
            val request = Request.Builder()
                .url(config.downloadUrl)
                .build()

            // Выполняем запрос
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadState.Error("Ошибка загрузки: HTTP ${response.code}"))
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    emit(DownloadState.Error("Пустой ответ от сервера"))
                    return@flow
                }

                val totalBytes = body.contentLength()
                val tempFile = File(sdkDirectory, "${config.libraryFileName}.tmp")
                val targetFile = File(sdkDirectory, config.libraryFileName)

                // Загружаем файл с отслеживанием прогресса
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0L
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            } else {
                                0
                            }

                            emit(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                        }
                    }
                }

                // Переименовываем временный файл
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    emit(DownloadState.Error("Ошибка сохранения файла"))
                    return@flow
                }

                Log.i(TAG, "Successfully downloaded ${config.libraryFileName}")
                emit(DownloadState.Success(targetFile.absolutePath))
            }

        } catch (e: SocketTimeoutException) {
            // Таймаут подключения к серверу (5 секунд)
            Log.e(TAG, "SDK download connection timeout", e)
            emit(DownloadState.Error("Сервер недоступен, попробуйте позднее"))
        } catch (e: ConnectException) {
            // Ошибка подключения к серверу
            Log.e(TAG, "SDK download connection error", e)
            emit(DownloadState.Error("Сервер недоступен, попробуйте позднее"))
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading SDK", e)
            emit(DownloadState.Error("Ошибка загрузки: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Удалить загруженный SDK
     */
    fun deleteSdk(config: SdkConfig): Boolean {
        val file = File(sdkDirectory, config.libraryFileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Получить размер загруженного SDK в байтах
     */
    fun getSdkSize(config: SdkConfig): Long {
        val file = File(sdkDirectory, config.libraryFileName)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Получить общий размер всех загруженных SDK
     */
    fun getTotalSdkSize(): Long {
        return sdkDirectory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Очистить все загруженные SDK
     */
    fun clearAllSdks(): Boolean {
        return try {
            sdkDirectory.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing SDKs", e)
            false
        }
    }

    /**
     * Вычислить MD5 хеш файла
     */
    private fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SdkDownloadManager"
    }
}
