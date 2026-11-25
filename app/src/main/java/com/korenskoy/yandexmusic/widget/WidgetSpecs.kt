package com.korenskoy.yandexmusic.widget

/**
 * Pure geometry and visibility specs for the three widgets ([LineSpec], [PlayerSpec], [WaveInviteSpec], [CoverSpec]).
 * Independent of Android UI types so every size/fontScale combination can be unit-tested exhaustively.
 */
internal fun widgetLineDp(sp: Float, fontScale: Float): Float = sp * fontScale.coerceIn(0.8f, 2.0f) * 1.3f

internal data class LineSpec(
    val padH: Float,
    val padV: Float,
    val cardH: Float,
    val knob: Float,
    val chip: Float,
    val cover: Float,
    val coverGap: Float,
    val gap: Float,
    val showCover: Boolean,
    val showHeart: Boolean,
    val showPrev: Boolean,
    val showNext: Boolean,
    val showText: Boolean,
    val showArtist: Boolean,
    val showProgress: Boolean,
    val textW: Float
)

internal data class PlayerSpec(
    val pad: Float,
    val sectionGap: Float,
    val knob: Float,
    val chip: Float,
    val react: Float,
    val rowGap: Float,
    val showDislike: Boolean,
    val showLike: Boolean,
    val headerLike: Boolean,
    val showBar: Boolean,
    val showTimes: Boolean,
    val titleLines: Int,
    val titleSp: Float,
    val artistSp: Float,
    val sourceSp: Float,
    val showArtist: Boolean,
    val showSource: Boolean,
    val showCover: Boolean,
    val cover: Float,
    val coverRadius: Float
)

internal data class WaveInviteSpec(
    val pad: Float,
    val knob: Float,
    val gap: Float,
    val textColW: Float,
    val showChips: Boolean,
    val hintLines: Int
)

internal enum class CoverMode { EMPTY, SQUARE, TALL, WIDE }

internal data class CoverSpec(
    val mode: CoverMode,
    val pad: Float,
    val artSide: Float,
    val artGap: Float,
    val artCorner: Float,
    val blockGap: Float,
    val prev: Float,
    val knob: Float,
    val next: Float,
    val gap: Float,
    val titleSp: Float,
    val artistSp: Float,
    val titleLines: Int,
    val showText: Boolean,
    val showTitleWhenEmpty: Boolean,
    val noteSize: Float
)

private data class ButtonTier(val chip: Float, val knob: Float, val react: Float, val gap: Float)

private val PLAYER_TIER_LARGE = ButtonTier(chip = 52f, knob = 64f, react = 46f, gap = 10f)
private val PLAYER_TIER_MEDIUM = ButtonTier(chip = 44f, knob = 52f, react = 40f, gap = 8f)
private val PLAYER_TIER_SMALL = ButtonTier(chip = 42f, knob = 48f, react = 38f, gap = 6f)
private val PLAYER_TIER_COMPACT = ButtonTier(chip = 38f, knob = 44f, react = 36f, gap = 6f)

private val PLAYER_TIERS = listOf(PLAYER_TIER_MEDIUM, PLAYER_TIER_SMALL, PLAYER_TIER_COMPACT)

private data class CoverStage(val prev: Float, val knob: Float, val next: Float, val gap: Float)

private val COVER_LARGE = CoverStage(prev = 44f, knob = 54f, next = 44f, gap = 8f)
private val COVER_MEDIUM = CoverStage(prev = 36f, knob = 44f, next = 36f, gap = 6f)
private val COVER_COMPACT = CoverStage(prev = 32f, knob = 42f, next = 32f, gap = 4f)
private val COVER_SOLO = CoverStage(prev = 0f, knob = 48f, next = 0f, gap = 0f)

private val COVER_STAGES = listOf(COVER_LARGE, COVER_MEDIUM, COVER_COMPACT, COVER_SOLO)

