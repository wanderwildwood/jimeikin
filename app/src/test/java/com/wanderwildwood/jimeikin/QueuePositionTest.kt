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

    @Test
    fun `the order from before a shuffle comes back from its saved ids`() {
        val shuffled = listOf("c", "a", "b", "a")
        assertEquals(listOf("a", "b", "c", "a"), QueuePosition.original(listOf("a", "b", "c", "a"), shuffled) { it })
    }

    @Test
    fun `a song gone from the library is left out of the saved order too`() {
        assertEquals(listOf("a", "b", "c"), QueuePosition.original(listOf("a", "b", "c", "d"), listOf("c", "a", "b")) { it })
    }

    @Test
    fun `no saved order, or one that does not hold the same songs, gives none`() {
        val shuffled = listOf("c", "a", "b")
        assertEquals(emptyList<String>(), QueuePosition.original(emptyList(), shuffled) { it })
        assertEquals(emptyList<String>(), QueuePosition.original(listOf("a", "b"), shuffled) { it })
        assertEquals(emptyList<String>(), QueuePosition.original(listOf("a", "b", "c", "c"), shuffled) { it })
    }
}
