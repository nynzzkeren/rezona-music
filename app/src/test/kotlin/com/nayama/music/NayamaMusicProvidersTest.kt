package com.nayama.music

import com.nayama.music.data.LyricsRepository
import com.nayama.music.model.MusicSource
import com.nayama.music.model.Track
import com.nayama.music.network.DeezerMusicProvider
import com.nayama.music.network.SaavnMusicProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class NayamaMusicProvidersTest {

    @Test
    fun testSaavnDesDecryption() {
        // Test encrypting a sample saavn cdn url and then decrypting it
        val originalUrl = "https://aac.saavncdn.com/123/sample_track_96.mp4"
        val key = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedBytes = cipher.doFinal(originalUrl.toByteArray(Charsets.UTF_8))
        val base64Encrypted = java.util.Base64.getEncoder().encodeToString(encryptedBytes)

        val decryptedUrl = SaavnMusicProvider.decryptMediaUrl(base64Encrypted)
        assertNotNull(decryptedUrl)
        // Check that _96.mp4 is automatically upgraded to _320.mp4 for high-quality audio
        assertEquals("https://aac.saavncdn.com/123/sample_track_320.mp4", decryptedUrl)
    }

    @Test
    fun testLyricsRepositoryNullSafety() = runBlocking {
        val repo = LyricsRepository()
        val dummyTrack = Track(
            id = "non_existent_track_xyz_999",
            title = "NonExistentTrack99999",
            artist = "NonExistentArtist99999",
            album = "Single",
            durationSeconds = 120
        )
        // Must return null gracefully without throwing NullPointerException in ConcurrentHashMap
        val lyrics = repo.getLyrics(dummyTrack)
        assertNull(lyrics)
    }

    @Test
    fun testWordByWordAndLineLrcParsing() {
        val repo = LyricsRepository()
        val sampleEnhancedLrc = """
            [00:12.50] <00:12.50> Midnight <00:13.10> rain <00:13.80> falling
            [00:16.00] Simple line without word tags
        """.trimIndent()

        val (lines, hasWordSync) = repo.parseSyncedLyrics(sampleEnhancedLrc)
        assertTrue(hasWordSync)
        assertEquals(2, lines.size)
        assertEquals(12_500L, lines[0].timeMs)
        assertEquals("Midnight rain falling", lines[0].text)
        assertEquals(3, lines[0].words.size)
        assertEquals("Midnight", lines[0].words[0].text)
        assertEquals(13_100L, lines[0].words[1].timeMs)

        assertEquals(16_000L, lines[1].timeMs)
        assertEquals("Simple line without word tags", lines[1].text)
        assertTrue(lines[1].words.isEmpty())
    }

    @Test
    fun testTrackMultiSourceMetadata() {
        val ytTrack = Track(
            id = "ytm_OtLcqr3RQJY",
            title = "snowfall",
            artist = "Øneheart",
            album = "snowfall",
            durationSeconds = 124,
            source = MusicSource.YOUTUBE_MUSIC,
            audioQualityLabel = "OPUS / M4A HD"
        )
        assertEquals("YTM", ytTrack.source.shortBadge)
        assertEquals("2:04", ytTrack.formattedDuration)
        assertEquals("OPUS / M4A HD", ytTrack.audioQualityLabel)
    }
}

