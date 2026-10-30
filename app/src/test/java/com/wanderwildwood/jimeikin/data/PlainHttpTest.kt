package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainHttpTest {

    @Test
    fun httpsAndSchemelessAreNeverWarnedAbout() {
        assertFalse(PlainHttp.onOpenNetwork("https://music.example.org"))
        assertFalse(PlainHttp.onOpenNetwork("music.example.org"))
        assertFalse(PlainHttp.onOpenNetwork(""))
    }

    @Test
    fun plainHttpAtHomeIsOrdinary() {
        for (a in listOf(
            "http://192.168.1.10:4533", "http://10.0.0.5", "http://172.16.0.2/x", "http://172.31.255.1",
            "http://127.0.0.1:4533", "http://localhost:4533", "http://navidrome", "http://nas.local:4533",
            "http://pi.lan", "http://100.97.232.53:4533", "http://[fd12::1]:4533", "http://user:pw@192.168.0.2/",
            "HTTP://192.168.1.10",
        )) assertFalse(a, PlainHttp.onOpenNetwork(a))
    }

    @Test
    fun plainHttpElsewhereIsWarnedAbout() {
        for (a in listOf(
            "http://music.example.org", "http://8.8.8.8", "http://172.32.0.1", "http://172.15.0.1",
            "http://192.169.1.1", "http://100.128.0.1", "http://stream.example.com:8000/live", "http://[2001:db8::1]/",
        )) assertTrue(a, PlainHttp.onOpenNetwork(a))
    }

    @Test
    fun addressesWithoutAScheme() {
        assertFalse(PlainHttp.hasScheme("192.168.1.10:4533"))
        assertTrue(PlainHttp.hasScheme(" http://x"))
        assertEquals("https://music.example.org", SubsonicConfig("music.example.org/", "u", "p").normalisedBaseUrl)
        assertEquals("http://192.168.1.10:4533", SubsonicConfig("http://192.168.1.10:4533", "u", "p").normalisedBaseUrl)
        assertEquals("https://stream.example.com/live", RadioAddress.normalise("stream.example.com/live"))
        assertEquals("http://stream.example.com/live", RadioAddress.normalise("http://stream.example.com/live"))
    }
}
