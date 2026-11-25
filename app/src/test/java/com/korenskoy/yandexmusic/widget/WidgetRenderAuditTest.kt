package com.korenskoy.yandexmusic.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.LocalSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders every widget size and state through [GlanceRemoteViews] and [android.widget.RemoteViews.apply]
 * to verify real pixel bounds, non-overlapping buttons, readable titles, visual harmony and Roborazzi snapshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 35], qualifiers = "w600dp-h600dp-mdpi", application = Application::class)
class WidgetRenderAuditTest {

    private data class Profile(val kind: String, val w: Int, val h: Int)
    private data class LeafBox(val view: View, val left: Int, val top: Int, val right: Int, val bottom: Int)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshotActivity: ComponentActivity? by lazy {
        if (android.os.Build.VERSION.SDK_INT == 35) {
            Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        } else {
            null
        }
    }

    private fun dummyBitmap(size: Int, color: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(color) }

    private fun playingNow(withCover: Boolean = true, isRadio: Boolean = false): WidgetNow {
        val cover = if (withCover) dummyBitmap(64, Color.rgb(156, 44, 62)) else null
        val glow = if (withCover) dummyBitmap(32, Color.rgb(68, 22, 34)) else null
        return WidgetNow(
            title = "Группа крови — очень длинное название",
            artist = if (isRadio) "В эфире" else "Кино",
            isPlaying = true,
            isLiked = true,
            isRadio = isRadio,
            canReact = true,
            progress = if (isRadio) null else 0.42f,
            positionMs = if (isRadio) 0L else 82_000L,
            durationMs = if (isRadio) 0L else 285_000L,
            source = if (isRadio) "РАДИО" else "МОЯ ВОЛНА",
            cover = cover,
            glow = glow
        )
    }

    private fun emptyNow() = WidgetNow(
        title = null, artist = null, isPlaying = false, isLiked = false,
        isRadio = false, canReact = false, progress = null,
        positionMs = 0L, durationMs = 0L, source = null, cover = null, glow = null
    )

    private fun collectLeaves(root: View, view: View, ox: Int, oy: Int, out: MutableList<LeafBox>) {
        if (view.visibility != View.VISIBLE) return
        val x = ox + view.left
        val y = oy + view.top
        if (view is ViewGroup) {
            if (view.contentDescription != null && view.width > 0 && view.height > 0 && view !== root) {
                out.add(LeafBox(view, x, y, x + view.width, y + view.height))
            }
            for (i in 0 until view.childCount) {
                collectLeaves(root, view.getChildAt(i), x - view.scrollX, y - view.scrollY, out)
            }
        } else if (view.width > 0 && view.height > 0) {
            val meaningful = view is ImageView ||
                (view is TextView && !view.text.isNullOrEmpty()) ||
                view.contentDescription != null
            if (meaningful) {
                out.add(LeafBox(view, x, y, x + view.width, y + view.height))
            }
        }
    }

