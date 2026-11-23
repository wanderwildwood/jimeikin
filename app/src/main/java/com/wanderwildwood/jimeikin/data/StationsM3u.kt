package com.wanderwildwood.jimeikin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Favourite stations as an .m3u file, the list every player reads: VLC, foobar2000, a desktop
 * Navidrome client, another phone. Each station is an #EXTINF line with its name and, when it
 * is in one, its folder as `group-title` (what IPTV and radio lists use for groups), then its
 * stream address.
 *
 * Opening one adds the stations not already kept, by stream address, in the folders it names.
 * Nothing kept is changed or removed: it is an import, not a restore.
 */
object StationsM3u {

    data class Entry(val title: String, val streamUrl: String, val folder: String)

    /** Larger than any list of stations; a bigger file is not one. */
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun write(stations: List<RadioStationEntity>): String = buildString {
        append("#EXTM3U\n")
        for (s in stations) {
            append("#EXTINF:-1")
            if (s.folder.isNotBlank()) append(" group-title=\"").append(s.folder.replace("\"", "'")).append('"')
            append(',').append(s.title.replace('\n', ' ')).append('\n')
            append(s.streamUrl).append('\n')
        }
    }

    fun read(text: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var title: String? = null
        var folder = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    title = line.substringAfter(',', "").trim().ifBlank { null }
                    folder = GROUP.find(line.substringBefore(','))?.groupValues?.get(1)?.trim().orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> folder = line.substringAfter(':').trim()
                line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> {
                    out += Entry(title ?: runCatching { java.net.URI(line).host }.getOrNull() ?: line, line, folder)
                    title = null
                    folder = ""
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                // A path to a file is a song list, not a station list; it is passed over.
                else -> { title = null; folder = "" }
            }
        }
        return out.distinctBy { it.streamUrl }
    }

    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val stations = CalmMusicDatabase.getDatabase(context).radioStationDao().getAll()
                .sortedWith(compareBy({ it.folder.lowercase() }, { it.title.lowercase() }))
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(write(stations).toByteArray(Charsets.UTF_8)) }
                ?: error("no stream")
        }.isSuccess
    }

    /** How many stations were added; null when the file could not be read or held none. */
    suspend fun open(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (out.size() <= MAX_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                if (out.size() > MAX_BYTES) null else out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull() ?: return@withContext null
        val entries = read(text)
        if (entries.isEmpty()) return@withContext null
        val dao = CalmMusicDatabase.getDatabase(context).radioStationDao()
        val kept = dao.getAll().map { it.streamUrl.trim() }.toSet()
        val now = System.currentTimeMillis()
        val fresh = entries.filter { it.streamUrl !in kept }
        fresh.forEachIndexed { i, e ->
            dao.keep(
                RadioStationEntity(
                    id = "ADDRESS:${e.streamUrl}",
                    title = e.title,
                    place = Uri.parse(e.streamUrl).host.orEmpty(),
                    country = "",
                    streamUrl = e.streamUrl,
                    // In the file's order: the first ends up at the top, as it was there.
                    keptAtMillis = now - i,
                    folder = e.folder,
                ),
            )
        }
        fresh.size
    }

    private val GROUP = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
}
