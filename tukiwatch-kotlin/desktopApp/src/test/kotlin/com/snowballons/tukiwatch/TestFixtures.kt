package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.createFavoritesDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Test engine for both the harness unit tests and the Compose UI tests: no
 * Streamlink process, no network, deterministic durations.
 */
internal class FakeEngine(
    var result: EngineResult = onlineResult(),
    var status: StreamStatus = StreamStatus(
        status = "online",
        title = "Fixture stream",
        author = "Fixture author",
        platform = "twitch",
    ),
) : StreamEngine {
    var resolveCalls = 0
    var statusCalls = 0
    var lastUrl: String? = null

    override suspend fun listStreams(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult = resolveDetails(url, optionsJson, wanted)

    override suspend fun resolveDetails(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult {
        resolveCalls++
        lastUrl = url
        return result
    }

    override suspend fun checkStatus(url: String, optionsJson: String): StreamStatus {
        statusCalls++
        return status
    }

    override fun version(): String = "fake-streamlink"

    companion object {
        fun onlineResult() = EngineResult(
            status = "online",
            title = "Fixture stream",
            author = "Fixture author",
            platform = "twitch",
            category = "Software and Game Development",
            viewerCount = 42,
            streams = listOf(
                StreamItem(
                    quality = "best",
                    kind = "hls",
                    playableDirect = true,
                    url = "https://example.com/best.m3u8",
                ),
                StreamItem(
                    quality = "audio_only",
                    kind = "hls",
                    playableDirect = true,
                    url = "https://example.com/audio.m3u8",
                ),
            ),
        )
    }
}

internal fun newHarness(
    engine: FakeEngine = FakeEngine(),
    favorites: FavoritesRepository = FavoritesRepository(
        createFavoritesDriver(),
        Dispatchers.IO,
    ),
): Pair<DesktopHarness, FakeEngine> {
    val harness = DesktopHarness(
        engine = engine,
        favorites = favorites,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
    return harness to engine
}
