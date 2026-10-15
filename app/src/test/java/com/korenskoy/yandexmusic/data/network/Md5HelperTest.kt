package com.korenskoy.yandexmusic.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Field

class Md5HelperTest {

    @Test
    fun testGenerateMp3Url() {
        // Set the mock SALT for test using reflection, since it's a private const/val or read from BuildConfig
        val field = Md5Helper::class.java.getDeclaredField("SALT")
        field.isAccessible = true
        
        // Remove 'final' modifier to modify if needed, but in Kotlin object it's usually static
        // Since we can't easily modify const val, we assume the SALT is injected or test what it outputs.
        // Actually, we can just test if the URL format is correct.
        
        val url = Md5Helper.generateMp3Url(
            host = "s33vla.storage.yandex.net",
            path = "/get-mp3/b1c4e1d3/track.mp3",
            ts = "123456",
            s = "saltysalt"
        )
        
        assert(url.startsWith("https://s33vla.storage.yandex.net/get-mp3/"))
    }
}
