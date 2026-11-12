package com.wanderwildwood.jimeikin.data

import java.net.URLDecoder

/**
 * Track numbers that are in a file's name and not in its tags: "01 Lantern", "01 - Lantern",
 * "1. Lantern", "01_Lantern", and "1-01 Lantern" for disc one, track one.
 *
 * A song with no title tag is titled by its file name, number and all. That number is the
 * song's place on its album, and the rest is its title as anyone else writes it, which is
 * what an album lookup has to compare.
 *
 * A title can begin with a number of its own ("99 Luftballons", "7 Years"), so one name is
 * never enough to tell. The album decides, see [sequence].
 */
object FilenameTracks {

    /** What a name begins with: the [track], the [disc] if the name gives one, and the [title] after them. */
    data class Leading(
        val disc: Int?,
        val track: Int,
        val title: String,
        /** Written with a zero in front ("01"), as track numbers are and titles almost never. */
        val padded: Boolean = false,
    )

    /** A song's title that came from its file name, and the disc the song is already known to be on. */
    data class Named(val name: String, val disc: Int? = null)

    // After the number: a dash, underscore or full stop with any spaces around it, or spaces
    // alone. A full stop before a digit is a decimal point ("1.21 Gigawatts").
    private const val AFTER = """(?:\s*[-_–—]\s*|\s*\.(?!\d)\s*|\s+)"""
    private val DISC_TRACK = Regex("""^(\d{1,2})-(\d{1,3})$AFTER(\S.*)$""")
    private val TRACK = Regex("""^(\d{1,3})$AFTER(\S.*)$""")

    /**
     * The number [name] begins with and the title after it, or null when it does not begin with
     * one: a name that is only a number ("1979"), a number with nothing set off after it
     * ("100% Pure", "10,000 Days", "1.21 Gigawatts", "2nd Song"), a number of four digits or more.
     * This says what the name could mean, not that it does: see [sequence].
     */
    fun parse(name: String): Leading? {
        val s = name.trim()
        DISC_TRACK.find(s)?.let { m ->
            val disc = m.groupValues[1].toInt()
            if (disc > 0) leading(disc, m.groupValues[2], m.groupValues[3])?.let { return it }
        }
        return TRACK.find(s)?.let { m -> leading(null, m.groupValues[1], m.groupValues[2]) }
    }

    private fun leading(disc: Int?, number: String, rest: String): Leading? {
        val title = rest.trim()
        if (title.none { it.isLetterOrDigit() }) return null
        return Leading(disc, number.toInt(), title, padded = number.length > 1 && number[0] == '0')
    }

    /**
     * The leading numbers of one album's file-name titles, in the order given, or null when
     * they are not track numbers. They are taken as track numbers only when the album as a
     * whole says so:
     *
     * - at least two of the album's titles came from file names, and every one of them begins
     *   with a number ([parse]): one unnumbered name and "7 Years" beside it is a title;
     * - no two of them claim the same place, disc and number together;
     * - the numbers could be this album's: none is more than twice the number of songs on the
     *   album ([albumSize]), or at least one is written with a zero in front. "99 Luftballons"
     *   and "7 Years" in a folder of two are titles; "03", "07" and "09" from an album of twelve
     *   are tracks.
     */
    fun sequence(named: List<Named>, albumSize: Int = named.size): List<Leading>? {
        if (named.size < 2) return null
        val found = named.map { parse(it.name) ?: return null }
        val places = found.mapIndexed { i, leading -> (leading.disc ?: named[i].disc) to leading.track }
        if (places.toSet().size != places.size) return null
        val fits = found.all { it.track <= 2 * maxOf(albumSize, named.size) }
        if (!fits && found.none { it.padded }) return null
        return found
    }

    /**
     * The file name a local song's title would have come from, without its ending: the last
     * part of the document id its address ends in. Null for an address that is not shaped so.
     */
    fun fileTitle(songId: String): String? {
        val last = songId.substringAfterLast('/', "")
        if (last.isEmpty()) return null
        val documentId = try {
            URLDecoder.decode(last.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            return null
        }
        val name = if ('/' in documentId) documentId.substringAfterLast('/') else documentId.substringAfterLast(':')
        return name.substringBeforeLast('.', name).takeIf { it.isNotEmpty() }
    }

    /** Whether [song]'s title is its file's name: no title tag was read for it. */
    fun titleIsFileName(song: SongEntity): Boolean = fileTitle(song.id) == song.title

    /**
     * For every song whose title is its file name on an album where those names are numbered
     * ([sequence]): the song's id to its number and its title without it. Songs with no album
     * are left out, as there is nothing to judge their names by.
     */
    fun inAlbums(songs: List<SongEntity>): Map<String, Leading> {
        val out = HashMap<String, Leading>()
        songs.filter { !it.albumId.isNullOrBlank() }.groupBy { it.albumId }.values.forEach { album ->
            val fromNames = album.filter { titleIsFileName(it) }
            val found = sequence(fromNames.map { Named(it.title, it.discNumber) }, album.size) ?: return@forEach
            fromNames.forEachIndexed { i, song -> out[song.id] = found[i] }
        }
        return out
    }

    /** [song] with the number from its file name, when its tags gave it no track number. */
    fun numbered(song: SongEntity, leading: Leading?): SongEntity {
        if (leading == null || song.trackNumber != null || leading.track <= 0) return song
        return song.copy(trackNumber = leading.track, discNumber = song.discNumber ?: leading.disc)
    }
}
