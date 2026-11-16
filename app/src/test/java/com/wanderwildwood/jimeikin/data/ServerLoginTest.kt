package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerLoginTest {

    @Test fun aSubsonicAddressLosesItsLoginAndKeepsTheRest() {
        val signed = "https://music.example.org/rest/stream?u=ada&t=0a1b&s=c0ffee&v=1.16.1&c=jimeikin&f=json&id=42&format=raw"
        assertEquals(
            "https://music.example.org/rest/stream?v=1.16.1&c=jimeikin&f=json&id=42&format=raw",
            ServerLogin.strip(signed),
        )
        assertTrue(ServerLogin.isSigned(signed))
        assertFalse(ServerLogin.isSigned(ServerLogin.strip(signed)))
    }

    @Test fun thePasswordFormGoesToo() {
        val signed = "http://192.168.1.10:4533/rest/stream?u=ada&p=enc%3A736563726574&v=1.12.0&id=7"
        assertEquals("http://192.168.1.10:4533/rest/stream?v=1.12.0&id=7", ServerLogin.strip(signed))
    }

    @Test fun aJellyfinKeyGoesWhateverItsCase() {
        val signed = "https://jelly.example.org/Audio/abc/universal?UserId=u1&DeviceId=d1&ApiKey=tok&MaxStreamingBitrate=96000"
        assertEquals(
            "https://jelly.example.org/Audio/abc/universal?UserId=u1&DeviceId=d1&MaxStreamingBitrate=96000",
            ServerLogin.strip(signed),
        )
        assertEquals("https://x.example.org/a", ServerLogin.strip("https://x.example.org/a?api_key=k"))
    }

    @Test fun anAddressWithNoLoginIsLeftAlone() {
        val radio = "https://radio.example.org/live.mp3?station=ada"
        assertEquals(radio, ServerLogin.strip(radio))
        assertEquals("https://radio.example.org/live.mp3", ServerLogin.strip("https://radio.example.org/live.mp3"))
    }

    @Test fun appendedAddsToAQueryOrStartsOne() {
        assertEquals("https://a.example.org/x?id=1&t=2", ServerLogin.appended("https://a.example.org/x?id=1", "t=2"))
        assertEquals("https://a.example.org/x?t=2", ServerLogin.appended("https://a.example.org/x", "t=2"))
    }
}
