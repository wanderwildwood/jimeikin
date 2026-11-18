package com.wanderwildwood.jimeikin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.jimeikin.R
import com.wanderwildwood.jimeikin.data.ArtistNames

/** An artist's name as drawn: a song saved without one reads in the phone's own language. */
@Composable
fun artistShown(name: String): String =
    ArtistNames.shown(name, stringResource(R.string.library_artist_unknown))
