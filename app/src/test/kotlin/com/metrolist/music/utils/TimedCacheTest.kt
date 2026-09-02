/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedCacheTest {
    private var currentTime = 0L
    private val cache = TimedCache<String>(ttlMillis = 1000L, now = { currentTime })

    @Test
    fun `get returns null when empty`() {
        assertNull(cache.get())
    }

    @Test
    fun `get returns value within ttl`() {
        cache.put("a")
        currentTime = 999L
        assertEquals("a", cache.get())
    }

    @Test
    fun `get returns null after ttl expired`() {
        cache.put("a")
        currentTime = 1000L
        assertNull(cache.get())
    }

    @Test
    fun `invalidate clears the entry`() {
        cache.put("a")
        cache.invalidate()
        assertNull(cache.get())
    }

    @Test
    fun `put overwrites previous entry and refreshes timestamp`() {
        cache.put("a")
        currentTime = 900L
        cache.put("b")
        currentTime = 1800L
        assertEquals("b", cache.get())
    }
}
