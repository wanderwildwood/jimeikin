package com.wanderwildwood.jimeikin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StationsM3uTest {

    private fun station(title: String, url: String, folder: String = "") =
        RadioStationEntity("ADDRESS:$url", title, "", "", url, 1L, folder)

    @Test
    fun writtenListReadsBackWithFolders() {
        val text = StationsM3u.write(
            listOf(
                station("Harbour Jazz", "https://stream.example.org/jazz", "Evening"),
                station("Low Field FM", "http://radio.example.net:8000/live.mp3"),
            ),
        )
        assertEquals(
            listOf(
                StationsM3u.Entry("Harbour Jazz", "https://stream.example.org/jazz", "Evening"),
                StationsM3u.Entry("Low Field FM", "http://radio.example.net:8000/live.mp3", ""),
            ),
            StationsM3u.read(text),
        )
    }

    @Test
    fun readsOtherPlayersLists() {
        val text = "﻿#EXTM3U\r\n" +
            "#EXTINF:-1 tvg-id=\"x\" group-title=\"News\",Tide Tables Radio\r\n" +
            "https://stream.example.org/news\r\n" +
            "#EXTINF:-1,Kitchen Radio\r\n" +
            "#EXTGRP:Talk\r\n" +
            "https://stream.example.org/talk\r\n" +
            "https://stream.example.org/bare\r\n" +
            "#EXTINF:123,A song file\r\n" +
            "Music/song.mp3\r\n" +
            "https://stream.example.org/news\r\n"
        assertEquals(
            listOf(
                StationsM3u.Entry("Tide Tables Radio", "https://stream.example.org/news", "News"),
                StationsM3u.Entry("Kitchen Radio", "https://stream.example.org/talk", "Talk"),
                StationsM3u.Entry("stream.example.org", "https://stream.example.org/bare", ""),
            ),
            StationsM3u.read(text),
        )
    }
}
