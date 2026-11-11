package com.lightphone.audiobooks.screens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SelectionEmphasisTextTest {
    private val normal = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Normal,
    )

    @Test fun currentSelectionIsBoldWithoutChangingMetrics() {
        val selected = emphasizedSelectionStyle(normal, selected = true)
        assertEquals(FontWeight.Bold, selected.fontWeight)
        assertEquals(normal.fontSize, selected.fontSize)
        assertEquals(normal.lineHeight, selected.lineHeight)
        assertEquals(normal.letterSpacing, selected.letterSpacing)
        assertEquals(FontWeight.Normal, normal.fontWeight)
    }

    @Test fun otherSelectionsStayExactlyAsBefore() {
        assertSame(normal, emphasizedSelectionStyle(normal, selected = false))
    }
}
