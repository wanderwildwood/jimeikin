package com.wanderwildwood.jimeikin.data

/**
 * Whether one of your own files is gone after asking for it to be deleted, so that its row and
 * its playlist places go only with the file.
 *
 * DocumentFile's delete() and exists() both answer false when the provider throws (a folder
 * whose permission was dropped, a card provider error), and "not there" used to be read from
 * that false: a file the app could not reach counted as deleted and lost its row while it
 * stayed on the phone. Here only a provider that answers counts.
 */
object DeletedFile {
    /**
     * [deleted]: the provider said the delete was done. [lookUp] asks the provider for the
     * document again and gives the number of rows it answered with, or null when it gave no
     * answer; it throws when it could not be asked. Gone means deleted, or asked and answered
     * with no such row. No answer is not taken for "not there": a documents provider gives none
     * for a card that is out of the phone too, and the file is still on that card.
     */
    fun isGone(deleted: Boolean, lookUp: () -> Int?): Boolean {
        if (deleted) return true
        return try {
            lookUp() == 0
        } catch (_: Exception) {
            false
        }
    }
}
