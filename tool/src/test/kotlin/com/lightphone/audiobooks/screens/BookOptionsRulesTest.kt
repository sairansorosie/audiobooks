package com.lightphone.audiobooks.screens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookOptionsRulesTest {
    @Test fun `new book cannot clear progress`() {
        assertFalse(canClearListeningProgress(completed = false, positionMs = 0L))
    }

    @Test fun `listening progress enables clear`() {
        assertTrue(canClearListeningProgress(completed = false, positionMs = 1L))
    }

    @Test fun `finished book can clear even with zero position`() {
        assertTrue(canClearListeningProgress(completed = true, positionMs = 0L))
    }
}
