package com.korenskoy.yandexmusic.presentation.hifi

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class HiFiPalette(
    val ground: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val barFill: Color,
    val scrim: Color,
    val sheetFill: Color,
    val cardFill: Color,
    val cardBorder: Color,
    val danger: Color,
    val live: Color,
    val placeholder: Color,
    val onMetal: Color,
    val anodizedLight: Color,
    val anodizedDark: Color,
    val steelLight: Color,
    val steelDark: Color,
    val steelEdge: Color,
    val paper: Color,
    val success: Color,
    val warning: Color,
    val love: Color,
    val explicitPlate: Color,
    val metalSweep: List<Color>,
    val brightMetalSweep: List<Color>,
    val knobFace: List<Color>,
    val metalEdge: List<Color>,
    val wellCenter: Color,
    val wellMid: Color,
    val wellOuter: Color,
    val pillFill: Color,
    val dialogContainer: Color,
    val dialogButtonFill: List<Color>,
    val spectrumActive: Color,
    val spectrumInactive: Color,
    val isDark: Boolean
) {
    companion object {
        val Dark = HiFiPalette(
            ground = HiFi.Ground,
            accent = HiFi.Accent,
            textPrimary = HiFi.TextPrimary,
            textSecondary = HiFi.TextSecondary,
            textTertiary = HiFi.TextTertiary,
            hairline = HiFi.Hairline,
            glassFill = HiFi.GlassFill,
            glassBorder = HiFi.GlassBorder,
            barFill = HiFi.BarFill,
            scrim = HiFi.Scrim,
            sheetFill = HiFi.SheetFill,
            cardFill = Color.White.copy(alpha = 0.07f),
            cardBorder = Color.White.copy(alpha = 0.10f),
            danger = HiFi.Danger,
            live = HiFi.Live,
            placeholder = HiFi.Placeholder,
            onMetal = HiFi.OnMetal,
            anodizedLight = HiFi.AnodizedLight,
            anodizedDark = HiFi.AnodizedDark,
            steelLight = HiFi.SteelLight,
            steelDark = HiFi.SteelDark,
            steelEdge = HiFi.SteelEdge,
            paper = HiFi.Paper,
            success = HiFi.Success,
            warning = HiFi.Warning,
            love = HiFi.Love,
            explicitPlate = HiFi.ExplicitPlate,
            metalSweep = HiFi.MetalSweep,
            brightMetalSweep = HiFi.BrightMetalSweep,
            knobFace = HiFi.KnobFace,
            metalEdge = HiFi.MetalEdge,
            wellCenter = Color(0xFF0B0D12),
            wellMid = Color(0xFF14171F),
            wellOuter = Color(0xFF1C1F2A),
            pillFill = Color.White.copy(alpha = 0.06f),
            dialogContainer = Color(0xFF141721),
            dialogButtonFill = listOf(Color(0xFF2C303B), Color(0xFF16181F)),
            spectrumActive = Color.White.copy(alpha = 0.95f),
            spectrumInactive = Color.White.copy(alpha = 0.24f),
            isDark = true
        )

        val Light = HiFiPalette(
            ground = Color(0xFFE6E8EE),
            accent = Color(0xFF181B24),
            textPrimary = Color(0xFF11131A),
            textSecondary = Color(0xFF11131A).copy(alpha = 0.68f),
            textTertiary = Color(0xFF11131A).copy(alpha = 0.54f),
            hairline = Color(0xFF11141C).copy(alpha = 0.08f),
            glassFill = Color.White.copy(alpha = 0.72f),
            glassBorder = Color(0xFF11141C).copy(alpha = 0.14f),
            barFill = Color(0xEBFFFFFF),
            scrim = Color(0x4711141C),
            sheetFill = Color(0xFFF3F5FA),
            cardFill = Color.White.copy(alpha = 0.72f),
            cardBorder = Color(0xFF11141C).copy(alpha = 0.12f),
            danger = Color(0xFFC92A2A),
            live = Color(0xFFE53935),
            placeholder = Color(0xFFD5DAE4),
            onMetal = Color(0xFF11131A),
            anodizedLight = Color(0xFFFFFFFF),
            anodizedDark = Color(0xFFE4E8F0),
            steelLight = Color(0xFFCBD2DF),
            steelDark = Color(0xFFE2E6EF),
            steelEdge = Color(0xFF9AA3B5),
            paper = Color(0xFFFFFFFF),
            success = Color(0xFF1F8A4C),
            warning = Color(0xFFC97A00),
            love = Color(0xFFE52E50),
            explicitPlate = Color(0xFF3A4152),
            metalSweep = listOf(
                Color(0xFFB8BFCC), Color(0xFFF7F8FB), Color(0xFF969EAE), Color(0xFFFFFFFF),
                Color(0xFFA9B1C0), Color(0xFFE3E7EF), Color(0xFFB8BFCC)
            ),
            brightMetalSweep = listOf(
                Color(0xFFFFFFFF), Color(0xFFE4E8F0), Color(0xFFFFFFFF), Color(0xFFD5DBE7),
                Color(0xFFFFFFFF), Color(0xFFCBD2DF), Color(0xFFFFFFFF)
            ),
            knobFace = listOf(Color(0xFFFFFFFF), Color(0xFFEAEDF4), Color(0xFFD6DCE7)),
            metalEdge = listOf(Color(0xFF9AA3B5), Color(0xFFE4E8F0), Color(0xFF8C94A6)),
            wellCenter = Color(0xFFE2E5ED),
            wellMid = Color(0xFFD5DAE4),
            wellOuter = Color(0xFFCBD1DD),
            pillFill = Color.White.copy(alpha = 0.74f),
            dialogContainer = Color(0xFFF3F5FA),
            dialogButtonFill = listOf(Color(0xFF252A36), Color(0xFF141821)),
            spectrumActive = Color(0xE611141C),
            spectrumInactive = Color(0x3811141C),
            isDark = false
        )
    }
}