internal fun lineSpec(w: Float, h: Float, fontScale: Float, hasTrack: Boolean, canReact: Boolean): LineSpec {
    val cardH = minOf(h, 96f)
    val padV = when {
        cardH >= 80f -> 10f
        cardH >= 58f -> 8f
        else -> 5f
    }
    val padH = if (cardH >= 80f) 12f else 10f
    val inner = (cardH - 2f * padV).coerceAtLeast(28f)
    val avail = (w - 2f * padH).coerceAtLeast(36f)
    val coverGap = if (w >= 340f) 12f else 10f
    val gap = if (w >= 340f) 6f else 4f

    if (!hasTrack) {
        val knob = (inner * 0.72f).coerceIn(36f, 50f).coerceAtMost(avail)
        val textAvail = (avail - knob - 8f).coerceAtLeast(0f)
        val showText = avail - knob - 8f >= 64f
        val showArtist = showText && widgetLineDp(15f, fontScale) + widgetLineDp(12f, fontScale) <= inner
        return LineSpec(
            padH = padH, padV = padV, cardH = cardH, knob = knob, chip = 34f, cover = 0f,
            coverGap = coverGap, gap = gap, showCover = false, showHeart = false,
            showPrev = false, showNext = false, showText = showText, showArtist = showArtist,
            showProgress = false, textW = textAvail
        )
    }

    var knob = (inner * 0.72f).coerceIn(36f, 50f)
    var chip = (knob - 8f).coerceIn(32f, 42f)
    var cover = inner.coerceIn(36f, 76f)
    var showHeart = canReact
    var showCover = true
    var showPrev = true

    fun fixedW(): Float {
        val coverPart = if (showCover) cover + coverGap else 0f
        val heartPart = if (showHeart) chip + gap else 0f
        val prevPart = if (showPrev) chip + gap else 0f
        return coverPart + 6f + heartPart + prevPart + knob + gap + chip
    }

    val minTitleBudget = when {
        w >= 300f -> maxOf(96f, w * 0.29f)
        w >= 260f -> 84f
        else -> 64f
    }
    if (showHeart && avail - fixedW() < 122f && cover > 56f) {
        cover = 56f
    }
    if (showHeart && avail - fixedW() < 122f) showHeart = false
    if (avail - fixedW() < minTitleBudget && cover > 48f) {
        cover = 48f
    }
    if (avail - fixedW() < minTitleBudget) showCover = false
    if (avail - fixedW() < 64f) showPrev = false
    if (avail - fixedW() < 64f) {
        knob = 36f
        chip = 32f
    }

    val textW = (avail - fixedW()).coerceAtLeast(0f)
    val titleH = widgetLineDp(15f, fontScale)
    val artistH = widgetLineDp(12f, fontScale)
    val showProgress = titleH + artistH + 9f <= inner
    val showArtist = showProgress || titleH + artistH <= inner
    return LineSpec(
        padH = padH, padV = padV, cardH = cardH, knob = knob, chip = chip, cover = cover,
        coverGap = coverGap, gap = gap, showCover = showCover, showHeart = showHeart,
        showPrev = showPrev, showNext = true, showText = textW >= 32f,
        showArtist = showArtist, showProgress = showProgress, textW = textW
    )
}