    private fun intersects(a: LeafBox, b: LeafBox): Boolean {
        val tol = 1
        return a.left + tol < b.right && b.left + tol < a.right &&
            a.top + tol < b.bottom && b.top + tol < a.bottom
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun renderAndAudit(
        profile: Profile,
        stateName: String,
        now: WidgetNow,
        expectTitle: Boolean,
        capture: Boolean,
        failures: MutableList<String>
    ) {
        val tag = "${profile.kind}_${profile.w}x${profile.h}_$stateName"
        val dpSize = DpSize(profile.w.dp, profile.h.dp)
        val density = context.resources.displayMetrics.density
        val wPx = (profile.w * density).roundToInt()
        val hPx = (profile.h * density).roundToInt()

        val result = runBlocking {
            GlanceRemoteViews().compose(context, dpSize) {
                CompositionLocalProvider(LocalSize provides dpSize) {
                    when (profile.kind) {
                        "line" -> LineWidget(now)
                        "player" -> PlayerWidgetContent(now)
                        else -> CoverWidgetContent(now)
                    }
                }
            }
        }

        val uiContext = snapshotActivity ?: context
        val host = FrameLayout(uiContext)
        val applied = result.remoteViews.apply(uiContext, host)
        host.addView(applied)
        snapshotActivity?.setContentView(host, ViewGroup.LayoutParams(wPx, hPx))
        host.measure(
            MeasureSpec.makeMeasureSpec(wPx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(hPx, MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, wPx, hPx)

        val leaves = mutableListOf<LeafBox>()
        collectLeaves(host, host, 0, 0, leaves)
        if (leaves.isEmpty()) {
            failures.add("$tag: rendered zero visible leaves")
            return
        }

        val tol = 1
        for (leaf in leaves) {
            if (leaf.left < -tol || leaf.top < -tol || leaf.right > wPx + tol || leaf.bottom > hPx + tol) {
                val desc = leaf.view.contentDescription ?: (leaf.view as? TextView)?.text ?: leaf.view::class.java.simpleName
                failures.add("$tag: leaf [$desc] clipped (${leaf.left},${leaf.top}..${leaf.right},${leaf.bottom}) outside 0..$wPx x 0..$hPx")
            }
        }

        val buttonLabels = setOf(
            "Предыдущий", "Воспроизведение или пауза", "Следующий",
            "Нравится", "Не нравится", "Слушать Мою волну"
        )
        val buttons = leaves.filter { it.view.contentDescription?.toString() in buttonLabels }
        for (i in buttons.indices) {
            for (j in i + 1 until buttons.size) {
                if (intersects(buttons[i], buttons[j])) {
                    failures.add("$tag: buttons overlap [${buttons[i].view.contentDescription}] and [${buttons[j].view.contentDescription}]")
                }
            }
        }

        if (expectTitle) {
            val minTitlePx = (48f * density).roundToInt()
            val expectedText = if (now.hasTrack) now.title else "Моя волна"
            val titleLeaf = leaves.firstOrNull {
                val tv = it.view as? TextView ?: return@firstOrNull false
                expectedText != null && tv.text.toString().startsWith(expectedText.take(6))
            }
            if (titleLeaf == null) {
                failures.add("$tag: expected title TextView [$expectedText] missing")
            } else if ((titleLeaf.right - titleLeaf.left) < minTitlePx) {
                failures.add("$tag: title width ${titleLeaf.right - titleLeaf.left}px < min $minTitlePx px")
            }
        }

        if (profile.kind == "cover" && now.cover != null) {
            val artLeaf = leaves.firstOrNull {
                it.view is ImageView && it.view.contentDescription?.toString() == now.title
            }
            if (artLeaf == null) {
                failures.add("$tag: cover ImageView missing")
            } else {
                val aw = (artLeaf.right - artLeaf.left).coerceAtLeast(1).toFloat()
                val ah = (artLeaf.bottom - artLeaf.top).coerceAtLeast(1).toFloat()
                val ratio = maxOf(aw / ah, ah / aw)
                if (ratio > 1.20f) {
                    val fmt = String.format(Locale.US, "%.2f", ratio)
                    failures.add("$tag: cover artwork cropped aspect=$fmt (${aw.toInt()}x${ah.toInt()}px)")
                }
            }
        }

        auditVisualHarmony(profile, stateName, now, leaves, buttonLabels, wPx, hPx, density, failures)

        if (capture && android.os.Build.VERSION.SDK_INT == 35) {
            host.captureRoboImage("src/test/screenshots/widget_${profile.kind}_${profile.w}x${profile.h}_${stateName}_sdk35.png")
        }
    }

    private fun auditVisualHarmony(
        profile: Profile,
        stateName: String,
        now: WidgetNow,
        leaves: List<LeafBox>,
        buttonLabels: Set<String>,
        wPx: Int,
        hPx: Int,
        density: Float,
        failures: MutableList<String>
    ) {
        if (!now.hasTrack) return
        val tag = "${profile.kind}_${profile.w}x${profile.h}_$stateName"
        val playLeaf = leaves.firstOrNull { it.view.contentDescription?.toString() == "Воспроизведение или пауза" }
        val artLeaf = if (now.cover != null) {
            leaves.firstOrNull { it.view is ImageView && it.view.contentDescription?.toString() == now.title }
        } else {
            null
        }
        val aspect = profile.w.toFloat() / profile.h.toFloat()

        if (playLeaf != null) {
            val playCenter = (playLeaf.left + playLeaf.right) * 0.5f
            if (profile.kind == "player" || (profile.kind == "cover" && aspect <= 1.18f)) {
                val diffDp = abs(playCenter - wPx * 0.5f) / density
                if (diffDp > 6f) {
                    failures.add("$tag: play button off-center by ${"%.1f".format(Locale.US, diffDp)}dp")
                }
            } else if (profile.kind == "cover" && aspect > 1.18f && artLeaf != null) {
                val rightCenter = (artLeaf.right + (wPx - 12f * density)) * 0.5f
                val diffDp = abs(playCenter - rightCenter) / density
                if (diffDp > 10f) {
                    failures.add("$tag: wide cover play button off-center in right pane by ${"%.1f".format(Locale.US, diffDp)}dp")
                }
            }
        }

        if (profile.kind == "line" && playLeaf != null) {
            val playHDp = (playLeaf.bottom - playLeaf.top) / density
            val maxPlayDp = minOf(52f, minOf(profile.h.toFloat(), 96f) * 0.76f)
            if (playHDp > maxPlayDp + 1f) {
                failures.add("$tag: line play button oversized ${"%.1f".format(Locale.US, playHDp)}dp > ${"%.1f".format(Locale.US, maxPlayDp)}dp")
            }
            if (profile.w >= 300) {
                val titleLeaf = leaves.firstOrNull {
                    it.view is TextView && it.view.text.toString().startsWith(now.title.orEmpty().take(6))
                }
                val titleW = ((titleLeaf?.right ?: 0) - (titleLeaf?.left ?: 0)) / density
                if (titleW < profile.w * 0.28f) {
                    failures.add("$tag: line title width ${"%.1f".format(Locale.US, titleW)}dp < 28% of ${profile.w}dp")
                }
            }
            if (profile.w >= 420 && now.canReact && leaves.none { it.view.contentDescription?.toString() == "Нравится" }) {
                failures.add("$tag: wide line missing heart button")
            }
            if (leaves.none { it.view.contentDescription?.toString() == "Следующий" }) {
                failures.add("$tag: line missing next button (check Glance 10-child Row limit)")
            }
        }

        if (profile.kind == "player" && now.cover != null) {
            if (profile.w >= 260 && profile.h >= 145 && artLeaf == null) {
                failures.add("$tag: player missing cover artwork at ${profile.w}x${profile.h}")
            }
            if (profile.h >= 220 && artLeaf != null) {
                val artDp = (artLeaf.bottom - artLeaf.top) / density
                val minArtDp = minOf(profile.h * 0.50f, profile.w * 0.40f)
                if (artDp < minArtDp) {
                    failures.add("$tag: large player cover too small ${"%.1f".format(Locale.US, artDp)}dp < ${"%.1f".format(Locale.US, minArtDp)}dp")
                }
            }
        }

        if (profile.kind == "cover" && now.cover != null && aspect < 0.85f && artLeaf != null) {
            val artWDp = (artLeaf.right - artLeaf.left) / density
            if (artWDp < profile.w * 0.68f) {
                failures.add("$tag: tall cover artwork too narrow ${"%.1f".format(Locale.US, artWDp)}dp < 68% of ${profile.w}dp")
            }
        }

        val isFramedCard = profile.kind == "player" || (profile.kind == "cover" && (aspect < 0.85f || aspect > 1.18f))
        if (isFramedCard && now.cover != null) {
            val fg = leaves.filter { leaf ->
                val isBtn = leaf.view.contentDescription?.toString() in buttonLabels
                val isText = leaf.view is TextView && !leaf.view.text.isNullOrEmpty()
                val isArt = leaf.view is ImageView && leaf.view.contentDescription?.toString() == now.title
                val hLeaf = leaf.bottom - leaf.top
                val wLeaf = leaf.right - leaf.left
                val isProgress = leaf.view is ImageView && leaf.view.contentDescription == null &&
                    hLeaf <= (10f * density).roundToInt() && wLeaf >= (40f * density).roundToInt()
                isBtn || isText || isArt || isProgress
            }
            val bands = mergeVerticalBands(fg)
            if (bands.isNotEmpty()) {
                val topMarginDp = bands.first().first / density
                val bottomMarginDp = (hPx - bands.last().second) / density
                val fillRatio = (bands.last().second - bands.first().first).toFloat() / hPx.toFloat()
                if (fillRatio < 0.72f) {
                    failures.add("$tag: low vertical fill ${"%.2f".format(Locale.US, fillRatio)} < 0.72")
                }
                if (topMarginDp > maxOf(24f, profile.h * 0.09f)) {
                    failures.add("$tag: excessive top margin ${"%.1f".format(Locale.US, topMarginDp)}dp")
                }
                if (abs(topMarginDp - bottomMarginDp) > 10f) {
                    failures.add("$tag: unbalanced vertical margins top=${"%.1f".format(Locale.US, topMarginDp)}dp bottom=${"%.1f".format(Locale.US, bottomMarginDp)}dp")
                }
                var maxGapDp = 0f
                for (i in 0 until bands.size - 1) {
                    val gapDp = (bands[i + 1].first - bands[i].second) / density
                    if (gapDp > maxGapDp) maxGapDp = gapDp
                }
                val allowedGapDp = maxOf(18f, profile.h * 0.065f)
                if (maxGapDp > allowedGapDp) {
                    failures.add("$tag: dead vertical gap ${"%.1f".format(Locale.US, maxGapDp)}dp > ${"%.1f".format(Locale.US, allowedGapDp)}dp")
                }
            }
        }
    }

    private fun mergeVerticalBands(leaves: List<LeafBox>): List<Pair<Int, Int>> {
        if (leaves.isEmpty()) return emptyList()
        val sorted = leaves.sortedBy { it.top }
        val bands = mutableListOf<Pair<Int, Int>>()
        var curTop = sorted[0].top
        var curBottom = sorted[0].bottom
        for (i in 1 until sorted.size) {
            val item = sorted[i]
            if (item.top <= curBottom + 2) {
                if (item.bottom > curBottom) curBottom = item.bottom
            } else {
                bands.add(curTop to curBottom)
                curTop = item.top
                curBottom = item.bottom
            }
        }
        bands.add(curTop to curBottom)
        return bands
    }

    private fun loadWidgetProfiles(): List<Profile> {
        val candidates = listOf(
            File("../.gemini/devices/devices_and_dpi.json"),
            File(".gemini/devices/devices_and_dpi.json")
        )
        val jsonFile = candidates.firstOrNull { it.isFile }
        val result = linkedSetOf<Profile>()
        if (jsonFile != null) {
            val root = JSONObject(jsonFile.readText(Charsets.UTF_8))
            val grids = root.optJSONArray("widget_grids")
            if (grids != null) {
                for (i in 0 until grids.length()) {
                    val grid = grids.getJSONObject(i)
                    for (kind in listOf("line", "player", "cover")) {
                        val arr = grid.optJSONArray(kind) ?: continue
                        if (arr.length() >= 2) {
                            result.add(Profile(kind, arr.getInt(0), arr.getInt(1)))
                        }
                    }
                }
            }
        }
        if (result.isNotEmpty()) return result.toList()
        return fallbackWidgetProfiles()
    }

    private fun fallbackWidgetProfiles(): List<Profile> {
        val grids = listOf(
            intArrayOf(472, 157, 472, 353, 216, 353),
            intArrayOf(540, 120, 540, 250, 260, 250),
            intArrayOf(340, 88, 340, 184, 172, 228),
            intArrayOf(320, 72, 320, 160, 160, 160),
            intArrayOf(360, 90, 360, 190, 180, 190),
            intArrayOf(520, 112, 520, 240, 250, 240),
            intArrayOf(250, 64, 250, 110, 110, 110),
            intArrayOf(320, 72, 320, 150, 150, 150),
            intArrayOf(420, 96, 420, 200, 200, 200),
            intArrayOf(460, 96, 460, 190, 220, 190),
            intArrayOf(340, 64, 340, 170, 340, 170),
            intArrayOf(180, 60, 180, 110, 180, 320)
        )
        val out = linkedSetOf<Profile>()
        for (g in grids) {
            out.add(Profile("line", g[0], g[1]))
            out.add(Profile("player", g[2], g[3]))
            out.add(Profile("cover", g[4], g[5]))
        }
        return out.toList()
    }

    @Test
    fun allWidgetsFitWithoutClippingOrOverlap() {
        val profiles = loadWidgetProfiles()
        val captureTargets = setOf(
            Profile("line", 472, 157),
            Profile("line", 540, 120),
            Profile("line", 320, 72),
            Profile("line", 250, 64),
            Profile("line", 180, 60),
            Profile("player", 472, 353),
            Profile("player", 540, 250),
            Profile("player", 320, 160),
            Profile("player", 250, 110),
            Profile("player", 180, 110),
            Profile("cover", 216, 353),
            Profile("cover", 260, 250),
            Profile("cover", 172, 228),
            Profile("cover", 340, 170),
            Profile("cover", 180, 320),
            Profile("cover", 160, 160),
            Profile("cover", 150, 150),
            Profile("cover", 110, 110)
        )
        val states = listOf(
            Triple("playing", playingNow(withCover = true, isRadio = false), true),
            Triple("radio", playingNow(withCover = true, isRadio = true), false),
            Triple("nothing", emptyNow(), false),
            Triple("nocover", playingNow(withCover = false, isRadio = false), false)
        )

        val failures = mutableListOf<String>()
        for (p in profiles) {
            for ((stateName, now, doCapture) in states) {
                val expectTitle = when (p.kind) {
                    "line" -> p.w >= 220 && now.hasTrack
                    "player" -> true
                    else -> p.w >= 160 && p.h >= 160
                }
                val captureWave = stateName == "nothing" && p == Profile("player", 472, 353)
                val capture = (doCapture || captureWave) && p in captureTargets
                renderAndAudit(p, stateName, now, expectTitle, capture, failures)
            }
        }
        assertTrue("Widget audit failures (${failures.size}):\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
