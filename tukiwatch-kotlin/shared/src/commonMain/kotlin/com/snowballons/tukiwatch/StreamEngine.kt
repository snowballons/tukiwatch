package com.snowballons.tukiwatch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreamItem(
    val quality: String,
    val alias: Boolean = false,
    val kind: String = "",
    @SerialName("playable_direct") val playableDirect: Boolean = false,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class EngineTimings(
    @SerialName("session_ms") val sessionMs: Double = 0.0,
    @SerialName("streams_ms") val streamsMs: Double = 0.0,
    @SerialName("build_ms") val buildMs: Double = 0.0,
    @SerialName("total_ms") val totalMs: Double = 0.0,
    @SerialName("session_reused") val sessionReused: Boolean = false,
    val count: Int = 0
)

/** Client-visible error contract, matching the Expo app's 422 `error_details` shape. */
@Serializable
data class ErrorDetails(
    val type: String? = null,           // no_plugin | browser_required | plugin_error | unexpected
    val error: String? = null,
    val message: String? = null,
    val reason: String? = null,
    val alternative: String? = null,
    /**
     * `network` when the failure is transport-level (DNS, timeout, refused
     * connection) rather than a plugin verdict. Drives the offline banner;
     * `type` deliberately stays `plugin_error` so the client taxonomy is stable.
     */
    @SerialName("error_class") val errorClass: String? = null
)

/** Full stream resolution with metadata, mirroring `StreamResolution` from tukiwatch-core. */
@Serializable
data class EngineResult(
    val status: String? = null,         // online | offline | error
    val error: String? = null,
    val message: String? = null,
    val streams: List<StreamItem> = emptyList(),
    val wanted: String? = null,
    @SerialName("quality_index") val qualityIndex: List<String>? = null,
    @SerialName("_timings") val timings: EngineTimings? = null,
    @SerialName("original_url") val originalUrl: String? = null,
    val platform: String? = null,
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val category: String? = null,
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("viewer_count") val viewerCount: Int? = null,
    @SerialName("stream_types") val streamTypes: List<String>? = null,
    @SerialName("best_quality") val bestQuality: String? = null,
    @SerialName("error_details") val errorDetails: ErrorDetails? = null
) {
    val directPlayable: List<StreamItem>
        get() = streams.filter { it.playableDirect && it.url != null }
}

/** Cheap liveness check, mirroring `StreamStatus` from tukiwatch-core. */
@Serializable
data class StreamStatus(
    val url: String = "",
    val status: String = "",            // online | offline | error
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val category: String? = null,
    @SerialName("stream_id") val streamId: String? = null,
    val platform: String? = null,
    @SerialName("viewer_count") val viewerCount: Int? = null,
    val error: String? = null,
    @SerialName("error_details") val errorDetails: ErrorDetails? = null
)

interface StreamEngine {
    suspend fun listStreams(
        url: String,
        optionsJson: String = "{}",
        wanted: String = "all"
    ): EngineResult

    /** Full resolution with metadata (Phase 1). */
    suspend fun resolveDetails(
        url: String,
        optionsJson: String = "{}",
        wanted: String = "all"
    ): EngineResult

    /** Cheap liveness check for non-Twitch staggered status (Phase 1). */
    suspend fun checkStatus(url: String, optionsJson: String = "{}"): StreamStatus

    /** Pre-build session off the critical path. Returns true when ready. */
    suspend fun warmup(url: String? = null): Boolean = true

    fun version(): String?
}

expect fun createStreamEngine(): StreamEngine