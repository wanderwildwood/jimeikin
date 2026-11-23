package com.wanderwildwood.jimeikin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Favourite stations kept the same here and on the music server's own radio list (Navidrome's
 * Radios, Subsonic's internet radio stations), so a station kept on the phone is in the server's
 * web player and desktop apps too, and one added there turns up here.
 *
 * Stations are matched by stream address. What both sides held at the last sync is remembered
 * ([agreed]), which is what tells "added here" from "removed there": a station only one side has
 * is new if it was not agreed, and was removed on the other side if it was. Folders stay on the
 * phone; the server's list has none, and stations that come from it arrive in none.
 *
 * Only a Subsonic-kind server has such a list; with Jellyfin or no server this does nothing.
 * Navidrome lets only its admin change the list: then the server's stations still come to the
 * phone, and nothing goes back.
 */
object RadioServerSync {

    sealed class Outcome {
        object NoServer : Outcome()
        data class Synced(val readOnly: Boolean) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    private const val PREFS = "radio_sync"
    private val lock = Mutex()

    /**
     * When more than this share of the server's stations would be removed in one go, none are:
     * that looks like a phone that lost its list, not a person who removed half their stations.
     */
    private const val MOST_REMOVED_AT_ONCE = 0.5

    suspend fun sync(context: Context): Outcome = lock.withLock {
        val app = context.applicationContext
        val settings = (app as com.wanderwildwood.jimeikin.CalmMusic).settingsManager
        val config = settings.subsonicConfig.value ?: return Outcome.NoServer
        val client = settings.clientFor(config, app.resources) as? SubsonicClient ?: return Outcome.NoServer
        val dao = CalmMusicDatabase.getDatabase(app).radioStationDao()
        val key = config.normalisedBaseUrl + "\n" + config.user.trim()

        val server = when (val r = client.fetchRadioStations()) {
            is SubsonicResult.Failure -> return Outcome.Failed(r.message)
            is SubsonicResult.Success -> r.value.associateBy { it.streamUrl.trim() }
        }
        val phone = withContext(Dispatchers.IO) { dao.getAll() }.associateBy { it.streamUrl.trim() }
        val plan = plan(phone.keys, server.keys, readAgreed(app, key))
        val removedThere = plan.removeHere
        val removedHere = plan.removeThere

        val nowAgreed = (phone.keys intersect server.keys).toMutableSet()
        var readOnly = false

        // New on the phone: onto the server's list.
        for (url in plan.sendUp) {
            val s = phone.getValue(url)
            when (val r = client.createRadioStation(s.title, url)) {
                is SubsonicResult.Success -> nowAgreed += url
                is SubsonicResult.Failure -> if (r.code == 50) readOnly = true else return Outcome.Failed(r.message)
            }
        }
        // New on the server: onto the phone, in no folder.
        val arriving = plan.bringDown.map { url ->
            val s = server.getValue(url)
            RadioStationEntity(
                id = "ADDRESS:$url",
                title = s.name,
                place = Uri.parse(url).host.orEmpty(),
                country = "",
                streamUrl = url,
                keptAtMillis = System.currentTimeMillis(),
            )
        }
        withContext(Dispatchers.IO) { arriving.forEach { dao.keep(it) } }
        nowAgreed += arriving.map { it.streamUrl }

        // Removed on the server since the last sync: off the phone too.
        withContext(Dispatchers.IO) { removedThere.forEach { dao.forget(phone.getValue(it).id) } }
        // Removed on the phone: off the server, if it lets us. If not, it stays agreed, so it
        // is not brought back to the phone it was removed from.
        for (url in removedHere) {
            when (val r = client.deleteRadioStation(server.getValue(url).id)) {
                is SubsonicResult.Success -> Unit
                is SubsonicResult.Failure -> {
                    if (r.code == 50) readOnly = true
                    nowAgreed += url
                }
            }
        }

        writeAgreed(app, key, nowAgreed)
        Outcome.Synced(readOnly)
    }

    /** What one sync does, by stream address. */
    data class Plan(
        /** On the phone only, and new: added to the server. */
        val sendUp: Set<String>,
        /** On the server only, and new: added to the phone. */
        val bringDown: Set<String>,
        /** On the phone only, but agreed last time, so removed on the server: removed here. */
        val removeHere: Set<String>,
        /** On the server only, but agreed last time, so removed on the phone: removed there. */
        val removeThere: Set<String>,
    )

    /**
     * Decides a sync from the two lists and what both held last time ([agreed]). A side that
     * has lost most of what was agreed is not trusted to remove anything: the sync then goes
     * as a first one does, and only adds.
     */
    fun plan(phone: Set<String>, server: Set<String>, agreed: Set<String>): Plan {
        val onlyHere = phone - server
        val onlyThere = server - phone
        val trusted = onlyThere.count { it in agreed } <= maxOf(2, (server.size * MOST_REMOVED_AT_ONCE).toInt()) &&
            onlyHere.count { it in agreed } <= maxOf(2, (phone.size * MOST_REMOVED_AT_ONCE).toInt())
        val known = if (trusted) agreed else emptySet()
        return Plan(
            sendUp = onlyHere.filterNot { it in known }.toSet(),
            bringDown = onlyThere.filterNot { it in known }.toSet(),
            removeHere = onlyHere.filter { it in known }.toSet(),
            removeThere = onlyThere.filter { it in known }.toSet(),
        )
    }

    /**
     * Forgets what was agreed, so the next sync only adds. Called when the phone's list is
     * replaced (a restore) or the server is forgotten: a station missing then was not removed.
     */
    fun forget(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun readAgreed(context: Context, key: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(key, emptySet()).orEmpty().toSet()

    private fun writeAgreed(context: Context, key: String, urls: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(key, urls.toSet()).apply()
    }
}
