package com.wanderwildwood.jimeikin.playback

/**
 * Where in the queue the player is, worked out from song ids alone. A song can be in the queue
 * more than once ("Play next", "Add to queue"), so an id names a place only together with the
 * place the queue was at.
 */
object QueuePosition {

    /**
     * The queue index the player's [mediaId] stands for, given the index the queue was at.
     * The current place is kept while it holds that id; otherwise the nearest place at or after
     * it, then the nearest before it. Null when the id is not in the queue.
     */
    fun follow(queueIds: List<String>, currentIndex: Int?, mediaId: String): Int? {
        if (currentIndex != null && queueIds.getOrNull(currentIndex) == mediaId) return currentIndex
        val from = (currentIndex ?: 0).coerceIn(0, queueIds.size)
        for (i in from until queueIds.size) if (queueIds[i] == mediaId) return i
        for (i in from - 1 downTo 0) if (queueIds[i] == mediaId) return i
        return null
    }

    /**
     * The index and position to restore a saved queue at, after songs that are no longer in
     * the library were left out of it. The saved place is found by the id it held; when that
     * song is gone, the queue starts at its top, from the beginning.
     */
    fun restore(savedIds: List<String>, savedIndex: Int?, savedPositionMs: Long, keptIds: List<String>): Pair<Int, Long> {
        if (keptIds.isEmpty()) return 0 to 0L
        val savedId = savedIndex?.let { savedIds.getOrNull(it) } ?: return 0 to 0L
        // The saved place's own copy: count how many times the id came before it, and take
        // the same copy among the songs kept (or the last one, if some copies went).
        val copy = savedIds.subList(0, savedIndex).count { it == savedId }
        val places = keptIds.indices.filter { keptIds[it] == savedId }
        if (places.isEmpty()) return 0 to 0L
        return places[copy.coerceAtMost(places.size - 1)] to savedPositionMs
    }
}