internal fun playerSpec(w: Float, h: Float, fontScale: Float, canReact: Boolean, isRadio: Boolean): PlayerSpec {
    val largeCanvas = h >= 300f && w >= 400f
    val pad = when {
        largeCanvas -> 20f
        h >= 145f && w >= 250f -> 14f
        else -> 10f
    }
    val sectionGap = when {
        largeCanvas -> 14f
        h >= 170f -> 10f
        else -> 6f
    }
    val innerW = (w - 2f * pad).coerceAtLeast(60f)
    val innerH = (h - 2f * pad).coerceAtLeast(40f)
    val allowFive = canReact && !isRadio
    val buttons = pickPlayerButtons(innerW, h, canReact, allowFive)

    val titleSp = when {
        h >= 250f && w >= 380f -> 22f
        h >= 175f && w >= 280f -> 17f
        else -> 15f
    }
    val artistSp = when {
        h >= 250f && w >= 380f -> 15f
        h >= 175f && w >= 280f -> 13f
        else -> 12f
    }
    val sourceSp = when {
        h >= 250f && w >= 380f -> 12f
        h >= 175f && w >= 280f -> 11f
        else -> 10f
    }

    var showBar = !isRadio
    var showTimes = !isRadio && innerH >= 144f
    fun calcTop(): Float {
        val barTopGap = if (showBar) 4f else 0f
        val barH = if (showBar) 4f else 0f
        val timesH = if (showBar && showTimes) widgetLineDp(11f, fontScale) else 0f
        return innerH - buttons.knob - sectionGap - barTopGap - barH - timesH
    }

    var top = calcTop()
    val titleOne = widgetLineDp(titleSp, fontScale)
    val artistH = widgetLineDp(artistSp, fontScale)
    val srcH = widgetLineDp(sourceSp, fontScale)
    val minTextH = titleOne + artistH
    if (top < minTextH && showTimes) {
        showTimes = false
        top = calcTop()
    }
    if (top < minTextH && showBar) {
        showBar = false
        top = calcTop()
    }

    val showArtist = titleOne + artistH <= top
    val baseText = titleOne + if (showArtist) artistH else 0f
    val showSource = baseText + srcH <= top
    val usedWithSource = baseText + if (showSource) srcH else 0f
    val titleLines = if (usedWithSource + titleOne <= top) 2 else 1
    val showCover = w >= 240f && top >= 48f
    val cover = if (showCover) minOf(top, 0.52f * innerW, 232f).coerceAtLeast(44f) else 0f
    val coverRadius = when {
        cover >= 140f -> 18f
        cover >= 80f -> 14f
        else -> 12f
    }
    return PlayerSpec(
        pad = pad, sectionGap = sectionGap,
        knob = buttons.knob, chip = buttons.chip, react = buttons.react, rowGap = buttons.gap,
        showDislike = buttons.showDislike, showLike = buttons.showLike, headerLike = buttons.headerLike,
        showBar = showBar, showTimes = showTimes, titleLines = titleLines,
        titleSp = titleSp, artistSp = artistSp, sourceSp = sourceSp,
        showArtist = showArtist, showSource = showSource, showCover = showCover,
        cover = cover, coverRadius = coverRadius
    )
}

private data class ChosenPlayerButtons(
    val knob: Float,
    val chip: Float,
    val react: Float,
    val gap: Float,
    val showDislike: Boolean,
    val showLike: Boolean,
    val headerLike: Boolean
)

private fun fiveButtonWidth(tier: ButtonTier): Float =
    tier.knob + 2f * tier.chip + 2f * tier.react + 4f * tier.gap

private fun threeButtonWidth(tier: ButtonTier): Float =
    tier.knob + 2f * tier.chip + 2f * tier.gap

private fun basePlayerTierFor(h: Float, innerW: Float): ButtonTier {
    if (h >= 260f && innerW >= 360f) return PLAYER_TIER_LARGE
    return when {
        h >= 175f -> PLAYER_TIER_MEDIUM
        h >= 145f -> PLAYER_TIER_SMALL
        else -> PLAYER_TIER_COMPACT
    }
}

private fun pickPlayerButtons(innerW: Float, h: Float, canReact: Boolean, allowFive: Boolean): ChosenPlayerButtons {
    val preferred = basePlayerTierFor(h, innerW)
    val candidates = listOf(preferred) + PLAYER_TIERS
    if (allowFive && innerW >= 248f) {
        val fitFive = candidates.firstOrNull { fiveButtonWidth(it) <= innerW }
        if (fitFive != null) {
            return ChosenPlayerButtons(
                knob = fitFive.knob, chip = fitFive.chip, react = fitFive.react, gap = fitFive.gap,
                showDislike = true, showLike = true, headerLike = false
            )
        }
    }
    val fitThree = candidates.firstOrNull { threeButtonWidth(it) <= innerW } ?: PLAYER_TIER_COMPACT
    val showHeaderHeart = canReact && innerW >= 180f
    return ChosenPlayerButtons(
        knob = fitThree.knob, chip = fitThree.chip, react = fitThree.react, gap = fitThree.gap,
        showDislike = false, showLike = false, headerLike = showHeaderHeart
    )
}

