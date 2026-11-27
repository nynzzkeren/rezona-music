package com.nayama.music.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rezona_settings")

enum class LibraryLayoutMode {
    GRID, LIST
}

enum class LibrarySortOrder {
    RECENT, ALPHABETICAL
}

class RezonaPreferences(private val context: Context) {
    companion object {
        val KEY_LAYOUT_MODE = stringPreferencesKey("library_layout_mode")
        val KEY_SORT_ORDER = stringPreferencesKey("library_sort_order")
        val KEY_FILTER_TAB = stringPreferencesKey("library_filter_tab")
        val KEY_OFFLINE_CACHE_ENABLED = booleanPreferencesKey("offline_cache_enabled")

        // Audio & Playback Settings
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("audio_crossfade_seconds")
        val KEY_GAPLESS_ENABLED = booleanPreferencesKey("audio_gapless_enabled")
        val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_stream_quality")
        val KEY_CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val KEY_IMAGE_CACHE_LIMIT_MB = intPreferencesKey("image_cache_limit_mb")
        val KEY_SOUND_PRESET = stringPreferencesKey("audio_sound_preset")
        val KEY_SCRUBBER_STYLE = stringPreferencesKey("player_scrubber_style")

        // Appearance Settings
        val KEY_GLASS_INTENSITY = stringPreferencesKey("ui_glass_intensity")
        val KEY_GLASS_OPACITY_PERCENT = intPreferencesKey("ui_glass_opacity_percent")
        val KEY_GLASS_BLUR_RADIUS_DP = intPreferencesKey("ui_glass_blur_radius_dp")
        val KEY_AMBIENT_VIVID_PLAYER = booleanPreferencesKey("ui_ambient_vivid_player")
        val KEY_DOCK_FOLD_ON_SCROLL = booleanPreferencesKey("ui_dock_fold_on_scroll")
        val KEY_DYNAMIC_ISLAND_ENABLED = booleanPreferencesKey("ui_dynamic_island_enabled")
        val KEY_DYNAMIC_ISLAND_CUTOUT_MODE = stringPreferencesKey("ui_dynamic_island_cutout_mode")
        val KEY_IOS_SIDE_PLAYER_ENABLED = booleanPreferencesKey("ui_ios_side_player_enabled")

        // Smart Playback, Bluetooth Auto-Play, Background Keep-Alive & Music Alarm
        val KEY_AUTO_PLAY_BLUETOOTH = booleanPreferencesKey("playback_auto_play_bluetooth")
        val KEY_KEEP_ALIVE_ON_SWIPE = booleanPreferencesKey("playback_keep_alive_on_swipe")
        val KEY_MUSIC_ALARM_ENABLED = booleanPreferencesKey("music_alarm_enabled")
        val KEY_MUSIC_ALARM_HOUR = intPreferencesKey("music_alarm_hour")
        val KEY_MUSIC_ALARM_MINUTE = intPreferencesKey("music_alarm_minute")

        // Lyrics & Translation Settings
        val KEY_LYRICS_TRANSLATION_ENABLED = booleanPreferencesKey("lyrics_translation_enabled")
        val KEY_LYRICS_ROMANIZATION_ENABLED = booleanPreferencesKey("lyrics_romanization_enabled")
        val KEY_LYRICS_BLUR_UNFOCUSED = booleanPreferencesKey("lyrics_blur_unfocused")
        val KEY_LYRICS_CENTER_ALIGN = booleanPreferencesKey("lyrics_center_align")
        val KEY_LYRICS_OFFSET_MS = intPreferencesKey("lyrics_timing_offset_ms")
        val KEY_LYRICS_FONT_FAMILY = stringPreferencesKey("lyrics_font_family")
        val KEY_LYRICS_FONT_WEIGHT = stringPreferencesKey("lyrics_font_weight")
        val KEY_LYRICS_FONT_SIZE_SP = floatPreferencesKey("lyrics_font_size_sp")
        val KEY_LYRICS_LINE_SPACING = stringPreferencesKey("lyrics_line_spacing")
        val KEY_LYRICS_ITALIC = booleanPreferencesKey("lyrics_italic")
        val KEY_LYRICS_LETTER_SPACING = floatPreferencesKey("lyrics_letter_spacing_em")
        val KEY_LYRICS_TRANSLATION_SIZE_SP = floatPreferencesKey("lyrics_translation_size_sp")

        // Video Playback Settings (Part 3)
        val KEY_VIDEO_QUALITY = stringPreferencesKey("video_stream_quality")
        val KEY_VIDEO_HQ_WIFI_ONLY = booleanPreferencesKey("video_hq_wifi_only")

        // Skip Intro & Update Notifier
        val KEY_SKIP_INTRO_ENABLED = booleanPreferencesKey("playback_skip_intro_enabled")
        val KEY_DISMISSED_UPDATE_VERSION = intPreferencesKey("update_dismissed_version_code")
        val KEY_DISMISSED_ANNOUNCEMENT_ID = stringPreferencesKey("update_dismissed_announcement_id")
        val KEY_LAST_SEEN_APP_VERSION_CODE = intPreferencesKey("update_last_seen_app_version_code")

        // Privacy & History
        val KEY_PAUSE_HISTORY = booleanPreferencesKey("privacy_pause_history")

        // Mood & Echo (Legacy Sound Lab)
        val KEY_MOOD_DIAL_ANGLE = floatPreferencesKey("mood_dial_angle")
        val KEY_ECHO_PROFILE = stringPreferencesKey("echo_mode_profile")
        val KEY_ECHO_INTENSITY = floatPreferencesKey("echo_mode_intensity")

        // One-time v2 seed clutter cleanup
        val KEY_CLEANED_DEFAULT_SEEDS_V2 = booleanPreferencesKey("db_cleaned_default_seeds_v2")

        // Auth & Account Identity (Firebase Auth UID + Guest State)
        val KEY_AUTH_GATE_SEEN = booleanPreferencesKey("auth_gate_seen")
        val KEY_AUTH_PROVIDER = stringPreferencesKey("auth_provider")
        val KEY_FIREBASE_UID = stringPreferencesKey("auth_firebase_uid")
        val KEY_GOOGLE_SIGNED_IN = booleanPreferencesKey("auth_google_signed_in")
        val KEY_GOOGLE_EMAIL = stringPreferencesKey("auth_google_email")
        val KEY_GOOGLE_DISPLAY_NAME = stringPreferencesKey("auth_google_display_name")

        // Cloud Sync Status Metadata
        val KEY_LAST_SYNC_PUSH_MS = stringPreferencesKey("sync_last_push_ms")
        val KEY_LAST_SYNC_PULL_MS = stringPreferencesKey("sync_last_pull_ms")
        val KEY_LAST_SYNC_ERROR = stringPreferencesKey("sync_last_error")
        val KEY_SETTINGS_UPDATED_AT = stringPreferencesKey("sync_settings_updated_at")
        val KEY_HANDLED_GUEST_UIDS_JSON = stringPreferencesKey("sync_handled_guest_uids")

        @Volatile
        var onSettingsMutated: (() -> Unit)? = null
    }

