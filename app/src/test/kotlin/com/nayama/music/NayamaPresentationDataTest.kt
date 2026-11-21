package com.nayama.music

import com.nayama.music.data.SamplePresentationData
import com.nayama.music.navigation.NayamaScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NayamaPresentationDataTest {

    @Test
    fun testNavigationScreensCount() {
        assertEquals(3, NayamaScreen.bottomNavItems.size)
        assertEquals("Home", NayamaScreen.Home.title)
        assertEquals("Search", NayamaScreen.Search.title)
        assertEquals("Library", NayamaScreen.Library.title)
    }

    @Test
    fun testSamplePresentationData() {
        assertTrue(SamplePresentationData.featuredTracks.isNotEmpty())
        assertTrue(SamplePresentationData.popularAlbums.isNotEmpty())
        assertTrue(SamplePresentationData.featuredArtists.isNotEmpty())
        assertTrue(SamplePresentationData.curatedPlaylists.isNotEmpty())
        assertTrue(SamplePresentationData.exploreCategories.isNotEmpty())

        val firstTrack = SamplePresentationData.featuredTracks.first()
        assertNotNull(firstTrack.title)
        assertTrue(firstTrack.durationSeconds > 0)
        assertEquals("3:54", firstTrack.formattedDuration)
    }
}