val LocalHiFiPalette = staticCompositionLocalOf { HiFiPalette.Dark }

/**
 * The "Hi-Fi" design language of redesign 2.0: a ground, metal controls, monochrome silver/graphite
 * accents and a single tint taken from the cover of what is playing. Every screen takes its colors from here.
 */
object HiFi {
    val palette: HiFiPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalHiFiPalette.current
    val Ground = Color(0xFF05060A)
    val Accent = Color(0xFFE6EAF2)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.66f)
    val TextTertiary = Color.White.copy(alpha = 0.55f)
    val Hairline = Color.White.copy(alpha = 0.10f)
    val GlassFill = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.16f)
    val BarFill = Color(0xB81C1E26)
    val Scrim = Color(0xE605060A)
    val SheetFill = Color(0xFF10121A)
    val Danger = Color(0xFFFF6B6B)
    val Live = Color(0xFFFF4D4D)
    /** Behind a cover while it loads, and for a missing cover. */
    val Placeholder = Color(0xFF1C1F28)
    /** Text and icons on the knobs and metal capsules. */
    val OnMetal = Color(0xFFF4F6FA)
    /** Selected rows: dark anodized metal, lit from the top. */
    val AnodizedLight = Color(0xFF2F333E)
    val AnodizedDark = Color(0xFF16181E)
    /** Avatars and cover placeholders: brushed steel. */
    val SteelLight = Color(0xFF3A4152)
    val SteelDark = Color(0xFF1C2130)
    /** The light steel of rims and selected outlines. */
    val SteelEdge = Color(0xFFC9CED8)
    /** A white plate behind station logos drawn for light backgrounds. */
    val Paper = Color(0xFFE9ECF2)
    /** Status colours, used only for words and small marks: all is well / needs attention. */
    val Success = Color(0xFF6FD08C)
    val Warning = Color(0xFFFFB547)
    /** A liked track or a favourite station: the heart is always this red, everywhere. */
    val Love = Color(0xFFFF3B5C)

    /** The plate of the explicit "E": light grey, the letter in the ground's colour. */
    val ExplicitPlate = Color(0xFFC9CED6)

    /** The metal rim: a sweep through light and dark steel. */
    val MetalSweep = listOf(
        Color(0xFF8E94A3), Color(0xFF2A2C33), Color(0xFFC9CED8), Color(0xFF2A2C33),
        Color(0xFF7C8291), Color(0xFF1E2026), Color(0xFF8E94A3)
    )
    /** A brighter metal for small thumbs (switch, slider) that sit on dark or light tracks. */
    val BrightMetalSweep = listOf(
        Color(0xFFE4E7EE), Color(0xFF8E94A3), Color(0xFFFFFFFF), Color(0xFF9AA0AD),
        Color(0xFFD5D9E1), Color(0xFF7C8291), Color(0xFFE4E7EE)
    )
    /** Anodized black face of a knob, lit from the top. */
    val KnobFace = listOf(Color(0xFF2C303B), Color(0xFF15171D), Color(0xFF0A0B0F))
    val MetalEdge = listOf(Color(0xFFC9CED8), Color(0xFF2A2C33), Color(0xFF7C8291))
}

/** Four speeds and one curve for the whole app (board "Переходы и плавность"). */
object HiFiMotion {
    const val QUICK = 120
    const val STANDARD = 220
    const val LARGE = 350
    /** Background tint follows a new cover slowly so the change is barely noticed. */
    const val TINT = 700

    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> quick(): TweenSpec<T> = tween(QUICK, easing = Easing)
    fun <T> standard(): TweenSpec<T> = tween(STANDARD, easing = Easing)
    fun <T> large(): TweenSpec<T> = tween(LARGE, easing = Easing)
    fun <T> tint(): TweenSpec<T> = tween(TINT, easing = Easing)
    fun <T> spring(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * "Light mode": the same look without the costly parts (live blur, film grain, idle animations).
 * Chosen once per process from the device; pure so it is unit-tested.
 */
object HiFiLitePolicy {
    fun isLite(sdkInt: Int, lowRamDevice: Boolean, powerSave: Boolean, animatorScale: Float): Boolean =
        sdkInt < Build.VERSION_CODES.Q || lowRamDevice || powerSave || animatorScale == 0f

    fun fromContext(context: Context): Boolean {
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        val animatorScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return isLite(Build.VERSION.SDK_INT, lowRam, powerSave, animatorScale)
    }
}

/** True on weak devices, in battery saver and with "remove animations": drop the expensive effects. */
val LocalHiFiLite = staticCompositionLocalOf { false }

@Composable
fun rememberHiFiLite(): Boolean {
    val context = LocalContext.current
    return remember { HiFiLitePolicy.fromContext(context) }
}

/** The app-wide tint taken from the current cover (or the radio station's brand colors). */
@Immutable
data class ArtworkTint(val primary: Color, val secondary: Color) {
    companion object {
        /** Nothing playing: neutral graphite. */
        val Neutral = ArtworkTint(Color(0xFF3A4152), Color(0xFF1C2130))
    }
}

val LocalArtworkTint = compositionLocalOf { ArtworkTint.Neutral }
