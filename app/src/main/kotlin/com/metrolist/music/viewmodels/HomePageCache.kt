/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.utils.TimedCache

// ponytail: in-memory only — cache dies with the process, so cold starts still hit
// the network (upgrade path: persist HomePage, requires making it @Serializable in :innertube)
object HomePageCache {
    private const val TTL_MILLIS = 30L * 60L * 1000L
    private val cache = TimedCache<HomePage>(TTL_MILLIS)

    fun get(): HomePage? = cache.get()

    fun put(page: HomePage) = cache.put(page)

    fun invalidate() = cache.invalidate()
}
