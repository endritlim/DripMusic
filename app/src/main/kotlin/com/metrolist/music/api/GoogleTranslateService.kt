/**
 * DripMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free, keyless lyric translation via Google's undocumented "gtx" endpoint
 * (the same endpoint the BetterLyrics extension falls back to).
 * ponytail: unofficial API — rate-limited per IP and can break or block
 * (e.g. VPN/datacenter IPs) anytime; there is no SLA. If it dies for good,
 * swap [translate] for another backend — callers only depend on this signature.
 */
object GoogleTranslateService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Translates [text] into [targetLanguage] (ISO code, e.g. "de"), returning one
     * translated line per input line. Line alignment is best-effort: the whole text
     * goes in a single request and Google's response is split on newlines.
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            if (text.isBlank()) return@runCatching emptyList()

            val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=auto&tl=${URLEncoder.encode(targetLanguage, "UTF-8")}&dt=t" +
                "&q=${URLEncoder.encode(text, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Translation request failed with HTTP ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty translation response")
                val root = JSONArray(body)

                // Response shape: [[[translated, original, ...], ...], null, "detectedLang", ...]
                val chunks = root.optJSONArray(0) ?: error("Unexpected translation response")
                val translated = buildString {
                    for (i in 0 until chunks.length()) {
                        append(chunks.optJSONArray(i)?.optString(0).orEmpty())
                    }
                }

                translated.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
        }
    }
}
