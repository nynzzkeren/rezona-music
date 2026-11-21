package com.nayama.music.sync

import com.nayama.music.data.local.PlaybackHistoryEntity
import com.nayama.music.data.local.TrackEntity

enum class PasswordStrengthLevel(val score: Int, val label: String) {
    EMPTY(0, "Enter password"),
    WEAK(1, "Weak"),
    FAIR(2, "Fair"),
    GOOD(3, "Good"),
    STRONG(4, "Strong")
}

data class PasswordStrengthEvaluation(
    val level: PasswordStrengthLevel,
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasNumber: Boolean,
    val hasSymbol: Boolean
) {
    val fraction: Float get() = level.score / 4f
    val isAcceptableForSignUp: Boolean get() = hasMinLength
}

data class CompactTrackMetadata(
    val videoId: String,
    val title: String,
    val artists: String,
    val album: String,
    val durationMs: Long,
    val thumbnailUrl: String
)

object RezonaSyncLogic {
    const val HISTORY_CAP = 500
    const val FIRESTORE_SAFE_BATCH_SIZE = 450

    fun evaluatePasswordStrength(password: String): PasswordStrengthEvaluation {
        if (password.isEmpty()) {
            return PasswordStrengthEvaluation(
                level = PasswordStrengthLevel.EMPTY,
                hasMinLength = false,
                hasUppercase = false,
                hasNumber = false,
                hasSymbol = false
            )
        }
        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var points = 0
        if (password.length >= 6) points++
        if (hasMinLength && hasUppercase) points++
        if (hasNumber) points++
        if (hasSymbol || password.length >= 12) points++

        val level = when {
            points <= 1 -> PasswordStrengthLevel.WEAK
            points == 2 -> PasswordStrengthLevel.FAIR
            points == 3 -> PasswordStrengthLevel.GOOD
            else -> PasswordStrengthLevel.STRONG
        }
        return PasswordStrengthEvaluation(
            level = level,
            hasMinLength = password.length >= 6,
            hasUppercase = hasUppercase,
            hasNumber = hasNumber,
            hasSymbol = hasSymbol
        )
    }

    fun isValidEmailFormat(email: String): Boolean {
        val clean = email.trim()
        if (clean.isEmpty() || clean.length > 254) return false
        val at = clean.indexOf('@')
        if (at <= 0 || at == clean.lastIndex) return false
        val domain = clean.substring(at + 1)
        return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.') && !clean.contains(' ')
    }

    fun extractVideoId(trackId: String, audioUrl: String = ""): String {
        val fromUrl = when {
            audioUrl.contains("v=") -> audioUrl.substringAfter("v=").substringBefore("&")
            audioUrl.contains("youtu.be/") -> audioUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            else -> ""
        }
        val candidate = when {
            fromUrl.isNotBlank() -> fromUrl
            trackId.startsWith("yt_") -> trackId.removePrefix("yt_")
            else -> trackId
        }
        return candidate.trim().ifBlank { trackId.trim() }.take(64)
    }

    fun resolveThumbnailUrl(videoId: String, existingUrl: String = ""): String {
        val cleanExisting = existingUrl.trim()
        if (cleanExisting.startsWith("https://") || cleanExisting.startsWith("http://")) {
            return cleanExisting.take(1024)
        }
        return if (videoId.length == 11) {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        } else {
            ""
        }
    }

    fun toCompactMetadata(track: TrackEntity): CompactTrackMetadata {
        val vid = extractVideoId(track.id, track.audioUrl)
        val thumb = resolveThumbnailUrl(vid, track.thumbnailUrl)
        val album = track.album.ifBlank {
            if (track.source == "On device") "Local Audio" else "YouTube Music"
        }
        return CompactTrackMetadata(
            videoId = vid,
            title = track.title.ifBlank { "Untitled Track" }.take(300),
            artists = track.artist.ifBlank { "Unknown Artist" }.take(300),
            album = album.take(300),
            durationMs = track.durationMs.coerceIn(0L, 86_400_000L),
            thumbnailUrl = thumb
        )
    }

    /**
     * Last-write-wins merge rule: remote state overwrites local state only when strictly newer.
     */
    fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean {
        val local = localUpdatedAt ?: -1L
        return remoteUpdatedAt > local
    }

    /**
     * Keeps at most [cap] newest active playback history entries.
     */
    fun trimHistoryToCap(
        entries: List<PlaybackHistoryEntity>,
        cap: Int = HISTORY_CAP
    ): List<PlaybackHistoryEntity> {
        return entries
            .filter { !it.deleted }
            .sortedByDescending { it.playedAt }
            .take(cap.coerceAtLeast(1))
    }

    fun <T> chunkBatchOperations(
        items: List<T>,
        maxBatchSize: Int = FIRESTORE_SAFE_BATCH_SIZE
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        val boundedSize = maxBatchSize.coerceIn(1, 500)
        return items.chunked(boundedSize)
    }

    /**
     * Produces safe user-facing auth failure copy without exposing raw backend hints, credentials,
     * or account enumeration details on failed sign-in.
     */
    fun toGenericSignInError(exception: Throwable?): String {
        val name = exception?.javaClass?.simpleName.orEmpty()
        return when {
            name.contains("Network", ignoreCase = true) ->
                "Unable to reach the authentication server. Check your connection and try again."
            name.contains("TooManyRequests", ignoreCase = true) ->
                "Too many attempts right now. Please wait a moment and try again."
            else ->
                "Unable to sign in. Check your credentials and try again."
        }
    }
}
