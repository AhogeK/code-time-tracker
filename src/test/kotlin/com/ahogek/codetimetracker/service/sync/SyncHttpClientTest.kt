package com.ahogek.codetimetracker.service.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class SyncHttpClientTest {

    private lateinit var server: HttpServer
    private lateinit var settings: SyncSettingsState
    private lateinit var client: SyncHttpClient

    private val bearerRequests = AtomicInteger(0)
    private val rateLimitRequests = AtomicInteger(0)
    private val alwaysRateLimitedRequests = AtomicInteger(0)
    private val bodyRetryRequests = AtomicInteger(0)
    private val hugeRetryRequests = AtomicInteger(0)

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bearer") { exchange -> handleBearer(exchange) }
        server.createContext("/unauthorized") { exchange -> handleUnauthorized(exchange) }
        server.createContext("/rate-limit-then-ok") { exchange -> handleRateLimitThenOk(exchange) }
        server.createContext("/always-429") { exchange -> handleAlwaysRateLimited(exchange) }
        server.createContext("/rate-limit-body") { exchange -> handleRateLimitBody(exchange) }
        server.createContext("/rate-limit-huge") { exchange -> handleRateLimitHuge(exchange) }
        server.createContext("/devices") { exchange -> handleDevices(exchange) }
        server.createContext("/devices-register") { exchange -> handleDevicesRegister(exchange) }
        server.start()

        settings = SyncSettingsState()
        settings.serverUrl = "http://127.0.0.1:${server.address.port}"
        client = SyncHttpClient(settings)
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `should inject bearer token and parse success response`() {
        val result = client.execute(
            SyncRequest(method = "GET", path = "/bearer", bearerToken = "test-token"),
            LoginResponse::class.java,
        )

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        val login = (result as SyncResult.Success).data
        assertThat(login.accessToken).isEqualTo("jwt-value")
        assertThat(login.userId).isEqualTo("user-1")
        assertThat(bearerRequests.get()).isEqualTo(1)
    }

    @Test
    fun `should fail when bearer token is missing or wrong`() {
        val result = client.execute(SyncRequest(method = "GET", path = "/bearer"), LoginResponse::class.java)

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.API_KEY_INVALID)
        assertThat(bearerRequests.get()).isEqualTo(1)
    }

    @Test
    fun `should map auth error with code and message`() {
        val result = client.execute(SyncRequest(method = "GET", path = "/unauthorized"), LoginResponse::class.java)

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        val error = (result as SyncResult.Failure).error
        assertThat(error.kind).isEqualTo(SyncErrorKind.API_KEY_INVALID)
        assertThat(error.httpStatus).isEqualTo(401)
        assertThat(error.code).isEqualTo("AUTH_010")
        assertThat(error.message).isEqualTo("API key invalid")
    }

    @Test
    fun `should retry once using header retry-after and succeed`() {
        val result = client.execute(
            SyncRequest(method = "POST", path = "/rate-limit-then-ok"),
            LoginResponse::class.java,
        )

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(rateLimitRequests.get()).isEqualTo(2)
    }

    @Test
    fun `should retry using body retry-after when header is missing`() {
        val result = client.execute(
            SyncRequest(method = "POST", path = "/rate-limit-body"),
            LoginResponse::class.java,
        )

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(bodyRetryRequests.get()).isEqualTo(2)
    }

    @Test
    fun `should stop retrying after max retries and report rate limited`() {
        val result = client.execute(SyncRequest(method = "GET", path = "/always-429"), LoginResponse::class.java)

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        val error = (result as SyncResult.Failure).error
        assertThat(error.kind).isEqualTo(SyncErrorKind.RATE_LIMITED)
        assertThat(error.httpStatus).isEqualTo(429)
        assertThat(error.retryAfterSeconds).isNotNull
        assertThat(alwaysRateLimitedRequests.get()).isEqualTo(SyncHttpClient.MAX_RETRIES + 1)
    }

    @Test
    fun `should fail fast when server retry-after exceeds the sleep cap`() {
        val result = client.execute(SyncRequest(method = "GET", path = "/rate-limit-huge"), LoginResponse::class.java)

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        val error = (result as SyncResult.Failure).error
        assertThat(error.kind).isEqualTo(SyncErrorKind.RATE_LIMITED)
        assertThat(hugeRetryRequests.get()).isEqualTo(1)
    }

    @Test
    fun `should report network error when server is unreachable`() {
        val socket = java.net.ServerSocket(0)
        val deadPort = socket.localPort
        socket.close()
        settings.serverUrl = "http://127.0.0.1:$deadPort"

        val result = client.execute(SyncRequest(method = "GET", path = "/bearer"), LoginResponse::class.java)

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.NETWORK_ERROR)
    }

    @Test
    fun `should compute exponential backoff from attempt index`() {
        assertThat(client.backoffMillis(0)).isEqualTo(1_000L)
        assertThat(client.backoffMillis(1)).isEqualTo(2_000L)
        assertThat(client.backoffMillis(2)).isEqualTo(4_000L)
    }

    private fun handleBearer(exchange: HttpExchange) {
        bearerRequests.incrementAndGet()
        val authorization = exchange.requestHeaders.getFirst("Authorization")
        if (authorization == "Bearer test-token") {
            respond(
                exchange,
                200,
                """{"success":true,"data":{"userId": "user-1", "accessToken": "jwt-value", "refreshToken": "refresh", "expiresIn": 3600, "tokenType": "Bearer"}}""",
            )
        } else {
            respond(exchange, 401, """{"success":false,"message":"API key invalid","data":{"code": "AUTH_010", "message": "API key invalid"}}""")
        }
    }

    private fun handleUnauthorized(exchange: HttpExchange) {
        respond(
            exchange,
            401,
            """{"success":false,"message":"API key invalid","data":{"code": "AUTH_010", "message": "API key invalid", "httpStatus": 401}}""",
        )
    }

    private fun handleRateLimitThenOk(exchange: HttpExchange) {
        val count = rateLimitRequests.incrementAndGet()
        if (count == 1) {
            respond(
                exchange,
                429,
                """{"success":false,"message":"Too many requests","data":{"code": "RATE_LIMIT_001", "message": "Too many requests"}}""",
                "Retry-After" to "0",
            )
        } else {
            respond(exchange, 200, """{"success":true,"data":{"userId": "user-1", "accessToken": "jwt-value"}}""")
        }
    }

    private fun handleAlwaysRateLimited(exchange: HttpExchange) {
        alwaysRateLimitedRequests.incrementAndGet()
        respond(
            exchange,
            429,
            """{"success":false,"message":"Too many requests","data":{"code": "RATE_LIMIT_001", "message": "Too many requests", "retryAfter": "${Instant.now().plusSeconds(5)}"}}""",
        )
    }

    private fun handleRateLimitBody(exchange: HttpExchange) {
        val count = bodyRetryRequests.incrementAndGet()
        if (count == 1) {
            respond(
                exchange,
                429,
                """{"success":false,"message":"Too many requests","data":{"code": "RATE_LIMIT_001", "message": "Too many requests", "retryAfter": "${Instant.now().plusSeconds(5)}"}}""",
            )
        } else {
            respond(exchange, 200, """{"success":true,"data":{"userId": "user-1", "accessToken": "jwt-value"}}""")
        }
    }


    private fun handleRateLimitHuge(exchange: HttpExchange) {
        hugeRetryRequests.incrementAndGet()
        respond(
            exchange,
            429,
            """{"success":false,"message":"Too many requests","data":{"code": "RATE_LIMIT_001", "message": "Too many requests"}}""",
            "Retry-After" to "86400",
        )
    }

    private fun handleDevices(exchange: HttpExchange) {
        respond(
            exchange,
            200,
            """{"success":true,"data":[
                {"id":"dev-1","deviceName":"MacBook Pro","platform":"macOS","ideName":"IntelliJ IDEA","ideVersion":"2026.1","appVersion":"0.13.0","createdAt":"2026-03-01T10:00:00Z","lastSeenAt":"2026-04-28T15:30:00Z"}
            ],"timestamp":"2026-08-27T00:00:00Z"}""",
        )
    }

    private fun handleDevicesRegister(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        val deviceId = if (body.contains("\"deviceId\"")) "dev-1" else "unknown"
        respond(
            exchange,
            200,
            """{"success":true,"data":{"id":"$deviceId","deviceName":"MacBook Pro","platform":"macOS","ideName":"IntelliJ IDEA","ideVersion":"2026.1","appVersion":"0.15.0"}}""",
        )
    }

    @Test
    fun `should deserialize a typed list response via TypeToken`() {
        val result = client.execute<List<DeviceResponse>>(
            SyncRequest(method = "GET", path = "/devices"),
            object : com.google.gson.reflect.TypeToken<List<DeviceResponse>>() {}.type,
        )

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        val devices = (result as SyncResult.Success).data
        assertThat(devices).hasSize(1)
        assertThat(devices[0].id).isEqualTo("dev-1")
        assertThat(devices[0].deviceName).isEqualTo("MacBook Pro")
        assertThat(devices[0].platform).isEqualTo("macOS")
        assertThat(devices[0].ideName).isEqualTo("IntelliJ IDEA")
    }

    @Test
    fun `should serialize a request body and parse a typed response on POST`() {
        val result = client.execute(
            SyncRequest(
                method = "POST",
                path = "/devices-register",
                body = RegisterDeviceRequest(
                    deviceId = "dev-1",
                    deviceName = "MacBook Pro",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    ideVersion = "2026.1",
                    appVersion = "0.15.0",
                ),
            ),
            DeviceResponse::class.java,
        )

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        val device = (result as SyncResult.Success).data
        assertThat(device.id).isEqualTo("dev-1")
        assertThat(device.deviceName).isEqualTo("MacBook Pro")
        assertThat(device.platform).isEqualTo("macOS")
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
        vararg extraHeaders: Pair<String, String>,
    ) {
        exchange.responseHeaders.set("Content-Type", "application/json")
        extraHeaders.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
