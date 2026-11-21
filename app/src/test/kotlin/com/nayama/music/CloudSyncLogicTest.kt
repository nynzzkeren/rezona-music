package com.nayama.music

import com.nayama.music.data.local.PlaybackHistoryEntity
import com.nayama.music.data.local.TrackEntity
import com.nayama.music.sync.PasswordStrengthLevel
import com.nayama.music.sync.RezonaSyncLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncLogicTest {

    @Test
    fun passwordStrength_evaluatesWeakFairGoodStrong() {
        assertEquals(PasswordStrengthLevel.EMPTY, RezonaSyncLogic.evaluatePasswordStrength("").level)
        assertEquals(PasswordStrengthLevel.WEAK, RezonaSyncLogic.evaluatePasswordStrength("abc").level)
        assertFalse(RezonaSyncLogic.evaluatePasswordStrength("abc").isAcceptableForSignUp)

        assertEquals(PasswordStrengthLevel.FAIR, RezonaSyncLogic.evaluatePasswordStrength("Abcdefgh").level)
        assertTrue(RezonaSyncLogic.evaluatePasswordStrength("abcdef").isAcceptableForSignUp)

        assertEquals(PasswordStrengthLevel.GOOD, RezonaSyncLogic.evaluatePasswordStrength("Abcdefg1").level)
        assertEquals(PasswordStrengthLevel.STRONG, RezonaSyncLogic.evaluatePasswordStrength("Abcdefg1!").level)
    }

    @Test
    fun extractVideoId_stripsYtPrefixAndUrlParams() {
        assertEquals(
            "dQw4w9WgXcQ",
            RezonaSyncLogic.extractVideoId("yt_dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RD")
        )
        assertEquals(
            "abc12345678",
            RezonaSyncLogic.extractVideoId("yt_abc12345678", "")
        )
        assertEquals(
            "short11char",
            RezonaSyncLogic.extractVideoId("track_x", "https://youtu.be/short11char?si=xyz")
        )
    }

    @Test
    fun toCompactMetadata_neverIncludesStreamOrAudioUrl() {
        val track = TrackEntity(
            id = "yt_kJQP7kiw5Fk",
            title = "Despacito",
            artist = "Luis Fonsi",
            durationMs = 281000L,
            audioUrl = "https://rr3---sn-example.googlevideo.com/videoplayback?v=kJQP7kiw5Fk",
            album = "",
            thumbnailUrl = ""
        )
        val meta = RezonaSyncLogic.toCompactMetadata(track)
        assertEquals("kJQP7kiw5Fk", meta.videoId)
        assertEquals("Despacito", meta.title)
        assertEquals("Luis Fonsi", meta.artists)
        assertEquals("YouTube Music", meta.album)
        assertEquals(281000L, meta.durationMs)
        assertEquals("https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg", meta.thumbnailUrl)
    }

    @Test
    fun shouldApplyRemote_enforcesLastWriteWinsByUpdatedAt() {
        assertTrue(RezonaSyncLogic.shouldApplyRemote(localUpdatedAt = null, remoteUpdatedAt = 100L))
        assertTrue(RezonaSyncLogic.shouldApplyRemote(localUpdatedAt = 100L, remoteUpdatedAt = 200L))
        assertFalse(RezonaSyncLogic.shouldApplyRemote(localUpdatedAt = 200L, remoteUpdatedAt = 200L))
        assertFalse(RezonaSyncLogic.shouldApplyRemote(localUpdatedAt = 300L, remoteUpdatedAt = 200L))
    }

    @Test
    fun trimHistoryToCap_capsAt500NewestActiveEntries() {
        val entries = (1..650).map { idx ->
            PlaybackHistoryEntity(
                id = idx.toLong(),
                trackId = "yt_t_$idx",
                playedAt = idx.toLong() * 1000L,
                entryId = "e_$idx",
                updatedAt = idx.toLong() * 1000L,
                deleted = idx == 650
            )
        }
        val trimmed = RezonaSyncLogic.trimHistoryToCap(entries, cap = 500)
        assertEquals(500, trimmed.size)
        assertEquals("e_649", trimmed.first().entryId)
        assertEquals("e_150", trimmed.last().entryId)
    }

    @Test
    fun chunkBatchOperations_respects500Limit() {
        val ops = (1..1005).toList()
        val chunks = RezonaSyncLogic.chunkBatchOperations(ops, maxBatchSize = 450)
        assertEquals(3, chunks.size)
        assertEquals(450, chunks[0].size)
        assertEquals(450, chunks[1].size)
        assertEquals(105, chunks[2].size)
    }

    @Test
    fun toGenericSignInError_neverLeaksRawCredentialsOrSpecificReason() {
        val msg = RezonaSyncLogic.toGenericSignInError(IllegalStateException("user secretPass123"))
        assertFalse(msg.contains("secretPass123"))
        assertTrue(msg.contains("Check your credentials"))
    }
}
