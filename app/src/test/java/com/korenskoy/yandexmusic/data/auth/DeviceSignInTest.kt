package com.korenskoy.yandexmusic.data.auth

import com.korenskoy.yandexmusic.data.auth.DeviceSignIn.Poll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sign-in by a code on the phone: what the Yandex OAuth answers mean for the screen. */
class DeviceSignInTest {

    @Test
    fun theCodeIsRead() {
        val code = DeviceSignIn.parseCode(
            """{"device_code":"85308c1e","expires_in":300,"interval":5,"user_code":"6iyqf7ik","verification_url":"https://ya.ru/device"}"""
        )!!
        assertEquals("6iyqf7ik", code.userCode)
        assertEquals("85308c1e", code.deviceCode)
        assertEquals("https://ya.ru/device", code.verificationUrl)
        assertEquals(5, code.intervalSeconds)
        assertEquals(300, code.expiresInSeconds)
    }

    @Test
    fun onlyAYandexPageIsShownAsThePlaceToEnterTheCode() {
        val code = DeviceSignIn.parseCode("""{"device_code":"d","user_code":"u","verification_url":"https://evil.example/device"}""")!!
        assertEquals("https://ya.ru/device", code.verificationUrl)
        assertEquals("https://ya.ru/device", DeviceSignIn.parseCode("""{"device_code":"d","user_code":"u","verification_url":"http://ya.ru/device"}""")!!.verificationUrl)
    }

    @Test
    fun aBrokenAnswerIsNoCode() {
        assertNull(DeviceSignIn.parseCode("not json"))
        assertNull(DeviceSignIn.parseCode("""{"user_code":"u"}"""))
    }

    @Test
    fun crazyTimingsAreKeptSane() {
        val code = DeviceSignIn.parseCode("""{"device_code":"d","user_code":"u","interval":0,"expires_in":999999}""")!!
        assertEquals(1, code.intervalSeconds)
        assertEquals(3600, code.expiresInSeconds)
    }

    @Test
    fun theAnswersWhileWaiting() {
        assertEquals(Poll.Wait(5), DeviceSignIn.parsePoll(400, """{"error":"authorization_pending"}""", 5))
        assertEquals(Poll.Wait(10), DeviceSignIn.parsePoll(400, """{"error":"slow_down"}""", 5))
        assertEquals(Poll.Token("y0_abc"), DeviceSignIn.parsePoll(200, """{"access_token":"y0_abc","token_type":"bearer"}""", 5))
        assertEquals(Poll.Denied, DeviceSignIn.parsePoll(400, """{"error":"access_denied"}""", 5))
        assertEquals(Poll.Misconfigured, DeviceSignIn.parsePoll(400, """{"error":"invalid_client","error_description":"Wrong client secret"}""", 5))
        assertEquals(Poll.Expired, DeviceSignIn.parsePoll(400, """{"error":"expired_token"}""", 5))
        assertEquals(Poll.Expired, DeviceSignIn.parsePoll(400, """{"error":"invalid_grant"}""", 5))
        // Anything unexpected ends in a fresh code, not in an endless loop of failing questions
        assertEquals(Poll.Expired, DeviceSignIn.parsePoll(400, "<html>", 5))
        // A token is never taken from an error answer
        assertEquals(Poll.Expired, DeviceSignIn.parsePoll(400, """{"access_token":"y0_abc"}""", 5))
    }

    @Test
    fun theCodeIsShownInTwoHalves() {
        assertEquals(listOf("6iyq", "f7ik"), DeviceSignIn.groups("6iyqf7ik"))
        assertEquals(listOf("abcd", "efg"), DeviceSignIn.groups("abcdefg"))
        assertEquals(listOf("abc"), DeviceSignIn.groups("abc"))
    }
}
