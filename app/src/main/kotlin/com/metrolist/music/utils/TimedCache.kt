/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

/**
 * In-memory cache holding a single value with a time-to-live.
 * Synchronized because callers use Dispatchers.IO.
 */
class TimedCache<T>(
    private val ttlMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var entry: Pair<Long, T>? = null

    @Synchronized
    fun get(): T? = entry?.takeIf { now() - it.first < ttlMillis }?.second

    @Synchronized
    fun put(value: T) {
        entry = now() to value
    }

    @Synchronized
    fun invalidate() {
        entry = null
    }
}
