package com.wanderwildwood.jimeikin.data

import android.content.Context
import android.os.Environment
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.io.IOException

/**
 * A server song's address as the library keeps it: without the login.
 *
 * Each song row used to keep the address exactly as the player opens it, signed: a Subsonic
 * token and salt (or, for a server that takes no tokens, the password itself, hex-encoded), or
 * a Jellyfin token as `ApiKey`. That put the login, readable, into the library's database, into
 * the small file kept beside each kept song, and into a library backup written to a folder of
 * the person's choosing: everything [Secrets] exists to keep it out of. Now the row keeps the
 * address with the login taken out, and the login is put on it ([MusicServerClient.signed]) only
 * as the player or the downloader opens it, and only for an address on the server set up now,
 * so a song left over from an earlier server is never sent the current one's login.
 */
object ServerLogin {

    /** The query fields that carry a login: Subsonic's user, token, salt and password, Jellyfin's key. */
    private val LOGIN_FIELDS = setOf("u", "t", "s", "p", "apikey", "api_key")

    private const val CLEANED_PREF = "server_addresses_unsigned_v1"

    /** [url] with every login field taken out of its query; anything else as it was. */
    fun strip(url: String): String = without(url, LOGIN_FIELDS)

    /** Whether [url] carries any login field. */
    fun isSigned(url: String): Boolean = fields(url).any { it.substringBefore('=').lowercase() in LOGIN_FIELDS }

    /** [url] with the query fields named in [names] (any case) taken out. */
    internal fun without(url: String, names: Set<String>): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val kept = fields(url).filter { it.substringBefore('=').lowercase() !in names }
        return if (kept.isEmpty()) url.substring(0, q) else url.substring(0, q) + "?" + kept.joinToString("&")
    }

    /** [url] with [query] (already encoded) added to the end of its query. */
    internal fun appended(url: String, query: String): String =
        if (query.isEmpty()) url else url + (if ('?' in url) "&" else "?") + query

    private fun fields(url: String): List<String> {
        val q = url.indexOf('?')
        if (q < 0) return emptyList()
        return url.substring(q + 1).split('&').filter { it.isNotEmpty() }
    }

    /**
     * Refuses to follow a redirect from https to plain http when the address it leads to still
     * carries a login: a server behind a badly set-up proxy could otherwise bounce the token,
     * or the password, out over the network in the clear. Any other redirect is followed as
     * before; a radio station that moves from https to http carries no login and still plays.
     */
    val noPlainRedirectWithLogin: Interceptor = Interceptor { chain ->
        val now = chain.request().url
        val first = chain.call().request().url
        if (first.isHttps && !now.isHttps && isSigned(now.toString())) {
            throw IOException("refused a redirect to plain http that carries the server login")
        }
        chain.proceed(chain.request())
    }

    /**
     * Once, on an install that kept signed addresses: takes the login out of every server song's
     * row, every hearted copy of one, and every file kept beside a kept song. Nothing is lost:
     * the login is put back on as each song is opened. Run off the main thread.
     */
    fun cleanStored(context: Context) {
        val prefs = context.getSharedPreferences("calmmusic_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(CLEANED_PREF, false)) return
        try {
            val database = CalmMusicDatabase.getDatabase(context)
            database.runInTransaction {
                val db = database.openHelper.writableDatabase
                for (table in listOf("songs", "hearted_songs")) {
                    val idColumn = if (table == "songs") "id" else "songId"
                    val changed = mutableListOf<Pair<String, String>>()
                    db.query(
                        "SELECT $idColumn, audioUri FROM $table WHERE sourceType = ? AND audioUri IS NOT NULL",
                        arrayOf<Any?>(SubsonicSync.SOURCE_TYPE),
                    ).use { c ->
                        while (c.moveToNext()) {
                            val stored = c.getString(1)
                            if (isSigned(stored)) changed += c.getString(0) to strip(stored)
                        }
                    }
                    changed.forEach { (id, bare) ->
                        db.execSQL("UPDATE $table SET audioUri = ? WHERE $idColumn = ?", arrayOf<Any?>(bare, id))
                    }
                }
            }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.resolve("server")
                ?.listFiles { f: File -> f.isFile && f.name.endsWith(".stream") }
                ?.forEach { f ->
                    val stored = runCatching { f.readText() }.getOrNull() ?: return@forEach
                    if (isSigned(stored)) runCatching { f.writeText(strip(stored)) }
                }
            prefs.edit().putBoolean(CLEANED_PREF, true).apply()
        } catch (e: Exception) {
            // Tried again at the next start; meanwhile every address is signed afresh when opened.
            android.util.Log.w("ServerLogin", "could not take the login out of stored addresses", e)
        }
    }
}
