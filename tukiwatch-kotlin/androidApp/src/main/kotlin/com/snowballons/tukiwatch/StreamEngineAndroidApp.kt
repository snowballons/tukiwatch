package com.snowballons.tukiwatch

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ChaquopyStreamEngine(appContext: Context) : StreamEngine {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext.applicationContext))
        }
    }

    override fun version(): String? = try {
        Python.getInstance().getModule("stream_engine").callAttr("version").toString()
    } catch (_: Exception) {
        null
    }

    override suspend fun warmup(url: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val module = Python.getInstance().getModule("stream_engine")
            if (url != null) module.callAttr("warmup", url) else module.callAttr("warmup")
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun <T : Any> call(attr: String, vararg args: Any): T =
        withContext(Dispatchers.IO) {
            val module = Python.getInstance().getModule("stream_engine")
            module.callAttr(attr, *args).toString() as T
        }

    override suspend fun listStreams(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult = resolveDetails(url, optionsJson, wanted)

    override suspend fun resolveDetails(
        url: String,
        optionsJson: String,
        wanted: String
    ): EngineResult = withContext(Dispatchers.IO) {
        try {
            val t0 = SystemClock.elapsedRealtime()
            val raw = call<String>("resolve_details", url, optionsJson, wanted)
            val parsed = json.decodeFromString(EngineResult.serializer(), raw)
            android.util.Log.d(
                "StreamEngine",
                "resolve url=$url status=${parsed.status} total=${parsed.timings?.totalMs}ms " +
                    "streams=${parsed.timings?.streamsMs}ms reused=${parsed.timings?.sessionReused} " +
                    "bridge=${SystemClock.elapsedRealtime() - t0}ms"
            )
            parsed
        } catch (e: Exception) {
            EngineResult(
                status = "error",
                error = "unexpected",
                message = "${e.javaClass.simpleName}: ${e.message}",
                errorDetails = ErrorDetails(
                    type = "unexpected",
                    error = "Unexpected error: ${e.message}",
                    message = "${e.javaClass.simpleName}: ${e.message}",
                    reason = "Kotlin/Python bridge failure",
                    alternative = null
                )
            )
        }
    }

    override suspend fun checkStatus(url: String, optionsJson: String): StreamStatus =
        withContext(Dispatchers.IO) {
            try {
                val raw = call<String>("check_status", url, optionsJson)
                json.decodeFromString(StreamStatus.serializer(), raw)
            } catch (e: Exception) {
                StreamStatus(
                    url = url,
                    status = "error",
                    error = "${e.javaClass.simpleName}: ${e.message}",
                    errorDetails = ErrorDetails(
                        type = "unexpected",
                        error = "Unexpected error: ${e.message}",
                        message = "${e.javaClass.simpleName}: ${e.message}",
                        reason = "Kotlin/Python bridge failure",
                        alternative = null
                    )
                )
            }
        }
}

fun playStream(player: ExoPlayer, item: StreamItem) {
    val streamUrl = item.url
    requireNotNull(streamUrl) { "StreamItem has no direct URL (kind=${item.kind})" }
    val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(item.headers)
    // If headers contain User-Agent, also set it explicitly for segments.
    item.headers["User-Agent"]?.let { httpFactory.setUserAgent(it) }
    // Route by stream type: HLS playlists need HlsMediaSource, everything
    // else (e.g. TikTok FLV over HTTP) goes through ProgressiveMediaSource,
    // whose extractor sniffs the container (FLV supported, not seekable).
    val isHls = item.kind.contains("HLS", ignoreCase = true) ||
        item.kind.contains("M3U8", ignoreCase = true) ||
        streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
    val source = if (isHls) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
    } else {
        ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))
    }
    player.setMediaSource(source)
    player.prepare()
    player.playWhenReady = true
}