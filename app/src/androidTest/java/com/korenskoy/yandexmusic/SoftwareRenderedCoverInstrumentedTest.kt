package com.korenskoy.yandexmusic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Head units that draw their window without the GPU (Android 9, Allwinner, Mali-400) crashed at start:
 * a "hardware" bitmap cannot be drawn on a software canvas. The app's own image loader must hand out plain
 * ones, which such a canvas draws.
 */
@RunWith(AndroidJUnit4::class)
class SoftwareRenderedCoverInstrumentedTest {

    @Test
    fun aCoverFromTheAppsLoaderDrawsOnASoftwareCanvas(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A cover on the disk, as one from the network would be after the cache
        val file = File(context.cacheDir, "software_cover.png")
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(43, 76, 126)) }
            .also { bmp -> file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(file).build())
        assertTrue("The cover did not load", result is SuccessResult)
        val bitmap = (result as SuccessResult).image.toBitmap()
        assertNotEquals(Bitmap.Config.HARDWARE, bitmap.config)

        // What ViewRootImpl.drawSoftware does with it: throws for a hardware bitmap
        val target = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(target).drawBitmap(bitmap, 0f, 0f, null)
        assertTrue(target.getPixel(32, 32) == Color.rgb(43, 76, 126))
        file.delete()
    }
}