internal fun inviteSpec(w: Float, h: Float, fontScale: Float): WaveInviteSpec {
    val pad = if (h >= 240f && w >= 360f) 20f else if (h >= 160f && w >= 260f) 16f else 12f
    val innerW = (w - 2f * pad).coerceAtLeast(60f)
    val innerH = (h - 2f * pad).coerceAtLeast(40f)
    val showChips = innerW >= 240f * fontScale && innerH >= 116f
    val knobMaxH = if (showChips) (innerH - 54f).coerceAtLeast(48f) else innerH
    val knob = knobMaxH.coerceIn(48f, if (showChips) 72f else 88f).coerceAtMost((innerW - 76f).coerceAtLeast(44f))
    val gap = if (innerW >= 240f) 18f else 10f
    val textColW = (innerW - knob - gap).coerceAtLeast(0f)
    val textNeed = widgetLineDp(20f, fontScale) + widgetLineDp(13f, fontScale) * 2f
    val hintLines = if (textNeed <= knobMaxH) 2 else 1
    return WaveInviteSpec(pad = pad, knob = knob, gap = gap, textColW = textColW, showChips = showChips, hintLines = hintLines)
}

private fun pickCoverButtons(innerW: Float, maxKnob: Float = 60f): CoverStage =
    COVER_STAGES.firstOrNull { stage ->
        val count = (if (stage.prev > 0f) 1 else 0) + 1 + (if (stage.next > 0f) 1 else 0)
        val fitsW = stage.prev + stage.knob + stage.next + (count - 1) * stage.gap <= innerW
        fitsW && stage.knob <= maxKnob
    } ?: COVER_SOLO

internal fun coverSpec(w: Float, h: Float, fontScale: Float, hasTrack: Boolean): CoverSpec {
    val minSide = minOf(w, h)
    val pad = if (minSide >= 150f) 12f else 8f
    if (!hasTrack) {
        val innerW = (w - 2f * pad).coerceAtLeast(40f)
        val knob = (minSide * 0.45f).coerceIn(48f, 88f)
        val needH = knob + 10f + widgetLineDp(15f, fontScale) + 2f * pad
        val showTitle = needH <= h && innerW >= 70f
        return CoverSpec(
            mode = CoverMode.EMPTY, pad = pad, artSide = 0f, artGap = 0f, artCorner = 18f, blockGap = 10f,
            prev = 0f, knob = knob, next = 0f, gap = 0f,
            titleSp = 15f, artistSp = 12f, titleLines = 1,
            showText = false, showTitleWhenEmpty = showTitle, noteSize = 36f
        )
    }

    val aspect = w / h.coerceAtLeast(1f)
    if (aspect < 0.85f) return tallCoverSpec(w, h, fontScale)
    if (aspect > 1.18f) return wideCoverSpec(w, h, fontScale)

    val innerW = (w - 2f * pad).coerceAtLeast(40f)
    val chosen = pickCoverButtons(innerW)
    val titleSp = if (minSide >= 220f) 16f else 15f
    val artistSp = if (minSide >= 220f) 13f else 12f
    val scrimNeed = 20f + widgetLineDp(titleSp, fontScale) + widgetLineDp(artistSp, fontScale) + 8f + chosen.knob + pad
    val enoughHeight = h - scrimNeed >= minOf(44f, h * 0.25f)
    val showText = h >= 150f && w >= 130f && enoughHeight
    val noteSize = (minSide * 0.30f).coerceIn(28f, 64f)
    return CoverSpec(
        mode = CoverMode.SQUARE, pad = pad, artSide = minSide, artGap = 0f, artCorner = 28f, blockGap = 8f,
        prev = chosen.prev, knob = chosen.knob, next = chosen.next, gap = chosen.gap,
        titleSp = titleSp, artistSp = artistSp, titleLines = 1,
        showText = showText, showTitleWhenEmpty = false, noteSize = noteSize
    )
}

