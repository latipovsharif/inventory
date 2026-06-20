package io.proffi.inventory.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/** Carries the HTTP status code and the server-provided error message. */
class ApiException(
    val code: Int,
    val serverMessage: String?,
    cause: Throwable? = null
) : Exception(serverMessage ?: "HTTP $code", cause)

/** Connectivity / timeout failure (no HTTP response received). */
class NetworkException(cause: Throwable) :
    Exception("Нет связи с сервером. Проверьте подключение.", cause)

private data class ErrorEnvelope(
    val message: String?,
    val detail: String?,
    val error: String?
)

private val errorGson = Gson()

private fun HttpException.serverMessage(): String? = try {
    val raw = response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
    if (raw == null) {
        null
    } else {
        val env = try {
            errorGson.fromJson(raw, ErrorEnvelope::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
        env?.message ?: env?.detail ?: env?.error ?: raw.take(300)
    }
} catch (e: Exception) {
    null
}

/**
 * Runs an API call and normalizes failures into typed results:
 * - non-2xx HTTP  → [ApiException] with the server's message
 * - connectivity  → [NetworkException]
 *
 * [retries] retries transient [IOException]s with linear backoff. Use it ONLY
 * for idempotent reads (GET). Never retry scans/POSTs that mutate state — a
 * retried write can double-count inventory.
 */
suspend fun <T> safeApiCall(retries: Int = 0, block: suspend () -> T): Result<T> {
    var attempt = 0
    while (true) {
        try {
            return Result.success(block())
        } catch (e: HttpException) {
            return Result.failure(ApiException(e.code(), e.serverMessage(), e))
        } catch (e: IOException) {
            if (attempt < retries) {
                attempt++
                delay(RETRY_DELAY_MS * attempt)
            } else {
                return Result.failure(NetworkException(e))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

private const val RETRY_DELAY_MS = 500L
