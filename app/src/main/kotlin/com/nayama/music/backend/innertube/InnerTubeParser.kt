package com.nayama.music.backend.innertube

import com.nayama.music.model.RezonaTrack
import org.json.JSONArray
import org.json.JSONObject

/**
 * Defensive JSON parser for YouTube Music InnerTube responses.
 * Parses search (initial + continuation), home shelves, moods & genres,
 * artist/album/playlist pages, next/radio queues, and YouTube lyrics tabs.
 */
object InnerTubeParser {

    private const val TAG = "InnerTubeParser"

    fun parseSearchResults(jsonStr: String?): List<RezonaTrack> {
        return parseSearchPage(jsonStr, requestParams = null).items
    }

    fun parseSearchPage(jsonStr: String?, requestParams: String? = null): InnerTubeSearchPage {
        if (jsonStr.isNullOrBlank()) return InnerTubeSearchPage(emptyList(), null, emptyMap(), requestParams)
        val results = mutableListOf<RezonaTrack>()
        val seenIds = HashSet<String>()
        val sectionCounts = LinkedHashMap<String, Int>()
        var continuation: String? = null

        fun addTrackIfNew(sectionName: String, track: RezonaTrack?) {
            if (track != null && track.id.isNotBlank() && seenIds.add(track.id)) {
                results.add(track.copy(index = results.size))
                sectionCounts[sectionName] = (sectionCounts[sectionName] ?: 0) + 1
            }
        }

        try {
            val root = JSONObject(jsonStr)

            // Handle continuation response first if present
            val contContents = root.optJSONObject("continuationContents")
            if (contContents != null) {
                contContents.optJSONObject("musicShelfContinuation")?.let { shelf ->
                    continuation = extractContinuationToken(shelf)
                    val contents = shelf.optJSONArray("contents")
                    if (contents != null) {
                        for (i in 0 until contents.length()) {
                            val itemRenderer = contents.optJSONObject(i)
                                ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            runCatching {
                                addTrackIfNew("Continuation", parseResponsiveListItem(itemRenderer, results.size))
                            }
                        }
                    }
                }
                return InnerTubeSearchPage(results, continuation, sectionCounts, requestParams)
            }

            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return InnerTubeSearchPage(emptyList(), null, emptyMap(), requestParams)

            val tabContent = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return InnerTubeSearchPage(emptyList(), null, emptyMap(), requestParams)

            for (i in 0 until tabContent.length()) {
                val section = tabContent.optJSONObject(i) ?: continue

                // 1. Top Card Result (musicCardShelfRenderer) + nested contents
                section.optJSONObject("musicCardShelfRenderer")?.let { card ->
                    runCatching { addTrackIfNew("Top result", parseMusicCardShelf(card, results.size)) }
                    val cardContents = card.optJSONArray("contents")
                    if (cardContents != null) {
                        for (k in 0 until cardContents.length()) {
                            val itemRenderer = cardContents.optJSONObject(k)
                                ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            runCatching { addTrackIfNew("Top result", parseResponsiveListItem(itemRenderer, results.size)) }
                        }
                    }
                }

                // 2. Direct Shelf Results (musicShelfRenderer)
                section.optJSONObject("musicShelfRenderer")?.let { shelf ->
                    val title = extractRunsText(shelf.optJSONObject("title")).ifBlank { "Results" }
                    continuation = continuation ?: extractContinuationToken(shelf)
                    parseMusicShelfContents(shelf) { addTrackIfNew(title, it) }
                }

                // 3. Item Section Results
                section.optJSONObject("itemSectionRenderer")?.let { itemSection ->
                    val isrContents = itemSection.optJSONArray("contents") ?: return@let
                    for (k in 0 until isrContents.length()) {
                        val el = isrContents.optJSONObject(k) ?: continue
                        el.optJSONObject("musicResponsiveListItemRenderer")?.let { itemRenderer ->
                            runCatching { addTrackIfNew("All results", parseResponsiveListItem(itemRenderer, results.size)) }
                        }
                        el.optJSONObject("musicShelfRenderer")?.let { shelf ->
                            val title = extractRunsText(shelf.optJSONObject("title")).ifBlank { "Shelf" }
                            continuation = continuation ?: extractContinuationToken(shelf)
                            parseMusicShelfContents(shelf) { addTrackIfNew(title, it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse search results", e)
        }

        return InnerTubeSearchPage(results, continuation, sectionCounts, requestParams)
    }

    private fun extractContinuationToken(obj: JSONObject?): String? {
        val contArray = obj?.optJSONArray("continuations") ?: return null
        for (i in 0 until contArray.length()) {
            val token = contArray.optJSONObject(i)
                ?.optJSONObject("nextContinuationData")
                ?.optString("continuation")
                ?.takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
    }

    private fun parseMusicShelfContents(shelf: JSONObject, onTrack: (RezonaTrack?) -> Unit) {
        val contents = shelf.optJSONArray("contents") ?: return
        for (j in 0 until contents.length()) {
            val item = contents.optJSONObject(j) ?: continue
            val itemRenderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            runCatching {
                onTrack(parseResponsiveListItem(itemRenderer, j))
            }.onFailure {
                SafeLog.d(TAG, "Skipping malformed item in shelf", it)
            }
        }
    }

    fun parseSearchSuggestions(jsonStr: String?): List<String> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val suggestions = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            val contents = root.optJSONArray("contents") ?: return emptyList()
            for (s in 0 until contents.length()) {
                val sectionRenderer = contents.optJSONObject(s)
                    ?.optJSONObject("searchSuggestionsSectionRenderer")
                    ?.optJSONArray("contents") ?: continue
                for (i in 0 until sectionRenderer.length()) {
                    val item = sectionRenderer.optJSONObject(i) ?: continue
                    val suggestionRenderer = item.optJSONObject("searchSuggestionRenderer")
                        ?: item.optJSONObject("historySuggestionRenderer")
                        ?: continue
                    val text = extractRunsText(suggestionRenderer.optJSONObject("suggestion"))
                    if (text.isNotEmpty() && !suggestions.contains(text)) {
                        suggestions.add(text)
                    }
                }
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse search suggestions", e)
        }
        return suggestions
    }

    fun parseHomeSections(jsonStr: String?): List<InnerTubeSection> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val sections = mutableListOf<InnerTubeSection>()
        try {
            val root = JSONObject(jsonStr)
            val sectionList = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until sectionList.length()) {
                val sectionObj = sectionList.optJSONObject(i) ?: continue
                sectionObj.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
                    runCatching {
                        val headerRenderer = carousel.optJSONObject("header")
                            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        val headerText = extractRunsText(headerRenderer?.optJSONObject("title")).ifBlank { "Featured" }
                        val headerBrowseId = headerRenderer?.optJSONObject("moreContentButton")
                            ?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.isNotBlank() }

                        val itemsArray = carousel.optJSONArray("contents") ?: return@runCatching
                        val tracks = mutableListOf<RezonaTrack>()
                        for (j in 0 until itemsArray.length()) {
                            val item = itemsArray.optJSONObject(j) ?: continue
                            item.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
                                parseTwoRowItem(twoRow, tracks.size)?.let { tracks.add(it) }
                            }
                            item.optJSONObject("musicResponsiveListItemRenderer")?.let { responsive ->
                                parseResponsiveListItem(responsive, tracks.size)?.let { tracks.add(it) }
                            }
                        }
                        if (tracks.isNotEmpty()) {
                            sections.add(InnerTubeSection(title = headerText, items = tracks, browseId = headerBrowseId))
                        }
                    }
                }

                sectionObj.optJSONObject("musicShelfRenderer")?.let { shelf ->
                    runCatching {
                        val title = extractRunsText(shelf.optJSONObject("title")).ifBlank { "Quick picks" }
                        val tracks = mutableListOf<RezonaTrack>()
                        parseMusicShelfContents(shelf) { it?.let { tracks.add(it.copy(index = tracks.size)) } }
                        if (tracks.isNotEmpty()) {
                            sections.add(InnerTubeSection(title = title, items = tracks))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse home sections", e)
        }
        return sections
    }

    fun parseMoodsAndGenres(jsonStr: String?): List<InnerTubeMoodCategory> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<InnerTubeMoodCategory>()
        val seen = HashSet<String>()
        try {
            val root = JSONObject(jsonStr)
            val sectionList = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until sectionList.length()) {
                val grid = sectionList.optJSONObject(i)?.optJSONObject("gridRenderer") ?: continue
                val items = grid.optJSONArray("items") ?: continue
                for (j in 0 until items.length()) {
                    val nav = items.optJSONObject(j)?.optJSONObject("musicNavigationButtonRenderer") ?: continue
                    val title = extractRunsText(nav.optJSONObject("buttonText"))
                    val ep = nav.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint") ?: continue
                    val browseId = ep.optString("browseId").orEmpty()
                    val params = ep.optString("params").takeIf { it.isNotBlank() }
                    if (title.isNotBlank() && browseId.isNotBlank() && seen.add(title)) {
                        list.add(InnerTubeMoodCategory(title = title, browseId = browseId, params = params))
                    }
                }
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse moods and genres", e)
        }
        return list
    }

    fun parseAlbumOrPlaylistPage(jsonStr: String?): InnerTubeAlbumPage? {
        if (jsonStr.isNullOrBlank()) return null
        try {
            val root = JSONObject(jsonStr)
            val twoCol = root.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
            val headerObj = twoCol?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")

            val title = extractRunsText(headerObj?.optJSONObject("title")).ifBlank { "Playlist" }
            val subtitle = extractRunsText(headerObj?.optJSONObject("subtitle"))
            val thumb = extractBestThumbnail(headerObj?.optJSONObject("thumbnail"))

            val tracks = mutableListOf<RezonaTrack>()
            val secondarySections = twoCol?.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: root.optJSONObject("contents")
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

            if (secondarySections != null) {
                for (i in 0 until secondarySections.length()) {
                    val sec = secondarySections.optJSONObject(i) ?: continue
                    val shelf = sec.optJSONObject("musicShelfRenderer")
                        ?: sec.optJSONObject("musicPlaylistShelfRenderer")
                        ?: continue
                    parseMusicShelfContents(shelf) { track ->
                        if (track != null) {
                            val resolved = if (track.coverArtUrl.isBlank()) track.copy(coverArtUrl = thumb) else track
                            tracks.add(resolved.copy(index = tracks.size))
                        }
                    }
                }
            }

            return InnerTubeAlbumPage(
                title = title,
                artist = subtitle.substringBefore(" • ").ifBlank { "YouTube Music" },
                thumbnailUrl = thumb,
                tracks = tracks
            )
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse album/playlist page", e)
            return null
        }
    }

    fun parseArtistPage(jsonStr: String?): InnerTubeArtistPage? {
        if (jsonStr.isNullOrBlank()) return null
        try {
            val root = JSONObject(jsonStr)
            val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")
            val name = extractRunsText(header?.optJSONObject("title")).ifBlank { "Artist" }
            val desc = extractRunsText(header?.optJSONObject("description"))
            val subs = extractRunsText(
                header?.optJSONObject("subscriptionButton")
                    ?.optJSONObject("subscribeButtonRenderer")
                    ?.optJSONObject("subscriberCountText")
            )
            val thumb = extractBestThumbnail(header?.optJSONObject("thumbnail"))

            val topSongs = mutableListOf<RezonaTrack>()
            val albums = mutableListOf<RezonaTrack>()
            val sectionList = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (sectionList != null) {
                for (i in 0 until sectionList.length()) {
                    val sec = sectionList.optJSONObject(i) ?: continue
                    sec.optJSONObject("musicShelfRenderer")?.let { shelf ->
                        parseMusicShelfContents(shelf) { t ->
                            t?.let { topSongs.add(it.copy(index = topSongs.size, artist = name)) }
                        }
                    }
                    sec.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
                        val arr = carousel.optJSONArray("contents") ?: return@let
                        for (k in 0 until arr.length()) {
                            arr.optJSONObject(k)?.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
                                parseTwoRowItem(twoRow, albums.size)?.let { albums.add(it) }
                            }
                        }
                    }
                }
            }

            return InnerTubeArtistPage(
                name = name,
                description = desc,
                thumbnailUrl = thumb,
                subscribers = subs,
                topSongs = topSongs,
                albums = albums
            )
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse artist page", e)
            return null
        }
    }

    fun parseLyricsBrowseId(nextJsonStr: String?): String? {
        if (nextJsonStr.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(nextJsonStr)
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                val browseId = tab.optJSONObject("endpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.startsWith("MPLYt") }
                if (browseId != null) return browseId
            }
            null
        }.getOrNull()
    }

    fun parseLyricsText(browseJsonStr: String?): String? {
        if (browseJsonStr.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(browseJsonStr)
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return null
            for (i in 0 until contents.length()) {
                val desc = contents.optJSONObject(i)?.optJSONObject("musicDescriptionShelfRenderer") ?: continue
                val text = extractRunsText(desc.optJSONObject("description"))
                if (text.isNotBlank()) return text
            }
            null
        }.getOrNull()
    }

    fun parseNextQueue(jsonStr: String?): List<RezonaTrack> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val tracks = mutableListOf<RezonaTrack>()
        try {
            val root = JSONObject(jsonStr)
            val panelContents = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until panelContents.length()) {
                val item = panelContents.optJSONObject(i) ?: continue
                val videoRenderer = item.optJSONObject("playlistPanelVideoRenderer") ?: continue
                val videoId = videoRenderer.optString("videoId")
                if (videoId.isNullOrBlank()) continue

                val title = extractRunsText(videoRenderer.optJSONObject("title"))
                val artist = extractRunsText(videoRenderer.optJSONObject("shortBylineText"))
                val duration = extractRunsText(videoRenderer.optJSONObject("lengthText"))
                val thumbnail = extractBestThumbnail(videoRenderer.optJSONObject("thumbnail"))

                tracks.add(
                    RezonaTrack(
                        id = videoId,
                        index = tracks.size,
                        title = title.ifBlank { "Unknown Track" },
                        artist = artist.ifBlank { "YouTube Music" },
                        album = "YouTube Music",
                        duration = duration.ifBlank { "3:30" },
                        source = "YouTube Music",
                        coverArtUrl = thumbnail,
                        streamUrl = "https://www.youtube.com/watch?v=$videoId"
                    )
                )
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to parse watch next queue", e)
        }
        return tracks
    }

    private fun parseMusicCardShelf(card: JSONObject, index: Int): RezonaTrack? {
        val title = extractRunsText(card.optJSONObject("title"))
        if (title.isBlank()) return null

        val subtitle = extractRunsText(card.optJSONObject("subtitle"))
        val videoId = card.optJSONObject("onTap")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: card.optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: card.optJSONArray("buttons")
                ?.optJSONObject(0)
                ?.optJSONObject("buttonRenderer")
                ?.optJSONObject("command")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: card.optJSONObject("onTap")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.isNotBlank() }
            ?: return null

        val thumbnail = extractBestThumbnail(card.optJSONObject("thumbnail"))
        val cleanedArtist = subtitle
            .replace("Song • ", "")
            .replace("Video • ", "")
            .substringBefore(" • ")
            .ifBlank { "YouTube Music" }

        return RezonaTrack(
            id = videoId,
            index = index,
            title = title,
            artist = cleanedArtist,
            album = "YouTube Music",
            duration = extractDurationFromRuns(card.optJSONObject("subtitle")) ?: "3:30",
            source = "YouTube Music",
            coverArtUrl = thumbnail,
            streamUrl = if (videoId.length == 11) "https://www.youtube.com/watch?v=$videoId" else ""
        )
    }