    data class GoogleAccountState(
        val isSignedIn: Boolean = false,
        val uid: String = "",
        val email: String = "",
        val displayName: String = "",
        val provider: String = "guest"
    )

    data class SyncPrefsMetadata(
        val lastPushAtMs: Long = 0L,
        val lastPullAtMs: Long = 0L,
        val lastError: String? = null,
        val settingsUpdatedAtMs: Long = 0L
    )

    private val authJson = Json { ignoreUnknownKeys = true }

    val cleanupDefaultSeedsV2DoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLEANED_DEFAULT_SEEDS_V2] ?: false
    }

    val authGateSeenFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTH_GATE_SEEN] ?: false
    }

    val googleAccountFlow: Flow<GoogleAccountState> = context.dataStore.data.map { prefs ->
        val signedIn = prefs[KEY_GOOGLE_SIGNED_IN] ?: false
        val uid = prefs[KEY_FIREBASE_UID].orEmpty()
        val email = prefs[KEY_GOOGLE_EMAIL].orEmpty()
        val name = prefs[KEY_GOOGLE_DISPLAY_NAME].orEmpty()
        val provider = prefs[KEY_AUTH_PROVIDER] ?: if (signedIn && uid.isNotBlank()) "firebase" else "guest"
        GoogleAccountState(
            isSignedIn = signedIn && uid.isNotBlank(),
            uid = uid,
            email = email,
            displayName = name,
            provider = provider
        )
    }

    val syncMetadataFlow: Flow<SyncPrefsMetadata> = context.dataStore.data.map { prefs ->
        SyncPrefsMetadata(
            lastPushAtMs = prefs[KEY_LAST_SYNC_PUSH_MS]?.toLongOrNull() ?: 0L,
            lastPullAtMs = prefs[KEY_LAST_SYNC_PULL_MS]?.toLongOrNull() ?: 0L,
            lastError = prefs[KEY_LAST_SYNC_ERROR]?.takeIf { it.isNotBlank() },
            settingsUpdatedAtMs = prefs[KEY_SETTINGS_UPDATED_AT]?.toLongOrNull() ?: 0L
        )
    }

    val layoutModeFlow: Flow<LibraryLayoutMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_LAYOUT_MODE]) {
            LibraryLayoutMode.LIST.name -> LibraryLayoutMode.LIST
            else -> LibraryLayoutMode.GRID
        }
    }

    val sortOrderFlow: Flow<LibrarySortOrder> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_SORT_ORDER]) {
            LibrarySortOrder.ALPHABETICAL.name -> LibrarySortOrder.ALPHABETICAL
            else -> LibrarySortOrder.RECENT
        }
    }

    val filterTabFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FILTER_TAB] ?: "All"
    }

    val crossfadeSecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CROSSFADE_SECONDS] ?: 4
    }

    val gaplessFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_GAPLESS_ENABLED] ?: true
    }

    val audioQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_QUALITY] ?: "High"
    }

    val cacheLimitMbFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CACHE_LIMIT_MB] ?: 1024
    }

    val imageCacheLimitMbFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMAGE_CACHE_LIMIT_MB] ?: 256
    }

    val soundPresetFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SOUND_PRESET] ?: "Normal"
    }

    val scrubberStyleFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SCRUBBER_STYLE] ?: "Line"
    }

    val glassIntensityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GLASS_INTENSITY] ?: "High"
    }

    val glassOpacityPercentFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_GLASS_OPACITY_PERCENT] ?: 72).coerceIn(25, 100)
    }

    val glassBlurRadiusDpFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_GLASS_BLUR_RADIUS_DP] ?: 22).coerceIn(0, 40)
    }

    val autoPlayBluetoothFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_PLAY_BLUETOOTH] ?: true
    }

    val keepAliveOnSwipeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEEP_ALIVE_ON_SWIPE] ?: false
    }

    val musicAlarmEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MUSIC_ALARM_ENABLED] ?: false
    }

    val musicAlarmHourFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_MUSIC_ALARM_HOUR] ?: 7).coerceIn(0, 23)
    }

    val musicAlarmMinuteFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_MUSIC_ALARM_MINUTE] ?: 0).coerceIn(0, 59)
    }

    val ambientVividPlayerFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AMBIENT_VIVID_PLAYER] ?: true
    }

    val dockFoldOnScrollFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOCK_FOLD_ON_SCROLL] ?: true
    }

    val lyricsTranslationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_TRANSLATION_ENABLED] ?: true
    }

    val lyricsRomanizationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_ROMANIZATION_ENABLED] ?: false
    }

    val lyricsBlurUnfocusedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_BLUR_UNFOCUSED] ?: true
    }

    val lyricsCenterAlignFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_CENTER_ALIGN] ?: false
    }

    val lyricsOffsetMsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_OFFSET_MS] ?: 0
    }

    val lyricsStyleFlow: Flow<com.nayama.music.ui.lyrics.LyricsStyleConfig> = context.dataStore.data.map { prefs ->
        com.nayama.music.ui.lyrics.LyricsStyleConfig(
            fontFamily = com.nayama.music.ui.lyrics.LyricsFontFamilyOption.fromId(prefs[KEY_LYRICS_FONT_FAMILY]),
            weight = com.nayama.music.ui.lyrics.LyricsFontWeightOption.fromId(prefs[KEY_LYRICS_FONT_WEIGHT]),
            fontSizeSp = (prefs[KEY_LYRICS_FONT_SIZE_SP] ?: 26f).coerceIn(20f, 40f),
            centerAlign = prefs[KEY_LYRICS_CENTER_ALIGN] ?: false,
            lineSpacing = com.nayama.music.ui.lyrics.LyricsLineSpacingOption.fromId(prefs[KEY_LYRICS_LINE_SPACING]),
            isItalic = prefs[KEY_LYRICS_ITALIC] ?: false,
            letterSpacingEm = (prefs[KEY_LYRICS_LETTER_SPACING] ?: -0.01f).coerceIn(-0.04f, 0.12f),
            translationSizeSp = (prefs[KEY_LYRICS_TRANSLATION_SIZE_SP] ?: 14f).coerceIn(11f, 22f)
        )
    }

    val videoQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIDEO_QUALITY] ?: "480p"
    }

    val videoHqWifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIDEO_HQ_WIFI_ONLY] ?: true
    }

    val skipIntroEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SKIP_INTRO_ENABLED] ?: true
    }

    val dismissedUpdateVersionFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISMISSED_UPDATE_VERSION] ?: 0
    }

    val dismissedAnnouncementIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISMISSED_ANNOUNCEMENT_ID] ?: ""
    }

    val lastSeenAppVersionCodeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SEEN_APP_VERSION_CODE] ?: 0
    }

    suspend fun setDismissedAnnouncementId(id: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DISMISSED_ANNOUNCEMENT_ID] = id }
    }

    suspend fun setLastSeenAppVersionCode(versionCode: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_SEEN_APP_VERSION_CODE] = versionCode }
    }

    val dynamicIslandEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_ISLAND_ENABLED] ?: false
    }

    val iosSidePlayerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IOS_SIDE_PLAYER_ENABLED] ?: false
    }

    // "Auto" | "Overlap" | "Below"
    val dynamicIslandCutoutModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_ISLAND_CUTOUT_MODE] ?: "Auto"
    }

    suspend fun setDynamicIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DYNAMIC_ISLAND_ENABLED] = enabled }
    }

    suspend fun setIosSidePlayerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_IOS_SIDE_PLAYER_ENABLED] = enabled }
    }

    suspend fun setDynamicIslandCutoutMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DYNAMIC_ISLAND_CUTOUT_MODE] = mode }
    }

    val pauseHistoryFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAUSE_HISTORY] ?: false
    }

    val moodDialAngleFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_MOOD_DIAL_ANGLE] ?: 0f
    }

    val echoProfileFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ECHO_PROFILE] ?: "Intimate Studio"
    }

    val echoIntensityFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_ECHO_INTENSITY] ?: 0.35f
    }

    suspend fun markDefaultSeedsV2Cleaned() {
        context.dataStore.edit { prefs -> prefs[KEY_CLEANED_DEFAULT_SEEDS_V2] = true }
    }

    private suspend fun editSyncedSetting(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs ->
            block(prefs)
            prefs[KEY_SETTINGS_UPDATED_AT] = System.currentTimeMillis().toString()
        }
        onSettingsMutated?.invoke()
    }

    suspend fun setLayoutMode(mode: LibraryLayoutMode) = editSyncedSetting { it[KEY_LAYOUT_MODE] = mode.name }

    suspend fun setSortOrder(order: LibrarySortOrder) = editSyncedSetting { it[KEY_SORT_ORDER] = order.name }

    suspend fun setFilterTab(tab: String) = editSyncedSetting { it[KEY_FILTER_TAB] = tab }

    suspend fun setCrossfadeSeconds(seconds: Int) = editSyncedSetting { it[KEY_CROSSFADE_SECONDS] = seconds }

    suspend fun setGaplessEnabled(enabled: Boolean) = editSyncedSetting { it[KEY_GAPLESS_ENABLED] = enabled }

    suspend fun setAudioQuality(quality: String) = editSyncedSetting { it[KEY_AUDIO_QUALITY] = quality }

    suspend fun setCacheLimitMb(limitMb: Int) = editSyncedSetting { it[KEY_CACHE_LIMIT_MB] = limitMb }

    suspend fun setImageCacheLimitMb(limitMb: Int) = editSyncedSetting { it[KEY_IMAGE_CACHE_LIMIT_MB] = limitMb }

    suspend fun setSoundPreset(preset: String) = editSyncedSetting { it[KEY_SOUND_PRESET] = preset }

    suspend fun setScrubberStyle(style: String) = editSyncedSetting { it[KEY_SCRUBBER_STYLE] = style }

    suspend fun setGlassIntensity(intensity: String) = editSyncedSetting { it[KEY_GLASS_INTENSITY] = intensity }

    suspend fun setGlassOpacityPercent(percent: Int) = editSyncedSetting {
        it[KEY_GLASS_OPACITY_PERCENT] = percent.coerceIn(25, 100)
    }

    suspend fun setGlassBlurRadiusDp(radiusDp: Int) = editSyncedSetting {
        it[KEY_GLASS_BLUR_RADIUS_DP] = radiusDp.coerceIn(0, 40)
    }

    suspend fun setAutoPlayBluetooth(enabled: Boolean) = editSyncedSetting {
        it[KEY_AUTO_PLAY_BLUETOOTH] = enabled
    }

    suspend fun setKeepAliveOnSwipe(enabled: Boolean) = editSyncedSetting {
        it[KEY_KEEP_ALIVE_ON_SWIPE] = enabled
    }

    suspend fun setMusicAlarm(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MUSIC_ALARM_ENABLED] = enabled
            prefs[KEY_MUSIC_ALARM_HOUR] = hour.coerceIn(0, 23)
            prefs[KEY_MUSIC_ALARM_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setAmbientVividPlayer(enabled: Boolean) = editSyncedSetting { it[KEY_AMBIENT_VIVID_PLAYER] = enabled }

    suspend fun setDockFoldOnScroll(enabled: Boolean) = editSyncedSetting { it[KEY_DOCK_FOLD_ON_SCROLL] = enabled }

    suspend fun setLyricsTranslationEnabled(enabled: Boolean) = editSyncedSetting { it[KEY_LYRICS_TRANSLATION_ENABLED] = enabled }

    suspend fun setLyricsRomanizationEnabled(enabled: Boolean) = editSyncedSetting { it[KEY_LYRICS_ROMANIZATION_ENABLED] = enabled }

    suspend fun setLyricsBlurUnfocused(enabled: Boolean) = editSyncedSetting { it[KEY_LYRICS_BLUR_UNFOCUSED] = enabled }

    suspend fun setLyricsCenterAlign(enabled: Boolean) = editSyncedSetting { it[KEY_LYRICS_CENTER_ALIGN] = enabled }

    suspend fun setLyricsOffsetMs(offsetMs: Int) = editSyncedSetting { it[KEY_LYRICS_OFFSET_MS] = offsetMs }

    suspend fun setLyricsStyle(style: com.nayama.music.ui.lyrics.LyricsStyleConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRICS_FONT_FAMILY] = style.fontFamily.id
            prefs[KEY_LYRICS_FONT_WEIGHT] = style.weight.id
            prefs[KEY_LYRICS_FONT_SIZE_SP] = style.fontSizeSp.coerceIn(20f, 40f)
            prefs[KEY_LYRICS_CENTER_ALIGN] = style.centerAlign
            prefs[KEY_LYRICS_LINE_SPACING] = style.lineSpacing.id
            prefs[KEY_LYRICS_ITALIC] = style.isItalic
            prefs[KEY_LYRICS_LETTER_SPACING] = style.letterSpacingEm.coerceIn(-0.04f, 0.12f)
            prefs[KEY_LYRICS_TRANSLATION_SIZE_SP] = style.translationSizeSp.coerceIn(11f, 22f)
        }
    }

    suspend fun resetLyricsStyle() = setLyricsStyle(com.nayama.music.ui.lyrics.LyricsStyleConfig())

    suspend fun setVideoQuality(quality: String) {
        context.dataStore.edit { it[KEY_VIDEO_QUALITY] = quality }
    }

    suspend fun setVideoHqWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIDEO_HQ_WIFI_ONLY] = enabled }
    }

    suspend fun setSkipIntroEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SKIP_INTRO_ENABLED] = enabled }
    }

    suspend fun setDismissedUpdateVersion(versionCode: Int) {
        context.dataStore.edit { it[KEY_DISMISSED_UPDATE_VERSION] = versionCode }
    }

    suspend fun setPauseHistory(paused: Boolean) = editSyncedSetting { it[KEY_PAUSE_HISTORY] = paused }

    suspend fun setMoodDialAngle(angle: Float) = editSyncedSetting { it[KEY_MOOD_DIAL_ANGLE] = angle }

    suspend fun setEchoProfile(profile: String) = editSyncedSetting { it[KEY_ECHO_PROFILE] = profile }

    suspend fun setEchoIntensity(intensity: Float) = editSyncedSetting { it[KEY_ECHO_INTENSITY] = intensity }

    suspend fun exportSettingsForCloudSync(updatedAt: Long = System.currentTimeMillis()): Map<String, Any> {
        val prefs = context.dataStore.data.first()
        val effectiveUpdated = (prefs[KEY_SETTINGS_UPDATED_AT]?.toLongOrNull() ?: updatedAt).coerceAtLeast(1L)
        return linkedMapOf(
            "updatedAt" to effectiveUpdated,
            "schemaVersion" to 1,
            "deleted" to false,
            "libraryLayoutMode" to (prefs[KEY_LAYOUT_MODE] ?: LibraryLayoutMode.GRID.name).take(32),
            "librarySortOrder" to (prefs[KEY_SORT_ORDER] ?: LibrarySortOrder.RECENT.name).take(32),
            "libraryFilterTab" to (prefs[KEY_FILTER_TAB] ?: "All").take(64),
            "audioCrossfadeSeconds" to (prefs[KEY_CROSSFADE_SECONDS] ?: 4).coerceIn(0, 30),
            "audioGaplessEnabled" to (prefs[KEY_GAPLESS_ENABLED] ?: true),
            "audioQuality" to (prefs[KEY_AUDIO_QUALITY] ?: "High").take(32),
            "cacheLimitMb" to (prefs[KEY_CACHE_LIMIT_MB] ?: 1024).coerceIn(0, 102400),
            "imageCacheLimitMb" to (prefs[KEY_IMAGE_CACHE_LIMIT_MB] ?: 256).coerceIn(0, 10240),
            "soundPreset" to (prefs[KEY_SOUND_PRESET] ?: "Normal").take(64),
            "scrubberStyle" to (prefs[KEY_SCRUBBER_STYLE] ?: "Line").take(32),
            "glassIntensity" to (prefs[KEY_GLASS_INTENSITY] ?: "Low").take(32),
            "ambientVividPlayer" to (prefs[KEY_AMBIENT_VIVID_PLAYER] ?: true),
            "dockFoldOnScroll" to (prefs[KEY_DOCK_FOLD_ON_SCROLL] ?: true),
            "lyricsTranslationEnabled" to (prefs[KEY_LYRICS_TRANSLATION_ENABLED] ?: true),
            "lyricsRomanizationEnabled" to (prefs[KEY_LYRICS_ROMANIZATION_ENABLED] ?: false),
            "lyricsBlurUnfocused" to (prefs[KEY_LYRICS_BLUR_UNFOCUSED] ?: true),
            "lyricsCenterAlign" to (prefs[KEY_LYRICS_CENTER_ALIGN] ?: false),
            "lyricsOffsetMs" to (prefs[KEY_LYRICS_OFFSET_MS] ?: 0).coerceIn(-60000, 60000),
            "pauseHistory" to (prefs[KEY_PAUSE_HISTORY] ?: false),
            "moodDialAngle" to (prefs[KEY_MOOD_DIAL_ANGLE] ?: 0f).toDouble(),
            "echoProfile" to (prefs[KEY_ECHO_PROFILE] ?: "Intimate Studio").take(64),
            "echoIntensity" to (prefs[KEY_ECHO_INTENSITY] ?: 0.35f).toDouble()
        )
    }

    suspend fun applySyncedSettingsFromCloud(data: Map<String, Any?>): Boolean {
        val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: return false
        val currentPrefs = context.dataStore.data.first()
        val localUpdatedAt = currentPrefs[KEY_SETTINGS_UPDATED_AT]?.toLongOrNull() ?: 0L
        if (remoteUpdatedAt <= localUpdatedAt) return false

        context.dataStore.edit { prefs ->
            prefs[KEY_SETTINGS_UPDATED_AT] = remoteUpdatedAt.toString()
            (data["libraryLayoutMode"] as? String)?.let { prefs[KEY_LAYOUT_MODE] = it }
            (data["librarySortOrder"] as? String)?.let { prefs[KEY_SORT_ORDER] = it }
            (data["libraryFilterTab"] as? String)?.let { prefs[KEY_FILTER_TAB] = it }
            (data["audioCrossfadeSeconds"] as? Number)?.toInt()?.let { prefs[KEY_CROSSFADE_SECONDS] = it.coerceIn(0, 30) }
            (data["audioGaplessEnabled"] as? Boolean)?.let { prefs[KEY_GAPLESS_ENABLED] = it }
            (data["audioQuality"] as? String)?.let { prefs[KEY_AUDIO_QUALITY] = it }
            (data["cacheLimitMb"] as? Number)?.toInt()?.let { prefs[KEY_CACHE_LIMIT_MB] = it.coerceIn(0, 102400) }
            (data["imageCacheLimitMb"] as? Number)?.toInt()?.let { prefs[KEY_IMAGE_CACHE_LIMIT_MB] = it.coerceIn(0, 10240) }
            (data["soundPreset"] as? String)?.let { prefs[KEY_SOUND_PRESET] = it }
            (data["scrubberStyle"] as? String)?.let { prefs[KEY_SCRUBBER_STYLE] = it }
            (data["glassIntensity"] as? String)?.let { prefs[KEY_GLASS_INTENSITY] = it }
            (data["ambientVividPlayer"] as? Boolean)?.let { prefs[KEY_AMBIENT_VIVID_PLAYER] = it }
            (data["dockFoldOnScroll"] as? Boolean)?.let { prefs[KEY_DOCK_FOLD_ON_SCROLL] = it }
            (data["lyricsTranslationEnabled"] as? Boolean)?.let { prefs[KEY_LYRICS_TRANSLATION_ENABLED] = it }
            (data["lyricsRomanizationEnabled"] as? Boolean)?.let { prefs[KEY_LYRICS_ROMANIZATION_ENABLED] = it }
            (data["lyricsBlurUnfocused"] as? Boolean)?.let { prefs[KEY_LYRICS_BLUR_UNFOCUSED] = it }
            (data["lyricsCenterAlign"] as? Boolean)?.let { prefs[KEY_LYRICS_CENTER_ALIGN] = it }
            (data["lyricsOffsetMs"] as? Number)?.toInt()?.let { prefs[KEY_LYRICS_OFFSET_MS] = it.coerceIn(-60000, 60000) }
            (data["pauseHistory"] as? Boolean)?.let { prefs[KEY_PAUSE_HISTORY] = it }
            (data["moodDialAngle"] as? Number)?.toFloat()?.let { prefs[KEY_MOOD_DIAL_ANGLE] = it }
            (data["echoProfile"] as? String)?.let { prefs[KEY_ECHO_PROFILE] = it }
            (data["echoIntensity"] as? Number)?.toFloat()?.let { prefs[KEY_ECHO_INTENSITY] = it }
        }
        return true
    }

    suspend fun recordSyncPushSuccess(timestampMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_PUSH_MS] = timestampMs.toString()
            prefs.remove(KEY_LAST_SYNC_ERROR)
        }
    }

    suspend fun recordSyncPullSuccess(timestampMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_PULL_MS] = timestampMs.toString()
            prefs.remove(KEY_LAST_SYNC_ERROR)
        }
    }

    suspend fun recordSyncError(message: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_ERROR] = message.take(500)
        }
    }

    suspend fun hasHandledGuestMergeForUid(uid: String): Boolean {
        if (uid.isBlank()) return true
        val raw = context.dataStore.data.first()[KEY_HANDLED_GUEST_UIDS_JSON] ?: return false
        val list = runCatching { authJson.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        return uid in list
    }

    suspend fun markGuestMergeHandledForUid(uid: String) {
        if (uid.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HANDLED_GUEST_UIDS_JSON]
                ?.let { runCatching { authJson.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) }
                .orEmpty()
                .toMutableSet()
            current.add(uid)
            prefs[KEY_HANDLED_GUEST_UIDS_JSON] = authJson.encodeToString(current.toList())
        }
    }

    suspend fun setFirebaseSignedInState(uid: String, email: String, displayName: String, provider: String) {
        val cleanEmail = email.trim()
        val derivedName = displayName.ifBlank { deriveNameFromEmail(cleanEmail) }
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = true
            prefs[KEY_FIREBASE_UID] = uid
            prefs[KEY_AUTH_PROVIDER] = provider
            prefs[KEY_GOOGLE_SIGNED_IN] = uid.isNotBlank()
            prefs[KEY_GOOGLE_EMAIL] = cleanEmail
            prefs[KEY_GOOGLE_DISPLAY_NAME] = derivedName
        }
    }

    suspend fun continueAsGuest() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = true
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
            prefs.remove(KEY_FIREBASE_UID)
        }
    }

    suspend fun resetAuthGateToSignIn() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = false
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
            prefs.remove(KEY_FIREBASE_UID)
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_DISPLAY_NAME)
        }
    }

    suspend fun clearGoogleAccount() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
            prefs.remove(KEY_FIREBASE_UID)
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_DISPLAY_NAME)
        }
    }

    suspend fun resetSyncAndAccountLocalData() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = false
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
            prefs.remove(KEY_FIREBASE_UID)
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_DISPLAY_NAME)
            prefs.remove(KEY_LAST_SYNC_PUSH_MS)
            prefs.remove(KEY_LAST_SYNC_PULL_MS)
            prefs.remove(KEY_LAST_SYNC_ERROR)
            prefs.remove(KEY_SETTINGS_UPDATED_AT)
        }
    }

    private fun deriveNameFromEmail(email: String): String {
        return email.substringBefore("@").replace(".", " ").replace("_", " ")
            .split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { ch -> ch.uppercase() } }
            .ifBlank { "Rezona Listener" }
    }
}
