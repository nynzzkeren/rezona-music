package com.nayama.music.data

import com.nayama.music.model.Album
import com.nayama.music.model.Artist
import com.nayama.music.model.GenreCategory
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track

/**
 * Visual presentation dataset for Phase 1 UI validation.
 * IMPORTANT: This data is strictly for visual layout testing and is clearly labeled as sample data.
 * Live online music providers will be integrated in Phase 2.
 */
object SamplePresentationData {

    const val PHASE_1_DISCLAIMER = "Phase 1 Visual Preview — Provider streaming integrates in Phase 2"

    // O - Reverie Curated "Panggilan Cepat" (Quick Access Grid)
    val quickAccessTracks = listOf(
        Track(
            id = "ytm_ini_abadi",
            title = "Ini Abadi",
            artist = "For Revenge",
            album = "Perayaan Patah Hati",
            durationSeconds = 246,
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_teh_hijau",
            title = "Teh Hijau",
            artist = "Danilla",
            album = "Telisik",
            durationSeconds = 212,
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_sesi_potret",
            title = "Sesi Potret",
            artist = "Juicy Luicy",
            album = "Sentimental",
            durationSeconds = 228,
            artworkUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_dunia_yang_nanti",
            title = "Dunia Yang Nanti",
            artist = "Raim Laode",
            album = "Dunia Yang Nanti",
            durationSeconds = 255,
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_jangan_paksa_rasa",
            title = "Jangan Paksa Rasa",
            artist = "Perunggu",
            album = "Memorandum",
            durationSeconds = 270,
            artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_terbelah_jadi_dua",
            title = "Terbelah Jadi Dua",
            artist = "Nadin Amizah",
            album = "Untuk Dunia, Cinta, dan Kotornya",
            durationSeconds = 284,
            artworkUrl = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_what_if_i_call",
            title = "What If I Call",
            artist = "Forrest Frank",
            album = "CHILD OF GOD",
            durationSeconds = 175,
            artworkUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        ),
        Track(
            id = "ytm_percuma",
            title = "Percuma",
            artist = "Bernadya",
            album = "Sialnya, Hidup Harus Tetap Berjalan",
            durationSeconds = 239,
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "YouTube Music"
        )
    )

    // O - Reverie Curated "Dari Komunitas" (Community Stage & Live Sessions)
    val communityTracks = listOf(
        Track(
            id = "ytm_comm_1",
            title = "Perunggu - Close Fest, Unpad Bandung",
            artist = "PERUNGGU OFFICIAL",
            album = "Live Session Bandung",
            durationSeconds = 345,
            artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "Komunitas"
        ),
        Track(
            id = "ytm_comm_2",
            title = "Perunggu - Per Hari Ini (Live Acoustic)",
            artist = "PERUNGGU OFFICIAL",
            album = "Memorandum Live",
            durationSeconds = 310,
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "Komunitas"
        ),
        Track(
            id = "ytm_comm_3",
            title = "Perunggu - Pastikan Riuh Rendah",
            artist = "PERUNGGU OFFICIAL",
            album = "Memorandum",
            durationSeconds = 295,
            artworkUrl = "https://images.unsplash.com/photo-1520523839898-507125cd53c1?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "Komunitas"
        ),
        Track(
            id = "ytm_comm_4",
            title = "Perunggu - Rencana Usung Harapan",
            artist = "PERUNGGU OFFICIAL",
            album = "Live at Synchronize",
            durationSeconds = 330,
            artworkUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop&q=80",
            sourceAttribution = "Komunitas"
        )
    )

    val featuredTracks = listOf(
        Track(
            id = "demo_track_1",
            title = "Monochrome Odyssey",
            artist = "Aetherium Project",
            album = "Shadow & Light",
            durationSeconds = 234,
            sourceAttribution = "Sample Preview"
        ),
        Track(
            id = "demo_track_2",
            title = "Midnight Echoes",
            artist = "Nayama Collective",
            album = "Quiet Nocturne",
            durationSeconds = 198,
            sourceAttribution = "Sample Preview"
        ),
        Track(
            id = "demo_track_3",
            title = "Solitude in D Minor",
            artist = "Klara Lindqvist",
            album = "Acoustic Reflections",
            durationSeconds = 275,
            sourceAttribution = "Sample Preview"
        ),
        Track(
            id = "demo_track_4",
            title = "Subterranean Pulse",
            artist = "Vortex Zero",
            album = "Dark Matter Rhythm",
            durationSeconds = 312,
            sourceAttribution = "Sample Preview"
        ),
        Track(
            id = "demo_track_5",
            title = "Silver Horizon",
            artist = "Isochron",
            album = "Linear Drift",
            durationSeconds = 186,
            sourceAttribution = "Sample Preview"
        ),
        Track(
            id = "demo_track_6",
            title = "Velvet Static",
            artist = "Ghost Frequencies",
            album = "Analog Noir",
            durationSeconds = 245,
            sourceAttribution = "Sample Preview"
        )
    )

