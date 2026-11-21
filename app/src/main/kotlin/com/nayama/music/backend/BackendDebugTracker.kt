package com.nayama.music.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StreamDebugEntry(
    val timestamp: String,
    val trackTitle: String,
    val artist: String,
    val selectedProvider: String,
    val bitrateKbps: Int?,
    val streamFormat: String?,
    val fallbackReason: String?,
    val resolutionDurationMs: Long,
    val isSuccess: Boolean
)

data class EndpointDebugStatus(
    val endpointName: String,
    val isReal: Boolean = true,
    val statusBadge: String = "REAL · ACTIVE",
    val lastDetail: String = "Ready",
    val lastLatencyMs: Long = 0L,
    val isHealthy: Boolean = true
)

data class SearchTelemetry(
    val sectionCounts: Map<String, Int> = emptyMap(),
    val rawRequestParams: String = "None yet"
)

object BackendDebugTracker {

    private val _logs = MutableStateFlow<List<StreamDebugEntry>>(emptyList())
    val logs: StateFlow<List<StreamDebugEntry>> = _logs.asStateFlow()

    private val _searchTelemetry = MutableStateFlow(SearchTelemetry())
    val searchTelemetry: StateFlow<SearchTelemetry> = _searchTelemetry.asStateFlow()

    private val _endpoints = MutableStateFlow<Map<String, EndpointDebugStatus>>(
        mapOf(
            "innertube_search" to EndpointDebugStatus("InnerTube Search", true, "REAL", "WEB_REMIX · Catalog + Filters"),
            "innertube_suggestions" to EndpointDebugStatus("InnerTube Suggestions", true, "REAL", "Autocomplete API"),
            "innertube_home" to EndpointDebugStatus("InnerTube Home Browse", true, "REAL", "FEmusic_home shelves"),
            "innertube_next" to EndpointDebugStatus("InnerTube Radio Queue", true, "REAL", "RDAMVM Autoplay Mix"),
            "newpipe_stream" to EndpointDebugStatus("NewPipe Stream Engine", true, "REAL", "Opus 160kbps / AAC"),
            "lrclib_lyrics" to EndpointDebugStatus("LRCLIB Synced Lyrics", true, "REAL", "Timestamped LRC + YTM Fallback")
        )
    )
    val endpoints: StateFlow<Map<String, EndpointDebugStatus>> = _endpoints.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun recordSearchTelemetry(sectionCounts: Map<String, Int>, rawParams: String) {
        _searchTelemetry.value = SearchTelemetry(
            sectionCounts = sectionCounts,
            rawRequestParams = rawParams
        )
    }

    fun updateEndpointStatus(
        key: String,
        endpointName: String,
        detail: String,
        latencyMs: Long,
        isHealthy: Boolean = true
    ) {
        val current = _endpoints.value.toMutableMap()
        current[key] = EndpointDebugStatus(
            endpointName = endpointName,
            isReal = !BackendConfig.ENABLE_MOCK_FALLBACK,
            statusBadge = if (BackendConfig.ENABLE_MOCK_FALLBACK) "MOCK" else "REAL",
            lastDetail = detail,
            lastLatencyMs = latencyMs,
            isHealthy = isHealthy
        )
        _endpoints.value = current
    }

    fun recordResolution(
        trackTitle: String,
        artist: String,
        provider: String,
        bitrateKbps: Int?,
        format: String?,
        fallbackReason: String?,
        durationMs: Long,
        isSuccess: Boolean
    ) {
        val entry = StreamDebugEntry(
            timestamp = dateFormat.format(Date()),
            trackTitle = trackTitle,
            artist = artist,
            selectedProvider = provider,
            bitrateKbps = bitrateKbps,
            streamFormat = format,
            fallbackReason = fallbackReason,
            resolutionDurationMs = durationMs,
            isSuccess = isSuccess
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 50) current.removeAt(current.size - 1)
        _logs.value = current

        updateEndpointStatus(
            key = "newpipe_stream",
            endpointName = "NewPipe Stream Engine",
            detail = if (isSuccess) "${format ?: "Opus"} · ${bitrateKbps ?: 160}kbps (${durationMs}ms)" else "Resolution failed",
            latencyMs = durationMs,
            isHealthy = isSuccess
        )
    }
}
