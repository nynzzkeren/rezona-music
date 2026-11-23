package com.nayama.music.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * On-device Romanization + real cached lyric translation helper.
 * - Romanization: deterministic Hangul / Hiragana / Katakana / Cyrillic transliteration.
 * - Translation: batch-translates lyric lines to the device language via Google Translate
 *   GTX endpoint with in-memory caching so every song translates smoothly and fast.
 */
object LyricLanguageHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Key: "$lang::$originalLine" -> translated line
    private val translationCache = ConcurrentHashMap<String, String>()

    fun normalizedDeviceLang(): String {
        val raw = Locale.getDefault().language.lowercase(Locale.ENGLISH)
        return when (raw) {
            "in" -> "id"
            "" -> "en"
            else -> raw
        }
    }

    fun romanize(text: String): String? {
        if (text.isBlank()) return null
        val hasNonAscii = text.any { it.code > 127 }
        if (!hasNonAscii) return null

        val sb = StringBuilder()
        var converted = false
        for (ch in text) {
            val mapped = when {
                ch in '\uAC00'..'\uD7A3' -> {
                    converted = true
                    romanizeHangulSyllable(ch)
                }
                ch in '\u3040'..'\u309F' -> {
                    converted = true
                    HIRAGANA_MAP[ch] ?: ch.toString()
                }
                ch in '\u30A0'..'\u30FF' -> {
                    converted = true
                    val hira = (ch.code - 0x60).toChar()
                    HIRAGANA_MAP[hira] ?: ch.toString()
                }
                ch in '\u0400'..'\u04FF' -> {
                    converted = true
                    CYRILLIC_MAP[ch.lowercaseChar()] ?: ch.toString()
                }
                else -> ch.toString()
            }
            sb.append(mapped)
        }
        return if (converted) sb.toString().trim() else null
    }

    /**
     * Synchronous lookup from cache (plus instant local gloss fallback).
     */
    fun translateToDeviceLanguage(text: String, targetLang: String = normalizedDeviceLang()): String? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        val lang = if (targetLang.equals("in", ignoreCase = true)) "id" else targetLang.lowercase(Locale.ENGLISH)
        translationCache["$lang::$clean"]?.let { cached ->
            return cached.takeIf { !it.equals(clean, ignoreCase = true) }
        }
        return null
    }

    /**
     * Batch-translates all unique lyric lines into [targetLang] in chunks so a full song
     * completes in 1-2 lightweight network calls and caches results for instant rendering.
     */
    suspend fun translateLyricsBatch(
        lines: List<String>,
        targetLang: String = normalizedDeviceLang()
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val lang = if (targetLang.equals("in", ignoreCase = true)) "id" else targetLang.lowercase(Locale.ENGLISH)
        val result = LinkedHashMap<String, String>()
        val missing = mutableListOf<String>()

        for (raw in lines) {
            val clean = raw.trim()
            if (clean.isBlank()) continue
            val cached = translationCache["$lang::$clean"]
            if (cached != null) {
                if (!cached.equals(clean, ignoreCase = true)) {
                    result[clean] = cached
                }
            } else if (clean !in missing) {
                missing.add(clean)
            }
        }

        if (missing.isEmpty()) return@withContext result

        // Chunk by ~16 lines per request to keep URL length well within safe limits
        val chunks = missing.chunked(16)
        for (chunk in chunks) {
            val joined = chunk.joinToString("\n")
            val translatedJoined = fetchTranslation(joined, lang) ?: continue
            val translatedLines = translatedJoined.split("\n")
            if (translatedLines.size == chunk.size) {
                for (i in chunk.indices) {
                    val src = chunk[i]
                    val dst = translatedLines[i].trim()
                    if (dst.isNotBlank()) {
                        translationCache["$lang::$src"] = dst
                        if (!dst.equals(src, ignoreCase = true)) {
                            result[src] = dst
                        }
                    }
                }
            } else {
                // Fallback per-line when line count differs
                for (src in chunk) {
                    val dst = fetchTranslation(src, lang)?.trim().orEmpty()
                    if (dst.isNotBlank()) {
                        translationCache["$lang::$src"] = dst
                        if (!dst.equals(src, ignoreCase = true)) {
                            result[src] = dst
                        }
                    }
                }
            }
        }
        result
    }

    internal fun parseGtxTranslationJson(rawJson: String): String? {
        val root = runCatching { JSONArray(rawJson) }.getOrNull() ?: return null
        val sentences = root.optJSONArray(0) ?: return null
        val sb = StringBuilder()
        for (i in 0 until sentences.length()) {
            val pair = sentences.optJSONArray(i) ?: continue
            val piece = pair.optString(0, "")
            if (piece.isNotEmpty()) sb.append(piece)
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun fetchTranslation(text: String, targetLang: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                parseGtxTranslationJson(body)
            }
        }.getOrNull()
    }

    private val INITIALS = arrayOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
    private val MEDIALS = arrayOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
    private val FINALS = arrayOf("", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "h")

    private fun romanizeHangulSyllable(ch: Char): String {
        val code = ch.code - 0xAC00
        val i = code / (21 * 28)
        val m = (code % (21 * 28)) / 28
        val f = code % 28
        return INITIALS.getOrElse(i) { "" } + MEDIALS.getOrElse(m) { "" } + FINALS.getOrElse(f) { "" }
    }

    private val HIRAGANA_MAP = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po"
    )

    private val CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ы' to "y", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )
}
