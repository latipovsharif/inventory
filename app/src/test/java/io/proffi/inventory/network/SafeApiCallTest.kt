package io.proffi.inventory.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SafeApiCallTest {

    @Test
    fun successReturnsValue() = runTest {
        val result = safeApiCall { 42 }
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun httpErrorMapsToApiExceptionWithServerMessage() = runTest {
        val body = """{"message":"bad request"}""".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Any>(400, body))

        val result = safeApiCall { throw httpException }

        val error = result.exceptionOrNull()
        assertTrue(error is ApiException)
        error as ApiException
        assertEquals(400, error.code)
        assertEquals("bad request", error.serverMessage)
    }

    @Test
    fun ioErrorMapsToNetworkExceptionAfterRetries() = runTest {
        var attempts = 0
        val result = safeApiCall(retries = 2) {
            attempts++
            throw IOException("boom")
        }
        assertTrue(result.exceptionOrNull() is NetworkException)
        assertEquals(3, attempts) // initial attempt + 2 retries
    }

    @Test
    fun successOnRetryAfterTransientFailure() = runTest {
        var attempts = 0
        val result = safeApiCall(retries = 3) {
            attempts++
            if (attempts < 2) throw IOException("flaky") else "ok"
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(2, attempts)
    }
}
