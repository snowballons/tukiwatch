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

@Serializable
data class EngineResult(
    val error: String? = null,
    val message: String? = null,
    val streams: List<StreamItem> = emptyList(),
    val wanted: String? = null,
    @SerialName("quality_index") val qualityIndex: List<String>? = null,
    @SerialName("_timings") val timings: EngineTimings? = null
) {
    val directPlayable: List<StreamItem>
        get() = streams.filter { it.playableDirect && it.url != null }
}

interface StreamEngine {
    suspend fun listStreams(
        url: String,
        optionsJson: String = "{}",
        wanted: String = "all"
    ): EngineResult

    /** Pre-build session off the critical path. Returns true when ready. */
    suspend fun warmup(url: String? = null): Boolean = true

    fun version(): String?
}

expect fun createStreamEngine(): StreamEngine
