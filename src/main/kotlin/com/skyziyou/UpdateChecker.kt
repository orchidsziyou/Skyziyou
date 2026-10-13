package com.skyziyou

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Manual update check against the GitHub "latest release" API.
 *
 * Deliberately read-only: it never downloads or replaces jars (the running jar is
 * locked on Windows and install paths differ across launchers). When a newer tag is
 * found the General page shows a notice and offers a button that opens the releases
 * page in the system browser, so the user picks and installs the jar themselves.
 *
 * The HTTP call runs on the HttpClient's background thread; [status] is volatile
 * and read every frame by the settings UI, so no client-thread marshalling is
 * needed. GitHub allows 60 unauthenticated API requests per hour per IP.
 */
object UpdateChecker {

    enum class Status { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

    private const val API_URL = "https://api.github.com/repos/orchidsziyou/Skyziyou/releases/latest"

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    @Volatile
    var status: Status = Status.IDLE
        private set

    /** Latest tag with any leading "v" removed, e.g. "1.2.6"; only set on AVAILABLE/UP_TO_DATE. */
    @Volatile
    var latestVersion: String? = null
        private set

    /** Web page of the latest release (not the API URL), for the manual-download button. */
    @Volatile
    var latestPageUrl: String? = null
        private set

    /** Short, user-facing failure reason; only set on FAILED. */
    @Volatile
    var failureReason: String? = null
        private set

    /** Starts an asynchronous check; a second click while one is in flight is ignored. */
    fun check() {
        if (status == Status.CHECKING) return
        status = Status.CHECKING
        failureReason = null

        val request = HttpRequest.newBuilder(URI.create(API_URL))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Skyziyou-UpdateChecker")
            .GET()
            .build()

        client.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete { response, error ->
            if (error != null) {
                fail(if (I18n.isZh()) "网络错误：无法连接 GitHub" else "Network error: cannot reach GitHub")
                return@whenComplete
            }
            if (response.statusCode() != 200) {
                fail("HTTP ${response.statusCode()}")
                return@whenComplete
            }
            try {
                val release = JsonParser.parseString(response.body()).asJsonObject
                val tag = release.get("tag_name")?.asString?.removePrefix("v").orEmpty()
                val pageUrl = release.get("html_url")?.asString.orEmpty()
                require(tag.isNotEmpty() && pageUrl.isNotEmpty())
                latestVersion = tag
                latestPageUrl = pageUrl
                status = if (isNewer(tag, GeneralScreen.modVersion())) Status.AVAILABLE else Status.UP_TO_DATE
            } catch (_: Exception) {
                fail(if (I18n.isZh()) "返回内容解析失败" else "Malformed response")
            }
        }
    }

    private fun fail(reason: String) {
        failureReason = reason
        latestVersion = null
        latestPageUrl = null
        status = Status.FAILED
    }

    /**
     * Compares dotted numeric versions (1.2.10 > 1.2.9); missing components count as
     * zero and any non-numeric suffix is ignored, which is sufficient for our tags.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = digits(candidate)
        val b = digits(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun digits(version: String): List<Int> =
        version.substringBefore('-')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