    private fun parseResponsiveListItem(item: JSONObject, index: Int): RezonaTrack? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        val col0Text = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val title = extractRunsText(col0Text)
        if (title.isBlank()) return null

        val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: col0Text?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: item.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }

        val browseId = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }
        val effectiveId = videoId ?: browseId ?: return null

        var artist = "YouTube Music"
        var album = ""
        var duration = "3:30"

        if (flexColumns.length() > 1) {
            val col1 = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
            val runs = col1?.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val tokens = mutableListOf<String>()
                for (r in 0 until runs.length()) {
                    val t = runs.optJSONObject(r)?.optString("text")?.trim().orEmpty()
                    if (t.isNotEmpty() && t != "•" && t != "·" && t != "&" && t != ",") {
                        tokens.add(t)
                    }
                }
                val filteredTokens = tokens.filterNot {
                    it.equals("Song", ignoreCase = true) ||
                        it.equals("Video", ignoreCase = true) ||
                        it.equals("Album", ignoreCase = true) ||
                        it.equals("Artist", ignoreCase = true) ||
                        it.equals("Playlist", ignoreCase = true) ||
                        it.equals("Single", ignoreCase = true) ||
                        it.equals("EP", ignoreCase = true)
                }
                if (filteredTokens.isNotEmpty()) artist = filteredTokens[0]
                for (token in filteredTokens.drop(1)) {
                    if (token.matches(Regex("""^\d+:\d{2}(:\d{2})?$"""))) {
                        duration = token
                    } else if (album.isEmpty()) {
                        album = token
                    }
                }
            }
        }

        val thumbnail = extractBestThumbnail(item.optJSONObject("thumbnail"))
        val streamUrl = if (videoId != null) "https://www.youtube.com/watch?v=$videoId" else ""

        return RezonaTrack(
            id = effectiveId,
            index = index,
            title = title,
            artist = artist.ifBlank { "YouTube Music" },
            album = album.ifBlank { "YouTube Music" },
            duration = duration,
            source = "YouTube Music",
            coverArtUrl = thumbnail,
            streamUrl = streamUrl
        )
    }

    private fun parseTwoRowItem(item: JSONObject, index: Int): RezonaTrack? {
        val videoId = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: item.optJSONObject("thumbnailOverlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: item.optJSONObject("thumbnailOverlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() }

        val browseId = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }
        val effectiveId = videoId ?: browseId ?: return null

        val title = extractRunsText(item.optJSONObject("title"))
        if (title.isBlank()) return null

        val artist = extractRunsText(item.optJSONObject("subtitle"))
            .substringBefore(" • ")
            .ifBlank { "YouTube Music" }
        val thumbnail = extractBestThumbnail(item.optJSONObject("thumbnailRenderer"))
        val streamUrl = if (videoId != null && !videoId.startsWith("RD") && !videoId.startsWith("VL") && !videoId.startsWith("PL") && !videoId.startsWith("OL")) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            ""
        }

        return RezonaTrack(
            id = effectiveId,
            index = index,
            title = title,
            artist = artist,
            album = "YouTube Music",
            duration = "3:30",
            source = "YouTube Music",
            coverArtUrl = thumbnail,
            streamUrl = streamUrl
        )
    }

    private fun extractDurationFromRuns(obj: JSONObject?): String? {
        val runs = obj?.optJSONArray("runs") ?: return null
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text")?.trim().orEmpty()
            if (t.matches(Regex("""^\d+:\d{2}(:\d{2})?$"""))) return t
        }
        return null
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun extractBestThumbnail(thumbObj: JSONObject?): String {
        if (thumbObj == null) return ""
        val musicThumb = thumbObj.optJSONObject("musicThumbnailRenderer") ?: thumbObj
        val thumbContainer = musicThumb.optJSONObject("thumbnail") ?: musicThumb
        val thumbnails = thumbContainer.optJSONArray("thumbnails") ?: return ""

        var bestUrl = ""
        var maxWidth = 0
        for (i in 0 until thumbnails.length()) {
            val t = thumbnails.optJSONObject(i) ?: continue
            val w = t.optInt("width", 0)
            val url = t.optString("url").orEmpty()
            if (w >= maxWidth && url.isNotBlank()) {
                maxWidth = w
                bestUrl = url
            }
        }
        if (bestUrl.startsWith("//")) bestUrl = "https:$bestUrl"
        bestUrl = bestUrl
            .replace(Regex("=w\\d+-h\\d+[^&#?]*"), "=w1080-h1080-l90-rj")
            .replace(Regex("=s\\d+[^&#?]*"), "=s1080-c-k-c0x00ffffff-no-rj")
        return bestUrl
    }
}