private fun tallCoverSpec(w: Float, h: Float, fontScale: Float): CoverSpec {
    val pad = when {
        w >= 200f && h >= 260f -> 14f
        w >= 175f && h >= 240f -> 12f
        else -> 10f
    }
    val innerW = (w - 2f * pad).coerceAtLeast(40f)
    val innerH = (h - 2f * pad).coerceAtLeast(60f)
    val maxKnob = if (h < 240f) 42f else 60f
    val chosen = pickCoverButtons(innerW, maxKnob)

    val titleSp = when {
        h < 250f -> 14f
        w >= 200f -> 16f
        else -> 15f
    }
    val artistSp = when {
        h < 250f -> 11f
        w >= 200f -> 13f
        else -> 12f
    }
    val blockGap = if (h >= 250f) 12f else 6f
    val titleOne = widgetLineDp(titleSp, fontScale)
    val artistH = widgetLineDp(artistSp, fontScale)

    val baseFooter = blockGap + chosen.knob
    val footerOne = baseFooter + titleOne + artistH + blockGap
    val showText = w >= 130f && h >= 150f && (innerH - footerOne) >= minOf(w * 0.65f, 72f)
    val footerTwo = baseFooter + titleOne * 2f + artistH + blockGap
    val fitsTwoLines = showText && h >= 250f && (innerW + footerTwo) <= innerH + 1f
    val titleLines = if (fitsTwoLines) 2 else 1
    val usedFooter = if (showText) (if (fitsTwoLines) footerTwo else footerOne) else baseFooter
    val artSide = minOf(innerW, (innerH - usedFooter).coerceAtLeast(40f))
    val artCorner = (28f - pad).coerceIn(14f, 18f)
    val noteSize = (artSide * 0.42f).coerceIn(24f, 64f)
    return CoverSpec(
        mode = CoverMode.TALL, pad = pad, artSide = artSide, artGap = 0f, artCorner = artCorner, blockGap = blockGap,
        prev = chosen.prev, knob = chosen.knob, next = chosen.next, gap = chosen.gap,
        titleSp = titleSp, artistSp = artistSp, titleLines = titleLines,
        showText = showText, showTitleWhenEmpty = false, noteSize = noteSize
    )
}

private fun wideCoverSpec(w: Float, h: Float, fontScale: Float): CoverSpec {
    val minSide = minOf(w, h)
    val pad = if (minSide >= 160f) 14f else 10f
    val innerW = (w - 2f * pad).coerceAtLeast(60f)
    val maxArtH = (h - 2f * pad).coerceAtLeast(44f)
    val artGap = if (w >= 240f) 14f else 8f
    val artSide = minOf(maxArtH, (innerW * 0.44f)).coerceAtLeast(36f)
    val rightW = (innerW - artSide - artGap).coerceAtLeast(42f)
    val chosenRaw = pickCoverButtons(rightW)
    val chosen = if (chosenRaw == COVER_SOLO && rightW < chosenRaw.knob) {
        COVER_SOLO.copy(knob = rightW.coerceAtLeast(42f))
    } else {
        chosenRaw
    }

    val titleSp = if (h >= 165f) 16f else 14f
    val artistSp = if (h >= 165f) 13f else 11f
    val blockGap = if (h >= 165f) 12f else 6f
    val titleOne = widgetLineDp(titleSp, fontScale)
    val artistH = widgetLineDp(artistSp, fontScale)

    val oneLineNeed = titleOne + artistH + blockGap + chosen.knob
    val twoLineNeed = titleOne * 2f + artistH + blockGap + chosen.knob
    val showText = rightW >= 88f && w >= 130f && h >= 140f && oneLineNeed <= maxArtH
    val titleLines = if (showText && twoLineNeed <= maxArtH) 2 else 1
    val artCorner = (28f - pad).coerceIn(14f, 18f)
    val noteSize = (artSide * 0.42f).coerceIn(24f, 64f)
    return CoverSpec(
        mode = CoverMode.WIDE, pad = pad, artSide = artSide, artGap = artGap, artCorner = artCorner, blockGap = blockGap,
        prev = chosen.prev, knob = chosen.knob, next = chosen.next, gap = chosen.gap,
        titleSp = titleSp, artistSp = artistSp, titleLines = titleLines,
        showText = showText, showTitleWhenEmpty = false, noteSize = noteSize
    )
}
