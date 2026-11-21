package com.nayama.music.model

/**
 * Rezona Track Model for Home and Playback Presentation
 */
data class RezonaTrack(
    val id: String,
    val index: Int = 0,
    val title: String,
    val artist: String,
    val duration: String = "3:30",
    val mood: String = "Chill", // "Chill", "Focus", "Party", "Energy", "Late Night"
    val progressFraction: Float = 0f,
    val isExplicit: Boolean = false,
    val playCount: String = "12k",
    val album: String = "",
    val source: String = "Local", // "YouTube Music", "SoundCloud", "Spotify", "Local"
    val coverArtUrl: String = "",
    val streamUrl: String = "",
    val matchedIds: List<String> = emptyList()
)

object RezonaMockRepository {
    val allTracks: List<RezonaTrack> = listOf(
        RezonaTrack("trk_01", 0, "Glass Resonance", "Echo Atlas", "4:12", "Chill", 0.68f, playCount = "34 plays"),
        RezonaTrack("trk_02", 1, "Obsidian Drift", "Null Carrier", "3:47", "Focus", 0.22f, playCount = "28 plays"),
        RezonaTrack("trk_03", 2, "Lunar Static", "Prism Void", "5:03", "Late Night", 0.85f, playCount = "25 plays"),
        RezonaTrack("trk_04", 3, "Thermal Haze", "Deep Signal", "3:28", "Energy", 0.10f, playCount = "19 plays"),
        RezonaTrack("trk_05", 4, "Velvet Circuit", "Monochrome Lab", "4:55", "Party", 0.45f, playCount = "16 plays"),
        RezonaTrack("trk_06", 5, "Carved from Silence", "Hollow Depths", "6:14", "Chill", 0.92f, playCount = "14 plays"),
        RezonaTrack("trk_07", 6, "Subsurface Drift", "Null Carrier", "3:50", "Focus", 0.33f, playCount = "42 plays"),
        RezonaTrack("trk_08", 7, "Phase Inversion", "Echo Atlas", "4:33", "Late Night", 0.15f, playCount = "38 plays"),
        RezonaTrack("trk_09", 8, "Matte Black Sky", "Prism Void", "5:21", "Chill", 0.50f, playCount = "31 plays"),
        RezonaTrack("trk_10", 9, "Cold Plate Reverb", "Signal Arc", "3:59", "Focus", 0.77f, playCount = "29 plays"),
        RezonaTrack("trk_11", 10, "Iron Lace", "Monochrome Lab", "4:08", "Energy", 0.40f, playCount = "22 plays"),
        RezonaTrack("trk_12", 11, "Soft Interference", "Deep Signal", "3:37", "Late Night", 0.60f, playCount = "18 plays"),
        RezonaTrack("trk_13", 12, "Bioluminescent Tide", "Kōan Systems", "4:42", "Chill", 0.25f, playCount = "52 plays"),
        RezonaTrack("trk_14", 13, "Achromatic Horizon", "Sylvan Ray", "3:15", "Focus", 0.05f, playCount = "47 plays"),
        RezonaTrack("trk_15", 14, "Sub-bass Sanctuary", "Modular Ghost", "5:30", "Party", 0.18f, playCount = "36 plays"),
        RezonaTrack("trk_16", 15, "Kinetic Silver", "Vektor Wave", "3:45", "Energy", 0.88f, playCount = "33 plays"),
        RezonaTrack("trk_17", 16, "Ghost Frequency", "Tape & Wire", "4:02", "Late Night", 0.72f, playCount = "27 plays"),
        RezonaTrack("trk_18", 17, "Acoustic Mirage", "Soren Kjell", "4:19", "Chill", 0.35f, playCount = "24 plays"),
        RezonaTrack("trk_19", 18, "Monolith Pulse", "Blackout Unit", "3:55", "Energy", 0.65f, playCount = "21 plays"),
        RezonaTrack("trk_20", 19, "Spectral Reflections", "Glass Canopy", "6:02", "Focus", 0.12f, playCount = "19 plays"),
        RezonaTrack("trk_21", 20, "Carbon Cathedral", "Archon Dusk", "4:48", "Late Night", 0.44f, playCount = "45 plays"),
        RezonaTrack("trk_22", 21, "Analog Dawn", "Solar Needle", "3:39", "Chill", 0.58f, playCount = "39 plays"),
        RezonaTrack("trk_23", 22, "Vapor Velocity", "Chrome Cascade", "3:51", "Party", 0.31f, playCount = "35 plays"),
        RezonaTrack("trk_24", 23, "Distant Sirens", "Pale Orbit", "4:24", "Late Night", 0.70f, playCount = "30 plays"),
        RezonaTrack("trk_25", 24, "Ceramic Chamber", "Forma Studio", "3:18", "Focus", 0.15f, playCount = "26 plays"),
        RezonaTrack("trk_26", 25, "Neptune Echoes", "Hydronaut", "5:12", "Chill", 0.82f, playCount = "23 plays"),
        RezonaTrack("trk_27", 26, "Kinetic Voltage", "Ion Shift", "3:33", "Energy", 0.90f, playCount = "20 plays"),
        RezonaTrack("trk_28", 27, "Darkroom Solitude", "Silver Halide", "4:06", "Late Night", 0.28f, playCount = "17 plays"),
        RezonaTrack("trk_29", 28, "Resonant Decay", "Milled Glass", "4:37", "Focus", 0.53f, playCount = "41 plays"),
        RezonaTrack("trk_30", 29, "Luminescent Orbit", "Zero Gravity", "4:15", "Party", 0.62f, playCount = "37 plays"),
        RezonaTrack("trk_31", 30, "Static Bloom", "North Meridian", "3:58", "Chill", 0.47f, playCount = "32 plays"),
        RezonaTrack("trk_32", 31, "Mercury Vapor", "Cyanide Arc", "4:29", "Energy", 0.19f, playCount = "28 plays"),
        RezonaTrack("trk_33", 32, "Shadow Cascade", "Obsidian Trio", "5:41", "Late Night", 0.84f, playCount = "25 plays"),
        RezonaTrack("trk_34", 33, "Floating Architecture", "Koto & Ribbon", "3:24", "Focus", 0.38f, playCount = "21 plays"),
        RezonaTrack("trk_35", 34, "Hyperbaric Chamber", "Depth Gauge", "4:50", "Chill", 0.76f, playCount = "18 plays"),
        RezonaTrack("trk_36", 35, "Pulse Code Modulation", "Bitstream Core", "3:40", "Energy", 0.63f, playCount = "48 plays"),
        RezonaTrack("trk_37", 36, "Nocturne on Glass", "Elena Vane", "4:11", "Late Night", 0.41f, playCount = "44 plays"),
        RezonaTrack("trk_38", 37, "Synaptic Delay", "Neural Rhythm", "3:52", "Party", 0.29f, playCount = "39 plays"),
        RezonaTrack("trk_39", 38, "Tungsten Wire", "Filament", "3:29", "Focus", 0.55f, playCount = "35 plays"),
        RezonaTrack("trk_40", 39, "Solar Wind Symphony", "Aurora Station", "6:22", "Chill", 0.16f, playCount = "31 plays"),
        RezonaTrack("trk_41", 40, "Zero Meridian", "Null Vector", "4:04", "Energy", 0.81f, playCount = "27 plays"),
        RezonaTrack("trk_42", 41, "Event Horizon", "Void Walker", "5:18", "Late Night", 0.94f, playCount = "24 plays")
    )

    fun getTrack(index: Int): RezonaTrack {
        return allTracks[index.coerceIn(0, allTracks.size - 1)]
    }

    fun getTracksForMood(mood: String): List<RezonaTrack> {
        return allTracks.filter { it.mood.equals(mood, ignoreCase = true) }
    }
}
