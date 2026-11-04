package com.lightphone.audiobooks.screens

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class LightAnchoredMenuPositionProviderTest {
    private fun calculate(anchor: IntRect, size: IntSize, window: IntSize): IntOffset =
        LightAnchoredMenuPositionProvider(anchor).calculatePosition(
            anchor, window, LayoutDirection.Ltr, size,
        )

    @Test fun rightAlignedMenuAppearsBelowWhenItFits() {
        assertEquals(IntOffset(100, 136), calculate(
            IntRect(250, 100, 280, 130), IntSize(180, 120), IntSize(360, 600)))
    }

    @Test fun menuFlipsAboveWhenCloseToBottom() {
        assertEquals(IntOffset(100, 384), calculate(
            IntRect(250, 490, 280, 510), IntSize(180, 100), IntSize(360, 600)))
    }

    @Test fun menuDoesNotOverlapBottomTrigger() {
        val trigger = IntRect(250, 510, 280, 530)
        val menu = calculate(trigger, IntSize(180, 120), IntSize(360, 600))
        assertEquals(trigger.top - 6 - 120, menu.y)
    }

    @Test fun menuDoesNotCrossLeftEdge() {
        assertEquals(8, calculate(
            IntRect(2, 100, 24, 120), IntSize(200, 100), IntSize(360, 600)).x)
    }

    @Test fun menuDoesNotCrossRightEdge() {
        assertEquals(190, calculate(
            IntRect(320, 100, 350, 120), IntSize(160, 100), IntSize(360, 600)).x)
    }

    @Test fun oversizedMenuAvoidsInvalidBounds() {
        assertEquals(0, calculate(
            IntRect(20, 80, 50, 100), IntSize(220, 100), IntSize(100, 400)).x)
    }
}
