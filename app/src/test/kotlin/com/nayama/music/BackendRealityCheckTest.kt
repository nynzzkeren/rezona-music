package com.nayama.music

import com.nayama.music.backend.LyricLanguageHelper
import com.nayama.music.backend.NewPipeDownloader
import com.nayama.music.backend.YouTubeMusicProvider
import com.nayama.music.backend.innertube.InnerTubeClient
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.backend.innertube.InnerTubeParser
import com.nayama.music.backend.innertube.InnerTubeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

class BackendRealityCheckTest {

    @Test
    fun verifyProvidersAndStream() = runBlocking {
        println("=== BACKEND REALITY CHECK (UI V2 / YOUTUBE MUSIC ONLY) ===")

        runCatching { NewPipe.init(NewPipeDownloader.create()) }
        val ytm = YouTubeMusicProvider()
        val innerTubeRepo = InnerTubeRepository(InnerTubeClient())

        // 1. InnerTube Unfiltered ALL Search ("multo" -> verify > 5 results)
        val multoPage = innerTubeRepo.searchPage("multo", InnerTubeFilter.ALL)
        println("InnerTube (ALL) 'multo' count: ${multoPage.items.size}, sections=${multoPage.sectionCounts}, cont=${multoPage.continuation != null}")
        assertTrue(
            "InnerTube ALL search for 'multo' must return multiple results (got ${multoPage.items.size})",
            multoPage.items.size > 5
        )

        // 2. InnerTube SONGS Search ("Daft Punk Get Lucky")
        val ytmResults = innerTubeRepo.search("Daft Punk Get Lucky", InnerTubeFilter.SONGS)
        println("InnerTube SONGS 'Daft Punk Get Lucky' count: ${ytmResults.size}")
        assertTrue("InnerTube must return real results for 'Daft Punk Get Lucky'", ytmResults.isNotEmpty())

        // 3. Short query test ("li")
        val ytmShort = innerTubeRepo.search("li", InnerTubeFilter.SONGS)
        println("InnerTube short query 'li' count: ${ytmShort.size}")
        assertTrue("Short query 'li' should return results for YouTube Music", ytmShort.isNotEmpty())

        // 4. InnerTube Home Feed (FEmusic_home)
        val homeSections = innerTubeRepo.getHomeSections(forceRefresh = true)
        println("InnerTube Home sections count: ${homeSections.size}")
        assertTrue("InnerTube Home feed must return real shelves", homeSections.isNotEmpty())

        // 5. Stream Resolution for top YTM track via NewPipe
        val firstStreamUrl = ytmResults.firstOrNull()?.streamUrl
        if (!firstStreamUrl.isNullOrBlank()) {
            val stream = ytm.resolveAudioStream(firstStreamUrl)
            println("Resolved Stream: format=${stream?.format} bitrate=${stream?.bitrateKbps}kbps")
            assertTrue("NewPipe must resolve a real audio URL", !stream?.streamUrl.isNullOrBlank())
        }

        println("=== END REALITY CHECK ===")
    }

    @Test
    fun testRecordedJsonFixtureSearchCardShelfAndContinuation() {
        val fixtureJson = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "musicCardShelfRenderer": {
                                  "title": { "runs": [{ "text": "Multo" }] },
                                  "subtitle": { "runs": [{ "text": "Song" }, { "text": " • " }, { "text": "Cup of Joe" }, { "text": " • " }, { "text": "3:57" }] },
                                  "onTap": { "watchEndpoint": { "videoId": "CcS1fsuT10M" } },
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "2cL2LkST4a4" },
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Multo (Stripped Down)" }] } } },
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Cup of Joe" }, { "text": "3:41" }] } } }
                                        ]
                                      }
                                    }
                                  ]
                                }
                              },
                              {
                                "musicShelfRenderer": {
                                  "title": { "runs": [{ "text": "Songs" }] },
                                  "continuations": [{ "nextContinuationData": { "continuation": "CONT_TOKEN_XYZ" } }],
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "4D7u5KF7SP8" },
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Get Lucky" }] } } },
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Daft Punk" }, { "text": "4:08" }] } } }
                                        ]
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val page = InnerTubeParser.parseSearchPage(fixtureJson, "query=multo, filter=All")
        assertEquals(3, page.items.size)
        assertEquals("Multo", page.items[0].title)
        assertEquals("Cup of Joe", page.items[0].artist)
        assertEquals("Multo (Stripped Down)", page.items[1].title)
        assertEquals("Get Lucky", page.items[2].title)
        assertEquals("CONT_TOKEN_XYZ", page.continuation)
        assertNotNull(page.sectionCounts["Top result"])
        assertEquals(2, page.sectionCounts["Top result"])
    }

    @Test
    fun testRomanizationAndChannelFlow() = runBlocking {
        assertEquals("annyeong", LyricLanguageHelper.romanize("안녕"))
        val emissions = channelFlow {
            val ch = Channel<Int>(Channel.UNLIMITED)
            launch { ch.send(1) }
            launch { ch.send(2) }
            repeat(2) { send(ch.receive()) }
        }.toList()
        assertEquals(2, emissions.size)
    }
}