    val popularAlbums = listOf(
        Album(
            id = "demo_album_1",
            title = "Shadow & Light",
            artist = "Aetherium Project",
            releaseYear = "2026",
            trackCount = 10
        ),
        Album(
            id = "demo_album_2",
            title = "Quiet Nocturne",
            artist = "Nayama Collective",
            releaseYear = "2025",
            trackCount = 12
        ),
        Album(
            id = "demo_album_3",
            title = "Linear Drift",
            artist = "Isochron",
            releaseYear = "2026",
            trackCount = 8
        ),
        Album(
            id = "demo_album_4",
            title = "Analog Noir",
            artist = "Ghost Frequencies",
            releaseYear = "2025",
            trackCount = 9
        )
    )

    val featuredArtists = listOf(
        Artist(
            id = "demo_artist_1",
            name = "Aetherium Project",
            genre = "Cinematic Ambient",
            monthlyListeners = "1.4M"
        ),
        Artist(
            id = "demo_artist_2",
            name = "Nayama Collective",
            genre = "Modern Classical",
            monthlyListeners = "890K"
        ),
        Artist(
            id = "demo_artist_3",
            name = "Ghost Frequencies",
            genre = "Lo-Fi Noir",
            monthlyListeners = "2.1M"
        ),
        Artist(
            id = "demo_artist_4",
            name = "Isochron",
            genre = "Minimal Electronic",
            monthlyListeners = "640K"
        )
    )

    val curatedPlaylists = listOf(
        Playlist(
            id = "demo_playlist_1",
            title = "Monochrome Focus",
            description = "Deep ambient textures and hypnotic minimalist rhythms for intense focus.",
            trackCount = 42,
            isUserCreated = false
        ),
        Playlist(
            id = "demo_playlist_2",
            title = "Nocturnal Echoes",
            description = "Dark jazz, acoustic strings, and low-frequency pulses after midnight.",
            trackCount = 28,
            isUserCreated = false
        ),
        Playlist(
            id = "demo_playlist_3",
            title = "Pure Soundscapes",
            description = "Uncompressed acoustic instruments and analog tape recordings.",
            trackCount = 35,
            isUserCreated = false
        )
    )

    val userPlaylists = listOf(
        Playlist(
            id = "user_pl_1",
            title = "Late Night Coding",
            description = "Personal collection of dark ambient and lo-fi tracks.",
            trackCount = 18,
            isUserCreated = true
        ),
        Playlist(
            id = "user_pl_2",
            title = "Morning Solitude",
            description = "Calm acoustic pieces for early morning clarity.",
            trackCount = 24,
            isUserCreated = true
        )
    )

    val exploreCategories = listOf(
        GenreCategory("cat_1", "Minimal Ambient", "Atmospheric & Spatial", "AMBIENT"),
        GenreCategory("cat_2", "Dark Techno", "Deep Raw Rhythms", "TECHNO"),
        GenreCategory("cat_3", "Neo-Classical", "Modern Piano & Strings", "CLASSICAL"),
        GenreCategory("cat_4", "Lo-Fi Noir", "Tape Hiss & Melancholy", "LO-FI"),
        GenreCategory("cat_5", "Deep Electronic", "Synthetic Textures", "ELECTRONIC"),
        GenreCategory("cat_6", "Acoustic Noir", "Unplugged & Raw", "ACOUSTIC"),
        GenreCategory("cat_7", "Post-Rock", "Crescendos & Echoes", "POST-ROCK"),
        GenreCategory("cat_8", "Experimental", "Avant-Garde Sonic Art", "EXPERIMENTAL")
    )

    val recentSearches = listOf(
        "Aetherium Project",
        "Monochrome Focus",
        "Minimal Techno",
        "Quiet Nocturne"
    )
}

