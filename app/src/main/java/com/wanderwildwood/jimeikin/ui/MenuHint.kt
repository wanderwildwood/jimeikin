package com.wanderwildwood.jimeikin.ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.jimeikin.R
import kotlinx.coroutines.delay

/**
 * Whether the one-line tip about holding a song is on show. It belongs to the first time the app
 * is used only: [heldBefore] is true once it has been shown (or a song held) on an earlier run.
 */
fun menuHintVisible(heldBefore: Boolean, hasSongs: Boolean): Boolean = hasSongs && !heldBefore

/** How long the tip stays the one time it is shown. */
const val MENU_HINT_MS = 6_000L

/**
 * The tip about holding a song shows once, the first time the app is used, and goes away after a
 * moment ([MENU_HINT_MS]) or as soon as a song is held. It never comes back after that.
 */
object MenuHint {
    private const val PREFS = "menu_hint"
    private const val KEY_HELD = "held_a_song" // kept: also true for anyone who held a song before
    private var loaded = false
    /** Read once per run: the tip was seen (or a song held) on an earlier run. */
    private var seenBefore = false
    /** This run: the moment is over, or a song was held. */
    private var gone by mutableStateOf(false)

    private fun load(context: Context) {
        if (loaded) return
        seenBefore = prefs(context).getBoolean(KEY_HELD, false)
        loaded = true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun remember(context: Context) = prefs(context).edit().putBoolean(KEY_HELD, true).apply()

    fun heldBefore(context: Context): Boolean {
        load(context)
        return seenBefore || gone
    }

    /** The tip has been put on screen: it will not show on a later run. */
    fun shown(context: Context) = remember(context)

    /** Its moment is over for this run. */
    fun timeUp() {
        gone = true
    }

    fun songHeld(context: Context) {
        load(context)
        gone = true
        remember(context)
    }
}

/** The tip itself, for the top of a list of songs: the first run only, and only for a moment. */
@Composable
fun MenuHintLine(hasSongs: Boolean = true) {
    val context = LocalContext.current
    if (!menuHintVisible(MenuHint.heldBefore(context), hasSongs)) return
    LaunchedEffect(Unit) {
        MenuHint.shown(context)
        delay(MENU_HINT_MS)
        MenuHint.timeUp()
    }
    TextMMD(
        text = stringResource(R.string.hint_hold_song),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
