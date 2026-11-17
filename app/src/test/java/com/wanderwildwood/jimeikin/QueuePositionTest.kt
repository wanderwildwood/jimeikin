package com.wanderwildwood.jimeikin

import com.wanderwildwood.jimeikin.playback.QueuePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuePositionTest {

    @Test
    fun `a song queued twice keeps its later place`() {
        val queue = listOf("a", "b", "a", "c")
        assertEquals(2, QueuePosition.follow(queue, 2, "a"))
    }

    @Test
    fun `the next song is found after the current place, not at its first copy`() {
        val queue = listOf("a", "b", "c", "a")
        assertEquals(3, QueuePosition.follow(queue, 2, "a"))
    }

    @Test
    fun `a song before the current place is found when there is none after`() {
        val queue = listOf("a", "b", "c")
        assertEquals(0, QueuePosition.follow(queue, 2, "a"))
        assertNull(QueuePosition.follow(queue, 1, "z"))
    }

    @Test
    fun `restore finds the saved song after others were left out`() {
        val saved = listOf("a", "gone", "b", "c")
        assertEquals(1 to 5000L, QueuePosition.restore(saved, 2, 5000L, listOf("a", "b", "c")))
    }

    @Test
    fun `restore starts at the top when the saved song is gone`() {
        val saved = listOf("a", "gone", "b")
        assertEquals(0 to 0L, QueuePosition.restore(saved, 1, 5000L, listOf("a", "b")))
        assertEquals(0 to 0L, QueuePosition.restore(saved, null, 5000L, listOf("a", "b")))
    }

    @Test
    fun `restore takes the same copy of a song queued twice`() {
        val saved = listOf("a", "b", "a")
        assertEquals(2 to 10L, QueuePosition.restore(saved, 2, 10L, listOf("a", "b", "a")))
    }
}
