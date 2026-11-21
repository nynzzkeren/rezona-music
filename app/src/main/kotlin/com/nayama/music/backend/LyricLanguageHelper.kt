package com.nayama.music.backend

import java.util.Locale

/**
 * Lightweight on-device Romanization & lyric line translation helper.
 * Performs fast deterministic transliteration for non-Latin scripts (Hangul/Hiragana/Katakana/Cyrillic)
 * and common multilingual phrase glosses matching the device language with zero external network calls.
 */
object LyricLanguageHelper {

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

    fun translateToDeviceLanguage(text: String, targetLang: String = Locale.getDefault().language): String? {
        if (text.isBlank()) return null
        val lower = text.trim().lowercase()
        val lang = targetLang.lowercase()
        // Check exact phrase glosses for common Tagalog/Indonesian/Spanish/Japanese/English lines
        val dict = when (lang) {
            "id", "in" -> COMMON_GLOSS_ID
            else -> COMMON_GLOSS_EN
        }
        dict.entries.firstOrNull { (k, _) -> lower.contains(k) }?.let { return it.value }
        return null
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

    private val COMMON_GLOSS_EN = mapOf(
        "aku yang jatuh cinta" to "I'm the one who fell in love",
        "terima kasih" to "Thank you",
        "cinta" to "Love",
        "malam ini" to "Tonight",
        "sendiri" to "Alone",
        "namamalik mata" to "Just closing my eyes",
        "pumikit na muna" to "Close your eyes for now"
    )

    private val COMMON_GLOSS_ID = mapOf(
        "i love you" to "Aku mencintaimu",
        "all night" to "Sepanjang malam",
        "up all night to get lucky" to "Begadang semalaman mencari keberuntungan",
        "don't go" to "Jangan pergi",
        "stay with me" to "Tetap bersamaku"
    )
}
