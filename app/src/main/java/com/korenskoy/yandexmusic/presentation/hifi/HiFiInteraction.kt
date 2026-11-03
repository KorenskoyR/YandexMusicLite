package com.korenskoy.yandexmusic.presentation.hifi

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * The one press reaction of the app: the element sinks to 0.97 under the finger and springs back, a
 * short haptic tick confirms the tap. No Material ripple. The scale lives in a layer, so a press never
 * recomposes or re-records the content.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hifiPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) scale.animateTo(pressedScale, tween(HiFiMotion.QUICK / 2, easing = HiFiMotion.Easing))
        else scale.animateTo(1f, HiFiMotion.spring())
    }
    val view = LocalView.current
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClick = if (onLongClick != null) {
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    longClick?.invoke()
                }
            } else null
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            click()
        }
}

/**
 * Lets the rows of a freshly loaded list fade in and rise one after another (25 ms apart). Every key
 * appears only once: rows that scroll into view later, or come back after scrolling, simply are there.
 */
@Stable
class Appearance internal constructor(private val createdAt: Long) {
    private val seen = HashSet<Any>()

    /** True only the first time [key] is shown, and only right after the list was created. */
    internal fun shouldAnimate(key: Any): Boolean =
        seen.add(key) && android.os.SystemClock.uptimeMillis() - createdAt < WINDOW_MS

    private companion object {
        const val WINDOW_MS = 600L
    }
}

@Composable
fun rememberAppearance(vararg keys: Any?): Appearance = remember(*keys) { Appearance(android.os.SystemClock.uptimeMillis()) }

/** Fade-and-rise for row [index] of a list, once per [key]; nothing at all in lite mode. */
fun Modifier.appearOnce(appearance: Appearance, key: Any, index: Int): Modifier = composed {
    val lite = LocalHiFiLite.current
    val animate = remember(key) { !lite && index < MAX_STAGGERED && appearance.shouldAnimate(key) }
    if (!animate) return@composed this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * STAGGER_MS)
        progress.animateTo(1f, tween(HiFiMotion.STANDARD, easing = HiFiMotion.Easing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12f * density
    }
}

private const val STAGGER_MS = 25L
private const val MAX_STAGGERED = 14

/**
 * Drag-to-reorder for a lazy list: the dragged row follows the finger while its neighbours swap
 * places. Works on keys, so headers or other rows around the reorderable ones do not matter.
 * [onMove] gets the key of the dragged row and the key of the row it now takes the place of.
 */
@Stable
class ReorderState internal constructor(private val listState: LazyListState) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    internal fun start(key: Any) {
        draggedKey = key
        offset = 0f
    }

    internal fun stop() {
        draggedKey = null
        offset = 0f
    }

    internal fun drag(dy: Float, canMoveTo: (Any) -> Boolean, onMove: (Any, Any) -> Unit) {
        val key = draggedKey ?: return
        offset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val center = current.offset + current.size / 2 + offset
        val target = items.firstOrNull {
            it.key != key && canMoveTo(it.key) && center.toInt() in it.offset until it.offset + it.size
        } ?: return
        onMove(key, target.key)
        // The row now sits in the target slot: keep it under the finger
        offset += current.offset - target.offset
    }
}

@Composable
fun rememberReorderState(listState: LazyListState): ReorderState = remember(listState) { ReorderState(listState) }

/** The dragged row floats above the others and follows the finger. */
fun Modifier.reorderItem(state: ReorderState, key: Any): Modifier {
    val dragged = state.draggedKey == key
    return this
        .zIndex(if (dragged) 1f else 0f)
        .graphicsLayer {
            translationY = if (state.draggedKey == key) state.offset else 0f
            val s = if (state.draggedKey == key) 1.02f else 1f
            scaleX = s
            scaleY = s
        }
}

/**
 * Where the drag starts: a handle (immediately) or, with [afterLongPress], the whole row after a long
 * press. [canMoveTo] keeps the row among its own kind (no dropping onto a header).
 */
fun Modifier.reorderHandle(
    state: ReorderState,
    key: Any,
    canMoveTo: (Any) -> Boolean = { true },
    afterLongPress: Boolean = false,
    onMove: (from: Any, to: Any) -> Unit
): Modifier = composed {
    val view = LocalView.current
    val move by rememberUpdatedState(onMove)
    val allowed by rememberUpdatedState(canMoveTo)
    pointerInput(key, afterLongPress) {
        val onStart = { _: androidx.compose.ui.geometry.Offset ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            state.start(key)
        }
        val onEnd = { state.stop() }
        if (afterLongPress) {
            detectDragGesturesAfterLongPress(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = onEnd) { change, amount ->
                change.consume()
                state.drag(amount.y, allowed, move)
            }
        } else {
            detectDragGestures(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = onEnd) { change, amount ->
                change.consume()
                state.drag(amount.y, allowed, move)
            }
        }
    }
}

/**
 * Predictive Back (Android 14+): while the back gesture is held the page shrinks a little and rounds
 * its corners, showing where Back leads; releasing calls [onBack], cancelling springs the page back.
 * On older Android a plain Back simply calls [onBack]. Read the returned progress with [predictiveBack].
 */
@Composable
fun rememberPredictiveBack(enabled: Boolean, onBack: () -> Unit): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    val back by rememberUpdatedState(onBack)
    // A page opened again starts flat; the one that just left keeps its shape while it fades out
    LaunchedEffect(enabled) { if (enabled) progress.snapTo(0f) }
    androidx.activity.compose.PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress.snapTo(it.progress) }
            back()
        } catch (e: kotlinx.coroutines.CancellationException) {
            progress.animateTo(0f, HiFiMotion.spring())
            throw e
        }
    }
    return progress
}

/** Applies the predictive-back shape: up to 8 % smaller, corners up to 28 dp. */
fun Modifier.predictiveBack(progress: Animatable<Float, *>): Modifier = graphicsLayer {
    val p = progress.value
    if (p > 0f) {
        val s = 1f - 0.08f * p
        scaleX = s
        scaleY = s
        shape = androidx.compose.foundation.shape.RoundedCornerShape((28f * p * density))
        clip = true
    }
}
