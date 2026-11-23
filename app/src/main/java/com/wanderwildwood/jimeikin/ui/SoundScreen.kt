package com.wanderwildwood.jimeikin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.jimeikin.R
import com.wanderwildwood.jimeikin.playback.SoundShaping
import com.wanderwildwood.jimeikin.playback.SoundShaping.Choice

/**
 * Settings -> Sound: the phone's equalizer. The five bands first, each with − and + a step at a
 * time (no slider: a drag on e-ink is a smear and lands wherever the panel caught up), and the
 * presets under them, the chosen one filled. Choosing a preset shows its levels in the bands;
 * moving a band makes the setting your own. Every change is heard at once.
 */
@Composable
fun SoundScreen() {
    val context = LocalContext.current
    val description = remember { SoundShaping.describe(context) }
    remember { SoundShaping.load(context) }
    val choice by SoundShaping.choice.collectAsState()

    if (description == null) {
        TextMMD(
            text = stringResource(R.string.sound_no_equalizer),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val levels = SoundShaping.levelsOf(choice, description)

    PagedColumnMMD(contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Heading(stringResource(R.string.sound_bands)) }
        for (band in 0 until description.bands) {
            item(key = "band$band") {
                BandRow(
                    label = frequencyLabel(description.centresHz[band]),
                    value = decibels(levels[band]),
                    canLower = levels[band] > description.minMb,
                    canRaise = levels[band] < description.maxMb,
                    onLower = { SoundShaping.set(context, SoundShaping.stepped(choice, description, band, -1)) },
                    onRaise = { SoundShaping.set(context, SoundShaping.stepped(choice, description, band, +1)) },
                )
            }
        }
        item { Heading(stringResource(R.string.sound_presets)) }
        item(key = "off") {
            ChoiceRow(stringResource(R.string.sound_off), choice == Choice.Off) {
                SoundShaping.set(context, Choice.Off)
            }
        }
        description.presetNames.forEachIndexed { index, name ->
            item(key = "preset$index") {
                ChoiceRow(name, choice == Choice.Preset(index)) {
                    SoundShaping.set(context, Choice.Preset(index))
                }
            }
        }
        if (choice is Choice.Own) {
            item(key = "own") { ChoiceRow(stringResource(R.string.sound_own), true) {} }
        }
    }
}

/** What Settings shows under Sound: off, the preset's name, or "Your own". */
@Composable
fun soundSummary(): String? {
    val context = LocalContext.current
    val description = remember { SoundShaping.describe(context) } ?: return null
    remember { SoundShaping.load(context) }
    val choice by SoundShaping.choice.collectAsState()
    return when (val c = choice) {
        Choice.Off -> stringResource(R.string.sound_off)
        is Choice.Preset -> description.presetNames.getOrNull(c.index) ?: stringResource(R.string.sound_off)
        is Choice.Own -> stringResource(R.string.sound_own)
    }
}

@Composable
private fun Heading(text: String) {
    TextMMD(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun BandRow(
    label: String,
    value: String,
    canLower: Boolean,
    canRaise: Boolean,
    onLower: () -> Unit,
    onRaise: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = label, style = MaterialTheme.typography.titleSmall)
            TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
        }
        HouseButton(label = "−", onClick = onLower, enabled = canLower, compact = true)
        Spacer(Modifier.width(HOUSE_GAP))
        HouseButton(label = "+", onClick = onRaise, enabled = canRaise, compact = true)
    }
}

/** A preset to choose: filled when it is the one in use, as a chosen choice is everywhere. */
@Composable
private fun ChoiceRow(label: String, chosen: Boolean, onClick: () -> Unit) {
    HouseButton(
        label = label,
        onClick = onClick,
        on = chosen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun frequencyLabel(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000.0
        stringResource(R.string.sound_khz, if (k % 1.0 == 0.0) k.toInt().toString() else "%.1f".format(k))
    } else {
        stringResource(R.string.sound_hz, hz)
    }

@Composable
private fun decibels(mb: Int): String {
    val db = mb / 100.0
    val shown = if (db % 1.0 == 0.0) db.toInt().toString() else "%.1f".format(db)
    return stringResource(R.string.sound_db, if (mb > 0) "+$shown" else shown)
}
