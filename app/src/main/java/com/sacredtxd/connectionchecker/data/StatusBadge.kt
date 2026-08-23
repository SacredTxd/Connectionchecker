package com.sacredtxd.connectionchecker.data

/**
 * The text drawn into the status bar icon. It has to stay legible at 24dp, so it is
 * at most four glyphs and never carries units.
 */
object StatusBadge {
    private const val MAX_PLAIN_MS = 999L

    fun format(event: ConnectionEvent?): String = when (val reachability = event?.reachability) {
        null -> "?"
        is ReachabilityResult.Success -> when {
            reachability.latencyMs <= MAX_PLAIN_MS -> reachability.latencyMs.toString()
            // Past a second the exact figure stops mattering; that it is slow does.
            else -> "1k+"
        }
        is ReachabilityResult.Timeout -> "T/O"
        is ReachabilityResult.Failure -> "×"
    }
}
