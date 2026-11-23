package com.wanderwildwood.jimeikin.playback

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The equalizer: the phone's own (android.media.audiofx), laid over what the player plays.
 *
 * Nothing is bundled. Every phone this app is for ships Android's five-band equalizer and its
 * presets, and using it costs no battery a software one would. A phone without one simply has
 * no Sound row ([describe] is null).
 *
 * What was chosen lives in its own small preferences file and is applied wherever the player
 * is: the playback service [attach]es the player's audio session, and the Sound screen's
 * changes reach the attached equalizer at once, so they are heard while the music plays.
 */
object SoundShaping {

    private const val TAG = "SoundShaping"
    private const val PREFS = "sound"
    private const val KEY_CHOICE = "eq_choice"
    private const val KEY_OWN = "eq_own"

    /** One millibel step of the − and + on a band: 3 dB, eleven places across the usual ±15. */
    const val STEP_MB: Int = 300

    /** What the equalizer is set to. */
    sealed class Choice {
        object Off : Choice()
        data class Preset(val index: Int) : Choice()
        /** The bands as the listener set them, in millibels, one per band. */
        data class Own(val levels: List<Int>) : Choice()
    }

    /** What this phone's equalizer offers. It does not change, so it is read once. */
    data class Description(
        val centresHz: List<Int>,
        val minMb: Int,
        val maxMb: Int,
        val presetNames: List<String>,
        /** Each preset's band levels, so the bands can show what a preset does. */
        val presetLevels: List<List<Int>>,
    ) {
        val bands: Int get() = centresHz.size
    }

    private var described: Description? = null
    private var describedTried = false

    private var attached: Equalizer? = null
    private var attachedSession: Int = 0

    private val _choice = MutableStateFlow<Choice>(Choice.Off)
    val choice: StateFlow<Choice> = _choice.asStateFlow()
    private var loaded = false

    /**
     * The phone's equalizer, read from one opened for a moment on a session of its own, or null
     * when the phone has none (or will not open one).
     */
    @Synchronized
    fun describe(context: Context): Description? {
        if (describedTried) return described
        describedTried = true
        described = runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val eq = Equalizer(0, am.generateAudioSessionId())
            try {
                val bands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val names = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
                val levels = names.indices.map { p ->
                    eq.usePreset(p.toShort())
                    (0 until bands).map { b -> eq.getBandLevel(b.toShort()).toInt() }
                }
                Description(
                    centresHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                    minMb = range[0].toInt(),
                    maxMb = range[1].toInt(),
                    presetNames = names,
                    presetLevels = levels,
                )
            } finally {
                eq.release()
            }
        }.onFailure { Log.w(TAG, "No equalizer on this phone", it) }.getOrNull()
            ?.takeIf { it.bands > 0 }
        return described
    }

    /** The saved choice, read once per process. */
    @Synchronized
    fun load(context: Context): Choice {
        if (!loaded) {
            _choice.value = read(context)
            loaded = true
        }
        return _choice.value
    }

    /** Keeps [choice] and lets the attached equalizer, if any, sound it at once. */
    @Synchronized
    fun set(context: Context, choice: Choice) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        when (choice) {
            Choice.Off -> prefs.putString(KEY_CHOICE, "off")
            is Choice.Preset -> prefs.putString(KEY_CHOICE, "preset:${choice.index}")
            is Choice.Own -> prefs.putString(KEY_CHOICE, "own").putString(KEY_OWN, choice.levels.joinToString(","))
        }
        prefs.apply()
        _choice.value = choice
        loaded = true
        attached?.let { apply(it, choice) }
    }

    /** The levels the bands are at under [choice]: zero when off. */
    fun levelsOf(choice: Choice, d: Description): List<Int> = when (choice) {
        Choice.Off -> List(d.bands) { 0 }
        is Choice.Preset -> d.presetLevels.getOrNull(choice.index) ?: List(d.bands) { 0 }
        is Choice.Own -> List(d.bands) { choice.levels.getOrElse(it) { 0 } }
    }

    /** [choice] with band [band] moved one step by [direction] (−1 or +1), kept in range. */
    fun stepped(choice: Choice, d: Description, band: Int, direction: Int): Choice.Own {
        val levels = levelsOf(choice, d).toMutableList()
        levels[band] = (levels[band] + direction * STEP_MB).coerceIn(d.minMb, d.maxMb)
        return Choice.Own(levels)
    }

    /**
     * Lays the equalizer over the player's audio session. Called again when the session
     * changes; the old one is let go first. A phone that will not open one plays on untouched.
     */
    @Synchronized
    fun attach(context: Context, sessionId: Int) {
        if (sessionId == 0 || sessionId == attachedSession && attached != null) return
        detach()
        val choice = load(context)
        attached = runCatching { Equalizer(0, sessionId) }
            .onFailure { Log.w(TAG, "Could not open the equalizer on session $sessionId", it) }
            .getOrNull()
            ?.also {
                attachedSession = sessionId
                apply(it, choice)
            }
    }

    @Synchronized
    fun detach() {
        attached?.let { runCatching { it.release() } }
        attached = null
        attachedSession = 0
    }

    private fun apply(eq: Equalizer, choice: Choice) {
        runCatching {
            when (choice) {
                Choice.Off -> eq.enabled = false
                is Choice.Preset -> {
                    eq.usePreset(choice.index.toShort())
                    eq.enabled = true
                }
                is Choice.Own -> {
                    val range = eq.bandLevelRange
                    for (b in 0 until eq.numberOfBands) {
                        val level = choice.levels.getOrElse(b) { 0 }.coerceIn(range[0].toInt(), range[1].toInt())
                        eq.setBandLevel(b.toShort(), level.toShort())
                    }
                    eq.enabled = true
                }
            }
        }.onFailure { Log.w(TAG, "Could not set the equalizer", it) }
    }

    private fun read(context: Context): Choice {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CHOICE, null) ?: return Choice.Off
        return when {
            stored == "own" -> Choice.Own(
                prefs.getString(KEY_OWN, "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
            )
            stored.startsWith("preset:") -> stored.removePrefix("preset:").toIntOrNull()
                ?.let { Choice.Preset(it) } ?: Choice.Off
            else -> Choice.Off
        }
    }
}
