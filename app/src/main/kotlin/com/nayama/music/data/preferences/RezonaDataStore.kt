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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

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
        val KEY_AMBIENT_VIVID_PLAYER = booleanPreferencesKey("ui_ambient_vivid_player")
        val KEY_DOCK_FOLD_ON_SCROLL = booleanPreferencesKey("ui_dock_fold_on_scroll")

        // Lyrics & Translation Settings
        val KEY_LYRICS_TRANSLATION_ENABLED = booleanPreferencesKey("lyrics_translation_enabled")
        val KEY_LYRICS_ROMANIZATION_ENABLED = booleanPreferencesKey("lyrics_romanization_enabled")
        val KEY_LYRICS_BLUR_UNFOCUSED = booleanPreferencesKey("lyrics_blur_unfocused")
        val KEY_LYRICS_CENTER_ALIGN = booleanPreferencesKey("lyrics_center_align")
        val KEY_LYRICS_OFFSET_MS = intPreferencesKey("lyrics_timing_offset_ms")

        // Privacy & History
        val KEY_PAUSE_HISTORY = booleanPreferencesKey("privacy_pause_history")

        // Mood & Echo (Legacy Sound Lab)
        val KEY_MOOD_DIAL_ANGLE = floatPreferencesKey("mood_dial_angle")
        val KEY_ECHO_PROFILE = stringPreferencesKey("echo_mode_profile")
        val KEY_ECHO_INTENSITY = floatPreferencesKey("echo_mode_intensity")

        // One-time v2 seed clutter cleanup
        val KEY_CLEANED_DEFAULT_SEEDS_V2 = booleanPreferencesKey("db_cleaned_default_seeds_v2")

        // Auth & Account Identity (On-device Google picker + Email Sign-Up / Sign-In)
        val KEY_AUTH_GATE_SEEN = booleanPreferencesKey("auth_gate_seen")
        val KEY_AUTH_PROVIDER = stringPreferencesKey("auth_provider")
        val KEY_GOOGLE_SIGNED_IN = booleanPreferencesKey("auth_google_signed_in")
        val KEY_GOOGLE_EMAIL = stringPreferencesKey("auth_google_email")
        val KEY_GOOGLE_DISPLAY_NAME = stringPreferencesKey("auth_google_display_name")
        val KEY_REGISTERED_ACCOUNTS_JSON = stringPreferencesKey("auth_registered_accounts_json")
    }

    @Serializable
    data class RegisteredLocalAccount(
        val email: String,
        val displayName: String,
        val passwordHash: String
    )

    sealed class AccountAuthResult {
        data class Success(val email: String, val displayName: String) : AccountAuthResult()
        data class Error(val message: String) : AccountAuthResult()
    }

    data class GoogleAccountState(
        val isSignedIn: Boolean = false,
        val email: String = "",
        val displayName: String = "",
        val provider: String = "guest"
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
        val email = prefs[KEY_GOOGLE_EMAIL].orEmpty()
        val name = prefs[KEY_GOOGLE_DISPLAY_NAME].orEmpty()
        val provider = prefs[KEY_AUTH_PROVIDER] ?: if (signedIn && email.isNotBlank()) "google" else "guest"
        GoogleAccountState(
            isSignedIn = signedIn && email.isNotBlank(),
            email = email,
            displayName = name,
            provider = provider
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
        prefs[KEY_GLASS_INTENSITY] ?: "Low"
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

    suspend fun setLayoutMode(mode: LibraryLayoutMode) {
        context.dataStore.edit { prefs -> prefs[KEY_LAYOUT_MODE] = mode.name }
    }

    suspend fun setSortOrder(order: LibrarySortOrder) {
        context.dataStore.edit { prefs -> prefs[KEY_SORT_ORDER] = order.name }
    }

    suspend fun setFilterTab(tab: String) {
        context.dataStore.edit { prefs -> prefs[KEY_FILTER_TAB] = tab }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CROSSFADE_SECONDS] = seconds }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_GAPLESS_ENABLED] = enabled }
    }

    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { prefs -> prefs[KEY_AUDIO_QUALITY] = quality }
    }

    suspend fun setCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CACHE_LIMIT_MB] = limitMb }
    }

    suspend fun setImageCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_IMAGE_CACHE_LIMIT_MB] = limitMb }
    }

    suspend fun setSoundPreset(preset: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SOUND_PRESET] = preset }
    }

    suspend fun setScrubberStyle(style: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SCRUBBER_STYLE] = style }
    }

    suspend fun setGlassIntensity(intensity: String) {
        context.dataStore.edit { prefs -> prefs[KEY_GLASS_INTENSITY] = intensity }
    }

    suspend fun setAmbientVividPlayer(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AMBIENT_VIVID_PLAYER] = enabled }
    }

    suspend fun setDockFoldOnScroll(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DOCK_FOLD_ON_SCROLL] = enabled }
    }

    suspend fun setLyricsTranslationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRICS_TRANSLATION_ENABLED] = enabled }
    }

    suspend fun setLyricsRomanizationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRICS_ROMANIZATION_ENABLED] = enabled }
    }

    suspend fun setLyricsBlurUnfocused(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRICS_BLUR_UNFOCUSED] = enabled }
    }

    suspend fun setLyricsCenterAlign(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRICS_CENTER_ALIGN] = enabled }
    }

    suspend fun setLyricsOffsetMs(offsetMs: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRICS_OFFSET_MS] = offsetMs }
    }

    suspend fun setPauseHistory(paused: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_PAUSE_HISTORY] = paused }
    }

    suspend fun setMoodDialAngle(angle: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_MOOD_DIAL_ANGLE] = angle }
    }

    suspend fun setEchoProfile(profile: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ECHO_PROFILE] = profile }
    }

    suspend fun setEchoIntensity(intensity: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_ECHO_INTENSITY] = intensity }
    }

    private fun hashPassword(email: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${email.lowercase().trim()}:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decodeAccounts(raw: String?): List<RegisteredLocalAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { authJson.decodeFromString<List<RegisteredLocalAccount>>(raw) }
            .getOrDefault(emptyList())
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String): AccountAuthResult {
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim().ifBlank { deriveNameFromEmail(cleanEmail) }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return AccountAuthResult.Error("Enter a valid email address.")
        }
        if (password.length < 6) {
            return AccountAuthResult.Error("Password must be at least 6 characters.")
        }
        val currentPrefs = context.dataStore.data.first()
        val accounts = decodeAccounts(currentPrefs[KEY_REGISTERED_ACCOUNTS_JSON]).toMutableList()
        if (accounts.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            return AccountAuthResult.Error("An account with this email already exists. Please sign in.")
        }
        val hash = hashPassword(cleanEmail, password)
        accounts.add(RegisteredLocalAccount(email = cleanEmail, displayName = cleanName, passwordHash = hash))
        context.dataStore.edit { prefs ->
            prefs[KEY_REGISTERED_ACCOUNTS_JSON] = authJson.encodeToString(accounts)
            prefs[KEY_AUTH_GATE_SEEN] = true
            prefs[KEY_AUTH_PROVIDER] = "email"
            prefs[KEY_GOOGLE_SIGNED_IN] = true
            prefs[KEY_GOOGLE_EMAIL] = cleanEmail
            prefs[KEY_GOOGLE_DISPLAY_NAME] = cleanName
        }
        return AccountAuthResult.Success(cleanEmail, cleanName)
    }

    suspend fun signInWithEmail(email: String, password: String): AccountAuthResult {
        val cleanEmail = email.trim().lowercase()
        if (!cleanEmail.contains("@") || password.isBlank()) {
            return AccountAuthResult.Error("Enter both your email and password.")
        }
        val currentPrefs = context.dataStore.data.first()
        val accounts = decodeAccounts(currentPrefs[KEY_REGISTERED_ACCOUNTS_JSON])
        val match = accounts.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }
            ?: return AccountAuthResult.Error("No local account found for $cleanEmail. Please sign up first.")
        val hash = hashPassword(cleanEmail, password)
        if (match.passwordHash != hash) {
            return AccountAuthResult.Error("Incorrect password.")
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = true
            prefs[KEY_AUTH_PROVIDER] = "email"
            prefs[KEY_GOOGLE_SIGNED_IN] = true
            prefs[KEY_GOOGLE_EMAIL] = match.email
            prefs[KEY_GOOGLE_DISPLAY_NAME] = match.displayName
        }
        return AccountAuthResult.Success(match.email, match.displayName)
    }

    suspend fun setGoogleAccount(email: String, displayName: String) {
        val cleanEmail = email.trim()
        val derivedName = displayName.ifBlank { deriveNameFromEmail(cleanEmail) }
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = true
            prefs[KEY_AUTH_PROVIDER] = "google"
            prefs[KEY_GOOGLE_SIGNED_IN] = true
            prefs[KEY_GOOGLE_EMAIL] = cleanEmail
            prefs[KEY_GOOGLE_DISPLAY_NAME] = derivedName
        }
    }

    suspend fun continueAsGuest() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = true
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
        }
    }

    suspend fun resetAuthGateToSignIn() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_GATE_SEEN] = false
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_DISPLAY_NAME)
        }
    }

    suspend fun clearGoogleAccount() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_GOOGLE_SIGNED_IN] = false
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs.remove(KEY_GOOGLE_DISPLAY_NAME)
        }
    }

    private fun deriveNameFromEmail(email: String): String {
        return email.substringBefore("@").replace(".", " ").replace("_", " ")
            .split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { ch -> ch.uppercase() } }
            .ifBlank { "Rezona Listener" }
    }
}
