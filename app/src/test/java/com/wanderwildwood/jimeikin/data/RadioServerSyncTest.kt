package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioServerSyncTest {

    private fun urls(vararg n: Int) = n.map { "https://s.example.org/$it" }.toSet()

    @Test
    fun firstSyncOnlyAdds() {
        val p = RadioServerSync.plan(phone = urls(1, 2), server = urls(2, 3), agreed = emptySet())
        assertEquals(urls(1), p.sendUp)
        assertEquals(urls(3), p.bringDown)
        assertEquals(emptySet<String>(), p.removeHere)
        assertEquals(emptySet<String>(), p.removeThere)
    }

    @Test
    fun removalOnEitherSideIsCarriedOver() {
        // 1..6 agreed; 1 since removed on the phone, 2 removed on the server; 7 new here, 8 new there.
        val p = RadioServerSync.plan(
            phone = urls(2, 3, 4, 5, 6, 7),
            server = urls(1, 3, 4, 5, 6, 8),
            agreed = urls(1, 2, 3, 4, 5, 6),
        )
        assertEquals(urls(7), p.sendUp)
        assertEquals(urls(8), p.bringDown)
        assertEquals(urls(2), p.removeHere)
        assertEquals(urls(1), p.removeThere)
    }

    @Test
    fun aPhoneThatLostItsListRemovesNothingFromTheServer() {
        // Everything agreed, then the phone's list is all but gone: put back, not deleted there.
        val p = RadioServerSync.plan(
            phone = urls(1),
            server = urls(1, 2, 3, 4, 5, 6, 7, 8),
            agreed = urls(1, 2, 3, 4, 5, 6, 7, 8),
        )
        assertEquals(emptySet<String>(), p.removeThere)
        assertEquals(urls(2, 3, 4, 5, 6, 7, 8), p.bringDown)
    }

    @Test
    fun anEmptiedServerRemovesNothingFromThePhone() {
        val p = RadioServerSync.plan(phone = urls(1, 2, 3, 4, 5), server = emptySet(), agreed = urls(1, 2, 3, 4, 5))
        assertEquals(emptySet<String>(), p.removeHere)
        assertEquals(urls(1, 2, 3, 4, 5), p.sendUp)
    }
}
