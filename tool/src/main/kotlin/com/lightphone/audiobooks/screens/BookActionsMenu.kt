package com.lightphone.audiobooks.screens
import com.lightphone.audiobooks.formatSpeed

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable

/**
 * Anchored menu coordinates are measured in window space. Compose Popup is
 * focusable (outside tap and Back dismiss), but does not dim the Library.
 * Placement stays inside the small Light Phone III viewport, flipping above
 * the trigger when there is insufficient room below.
 */
internal class LightAnchoredMenuPositionProvider(
    private val target: IntRect,
    private val alignEnd: Boolean = true,
    private val alignCenter: Boolean = false,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val edge = 8
        val rightLimit = (windowSize.width - popupContentSize.width - edge).coerceAtLeast(0)
        val leftLimit = edge.coerceAtMost(rightLimit)
        val requestedX = when {
            alignCenter -> target.left + (target.width - popupContentSize.width) / 2
            alignEnd -> target.right - popupContentSize.width
            else -> target.left
        }
        val x = requestedX.coerceIn(leftLimit, rightLimit)
        // A small, visible two-dp gap from the drawn ellipsis, not its touch box.
        val gap = 6
        val below = target.bottom + gap
        val above = target.top - popupContentSize.height - gap
        val fitsBelow = below + popupContentSize.height <= windowSize.height - edge
        val fitsAbove = above >= edge
        // Menu height is constrained at composition time for very short viewports.
        val y = when {
            fitsBelow -> below
            fitsAbove -> above
            windowSize.height - target.bottom >= target.top -> below
            else -> above
        }.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/** Space available on the larger side of a trigger, reserved for a non-overlapping popup. */
@Composable
internal fun anchoredMenuMaxHeight(anchor: IntRect): androidx.compose.ui.unit.Dp {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val above = (anchor.top - 14).coerceAtLeast(0)
    val below = (windowHeight - anchor.bottom - 14).coerceAtLeast(0)
    return with(density) { maxOf(above, below).toDp() }
}

/** Bounds in the same window coordinate space used by PopupPositionProvider. */
internal fun popupAnchorBounds(coordinates: LayoutCoordinates): IntRect {
    val rect = coordinates.boundsInWindow()
    return IntRect(
        rect.left.roundToInt(), rect.top.roundToInt(),
        rect.right.roundToInt(), rect.bottom.roundToInt(),
    )
}

/** A real checkmark, instead of the SDK ACCEPT triangle. */
@Composable
internal fun FinishedCheckmark(withCircle: Boolean = false, modifier: Modifier = Modifier.size(20.dp)) {
    val ink = LightThemeTokens.colors.content
    androidx.compose.foundation.Canvas(modifier) {
        val scale = size.minDimension / 24f
        val stroke = 2.2f * scale
        if (withCircle) {
            drawCircle(color = ink, radius = 10f * scale,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        }
        drawLine(ink, androidx.compose.ui.geometry.Offset(5f * scale, 12f * scale),
            androidx.compose.ui.geometry.Offset(10f * scale, 17f * scale), stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(ink, androidx.compose.ui.geometry.Offset(10f * scale, 17f * scale),
            androidx.compose.ui.geometry.Offset(19f * scale, 7f * scale), stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** The speed dropdown remains intentionally lightweight and anchored. */
@Composable
internal fun PlaybackSpeedMenu(
    anchorBounds: IntRect,
    selectedSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = LightAnchoredMenuPositionProvider(
            target = anchorBounds, alignCenter = true,
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 132.dp, max = 180.dp)
                .heightIn(max = anchoredMenuMaxHeight(anchorBounds))
                .border(1.dp, LightThemeTokens.colors.contentSecondary.copy(alpha = 0.28f))
                .background(LightThemeTokens.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            playbackSpeedOptions.forEach { speed ->
                SelectionEmphasisText(
                    text = formatSpeed(speed),
                    selected = speed == selectedSpeed,
                    modifier = Modifier.fillMaxWidth()
                        .lightClickable(onClickLabel = "Set playback speed to ${formatSpeed(speed)}") {
                            onSelect(speed)
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Confirmation before clearing a single book's listening state. */
@Composable
internal fun ClearProgressConfirmation(
    title: String,
    inFlight: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!inFlight) onCancel() }) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(LightThemeTokens.colors.background)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            LightText(text = "Clear listening progress?", variant = LightTextVariant.Subheading)
            LightText(
                text = "This will reset your listening position for \"$title\" to the beginning and remove its Finished status. Your audiobook files and edited book details will be kept.",
                variant = LightTextVariant.Detail,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
            error?.let {
                LightText(text = it, variant = LightTextVariant.Detail,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            LightText(
                text = "Cancel",
                variant = LightTextVariant.Copy,
                modifier = Modifier.fillMaxWidth()
                    .lightClickable(enabled = !inFlight, onClick = onCancel)
                    .padding(vertical = 12.dp),
            )
            LightText(
                text = if (inFlight) "Clearing..." else "Clear Progress",
                variant = LightTextVariant.Copy,
                modifier = Modifier.fillMaxWidth()
                    .lightClickable(enabled = !inFlight, onClick = onConfirm)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
