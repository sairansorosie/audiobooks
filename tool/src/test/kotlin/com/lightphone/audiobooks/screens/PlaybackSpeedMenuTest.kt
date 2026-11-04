package com.lightphone.audiobooks.screens

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.lightphone.audiobooks.formatSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSpeedMenuTest {
    @Test fun speedChoicesIncludeOnePointSevenFiveInOrder() {
        assertEquals(listOf(0.5f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
            playbackSpeedOptions)
    }

    @Test fun speedLabelIsAccurate() {
        assertEquals("1.75x", formatSpeed(1.75f))
    }

    @Test fun speedDropdownCentersAboveTriggerNearBottom() {
        val anchor = IntRect(144, 352, 216, 376)
        val result = LightAnchoredMenuPositionProvider(anchor, alignCenter = true)
            .calculatePosition(anchor, IntSize(360, 414), LayoutDirection.Ltr, IntSize(160, 264))
        assertEquals(IntOffset(100, 82), result)
    }

    @Test fun speedDropdownDoesNotCrossScreenEdges() {
        val anchor = IntRect(10, 310, 42, 336)
        val result = LightAnchoredMenuPositionProvider(anchor, alignCenter = true)
            .calculatePosition(anchor, IntSize(360, 414), LayoutDirection.Ltr, IntSize(180, 200))
        assertEquals(8, result.x)
    }
}
