package com.ahogek.codetimetracker.service.sync

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * Result of a sync API call.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
sealed interface SyncResult<out T> {
    data class Success<T>(val data: T) : SyncResult<T>
    data class Failure(val error: SyncError) : SyncResult<Nothing>
}

/**
 * A single HTTP request against the ctt-server API.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
data class SyncRequest(
    val method: String,
    val path: String,
    val body: Any? = null,
    val bearerToken: String? = null,
)

/**
 * HTTP transport for the sync feature, built on the JDK [HttpClient] (zero new
 * dependencies). Injects the `Authorization: Bearer` header when a token is provided,
 * serialises request bodies with Gson, normalises failures into [SyncError] and retries
 * 429 responses using Retry-After (header delta-seconds first, response-body ISO-8601
 * fallback) with capped exponential backoff.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
@Service(Service.Level.APP)
class SyncHttpClient(private val settings: SyncSettingsState) : Disposable {

    companion object {
        const val MAX_RETRIES = 2
        const val REQUEST_TIMEOUT_SECONDS = 30L
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_RETRY_SLEEP_MS = 60_000L
        private val log = Logger.getInstance(SyncHttpClient::class.java)
    }

    private val gson: Gson = GsonBuilder().create()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /**
     * Executes [request], retrying rate-limited responses up to [MAX_RETRIES] times.
     *
     * @param responseType the class used to deserialise a 2xx response body
     */
    fun <T> execute(request: SyncRequest, responseType: Class<T>): SyncResult<T> {
        var attempt = 0
        while (true) {
            when (val outcome = trySend(request, responseType)) {
                is Outcome.Success -> return SyncResult.Success(outcome.data)
                is Outcome.HttpFailure -> {
                    val retryAfter = SyncErrorMapper.parseRetryAfter(
                        outcome.retryAfterHeader,
                        outcome.errorBody?.retryAfter,
                    )
                    if (outcome.statusCode == 429 && attempt < MAX_RETRIES) {
                        val delayMs = retryAfter ?: backoffMillis(attempt)
                        // A server-controlled delay beyond the cap is not worth blocking the
                        // caller thread for; fail fast instead of retrying.
                        if (delayMs > MAX_RETRY_SLEEP_MS) {
                            return SyncResult.Failure(
                                SyncError(
                                    kind = SyncErrorKind.RATE_LIMITED,
                                    httpStatus = outcome.statusCode,
                                    code = outcome.errorBody?.code,
                                    message = outcome.errorBody?.message,
                                    retryAfterSeconds = retryAfter,
                                ),
                            )
                        }
                        if (!sleepQuietly(delayMs)) {
                            return SyncResult.Failure(SyncError(SyncErrorKind.UNKNOWN))
                        }
                        attempt++
                    } else {
                        return SyncResult.Failure(
                            SyncError(
                                kind = SyncErrorMapper.map(outcome.statusCode, outcome.errorBody?.code),
                                httpStatus = outcome.statusCode,
                                code = outcome.errorBody?.code,
                                message = outcome.errorBody?.message,
                                retryAfterSeconds = retryAfter,
                            ),
                        )
                    }
                }
                is Outcome.TransportFailure -> return SyncResult.Failure(outcome.error)
            }
        }
    }

    private sealed interface Outcome<out T> {
        data class Success<T>(val data: T) : Outcome<T>
        data class HttpFailure(
            val statusCode: Int,
            val errorBody: ErrorData?,
            val retryAfterHeader: String?,
        ) : Outcome<Nothing>

        data class TransportFailure(val error: SyncError) : Outcome<Nothing>
    }

    private fun <T> trySend(request: SyncRequest, responseType: Class<T>): Outcome<T> {
        val uri = URI.create(settings.serverUrl.trimEnd('/') + request.path)
        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        request.bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        val publisher = if (request.body == null) {
            HttpRequest.BodyPublishers.noBody()
        } else {
            HttpRequest.BodyPublishers.ofString(gson.toJson(request.body))
        }
        builder.method(request.method, publisher)

        return try {
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val status = response.statusCode()
            if (status in 200..299) {
                val data = parseBody(response.body(), responseType)
                if (data != null) {
                    Outcome.Success(data)
                } else {
                    Outcome.TransportFailure(
                        SyncError(SyncErrorKind.UNKNOWN, httpStatus = status, message = "Unparseable response body"),
                    )
                }
            } else {
                Outcome.HttpFailure(
                    statusCode = status,
                    errorBody = parseErrorBody(response.body()),
                    retryAfterHeader = response.headers().firstValue("Retry-After").orElse(null),
                )
            }
        } catch (e: HttpTimeoutException) {
            Outcome.TransportFailure(SyncError(SyncErrorKind.TIMEOUT))
        } catch (e: IOException) {
            Outcome.TransportFailure(SyncError(SyncErrorKind.NETWORK_ERROR))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Outcome.TransportFailure(SyncError(SyncErrorKind.UNKNOWN))
        }
    }

    private fun <T> parseBody(body: String, type: Class<T>): T? = try {
        val envelope = gson.fromJson(body, RestApiEnvelope::class.java)
        envelope.data?.let { gson.fromJson(it, type) }
    } catch (e: JsonSyntaxException) {
        log.warn("Failed to parse sync response body", e)
        null
    }

    private fun parseErrorBody(body: String): ErrorData? = try {
        gson.fromJson(body, RestApiEnvelope::class.java).data
            ?.takeIf { it.isJsonObject }
            ?.let { gson.fromJson(it, ErrorData::class.java) }
    } catch (e: JsonSyntaxException) {
        null
    }

    /**
     * Exponential backoff for a retry attempt: 1s, 2s, ... (bounded by [MAX_RETRIES] and [MAX_RETRY_SLEEP_MS])
     */
    internal fun backoffMillis(attempt: Int): Long = BASE_BACKOFF_MS shl attempt

    private fun sleepQuietly(millis: Long): Boolean = try {
        Thread.sleep(millis)
        true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    override fun dispose() {
        httpClient.close()
    }
}
