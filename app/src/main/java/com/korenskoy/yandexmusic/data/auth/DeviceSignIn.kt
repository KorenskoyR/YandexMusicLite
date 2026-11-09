package com.korenskoy.yandexmusic.data.auth

import android.os.Build
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.di.UpdateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sign-in by a code on the phone (Yandex OAuth device flow), for devices without WebView — head units
 * like the Geely Galaxy L7. The device asks for a short code, the person enters it at ya.ru/device on
 * the phone, and the device keeps asking for the token until they confirm or the code runs out.
 *
 * The requests go through the client without the Yandex auth headers: there is no token yet, and the
 * old one (if any) must not travel with a sign-in.
 */
@Singleton
class DeviceSignIn @Inject constructor(
    @param:UpdateClient private val client: OkHttpClient
) {
    data class Code(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int
    )

    sealed interface Poll {
        /** Not confirmed yet: ask again after [delaySeconds]. */
        data class Wait(val delaySeconds: Int) : Poll
        data class Token(val token: String) : Poll
        /** The code ran out or was used: a new one is needed. */
        data object Expired : Poll
        /** The person declined on the phone. */
        data object Denied : Poll
        /** The app is set up wrong (the client secret): no new code will help. */
        data object Misconfigured : Poll
    }

    /** False in a build made without the secret: the screen then explains how to get WebView instead. */
    val available: Boolean get() = SECRET.isNotBlank()

    suspend fun requestCode(): Code = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("device_name", deviceName())
            .build()
        val body = post(CODE_URL, form).second
        parseCode(body) ?: throw IOException("No device code in the answer")
    }

    /** One question to the server; a network failure is thrown, so the caller simply asks again later. */
    suspend fun poll(code: Code, currentInterval: Int): Poll = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "device_code")
            .add("code", code.deviceCode)
            .add("client_id", CLIENT_ID)
            .add("client_secret", SECRET)
            .build()
        val (status, body) = post(TOKEN_URL, form)
        parsePoll(status, body, currentInterval)
    }

    private fun post(url: String, form: FormBody): Pair<Int, String> {
        val request = Request.Builder().url(url).post(form).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code >= 500) throw IOException("Server error ${response.code}")
            return response.code to body
        }
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ")
            .ifBlank { "Android" }.take(100)

    companion object {
        /** The client the app signs in as everywhere (the same as the WebView sign-in). */
        const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
        private val SECRET: String get() = BuildConfig.YANDEX_DEVICE_SECRET
        private const val CODE_URL = "https://oauth.yandex.ru/device/code"
        private const val TOKEN_URL = "https://oauth.yandex.ru/token"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun obj(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.number(key: String) = this[key]?.jsonPrimitive?.intOrNull

        internal fun parseCode(body: String): Code? {
            val o = obj(body) ?: return null
            val deviceCode = o.text("device_code")?.takeIf { it.isNotBlank() } ?: return null
            val userCode = o.text("user_code")?.takeIf { it.isNotBlank() } ?: return null
            // Only a Yandex page may be shown as the place to enter the code
            val url = o.text("verification_url")?.takeIf { isYandexHttps(it) } ?: "https://ya.ru/device"
            return Code(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUrl = url,
                intervalSeconds = (o.number("interval") ?: 5).coerceIn(1, 60),
                expiresInSeconds = (o.number("expires_in") ?: 300).coerceIn(30, 3600)
            )
        }

        internal fun parsePoll(status: Int, body: String, currentInterval: Int): Poll {
            val o = obj(body)
            o?.text("access_token")?.takeIf { status in 200..299 && it.isNotBlank() }?.let { return Poll.Token(it) }
            return when (o?.text("error")) {
                "authorization_pending" -> Poll.Wait(currentInterval)
                "slow_down" -> Poll.Wait((currentInterval + 5).coerceAtMost(60))
                "access_denied" -> Poll.Denied
                "invalid_client", "unauthorized_client" -> Poll.Misconfigured
                "expired_token", "invalid_grant", "bad_verification_code" -> Poll.Expired
                // Something unexpected: a fresh code is the way out, not a loop of failing questions
                else -> Poll.Expired
            }
        }

        /** The code on screen in two halves, as people read it out: "6iyq–f7ik". */
        fun groups(userCode: String): List<String> {
            val code = userCode.trim()
            if (code.length < 6) return listOf(code)
            val half = (code.length + 1) / 2
            return listOf(code.take(half), code.drop(half))
        }

        internal fun isYandexHttps(url: String): Boolean {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            if (uri.scheme != "https") return false
            val host = uri.host?.lowercase() ?: return false
            return listOf("ya.ru", "yandex.ru", "yandex.com").any { host == it || host.endsWith(".$it") }
        }
    }
}
