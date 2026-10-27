package com.snowballons.tukiwatch.liveness

import com.snowballons.tukiwatch.ErrorDetails
import com.snowballons.tukiwatch.StreamEngine
import com.snowballons.tukiwatch.StreamStatus
import com.snowballons.tukiwatch.model.Favorite
import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.net.NETWORK_ERROR_CLASS
import com.snowballons.tukiwatch.net.isNetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** StreamContext.tsx AUTO_REFRESH_INTERVAL = 300_000. */
const val AUTO_REFRESH_INTERVAL_MS = 300_000L

const val STATUS_ONLINE = "online"
const val STATUS_OFFLINE = "offline"
const val STATUS_ERROR = "error"

/**
 * A favorite enriched with its latest liveness result — the shape
 * StreamContext.tsx built by joining `statusResults` onto `favorites[index]`.
 */
data class LiveStream(
    val id: Long,
    val streamerName: String,
    val url: String,
    val status: String,
    val title: String = "",
    val author: String = "",
    val thumbnail: String = "",
    val category: String? = null,
    val streamId: String? = null,
    val platform: String? = null,
    val viewerCount: Int? = null,
    val error: String? = null,
    val errorDetails: ErrorDetails? = null,
    /** Single stamp per tick, so Home/Library age every row identically. */
    val fetchedAt: Long = 0L,
)

/** Where liveness comes from. Keeps a secretless Twitch source pluggable later. */
fun interface LivenessSource {
    suspend fun status(url: String): StreamStatus
}

/**
 * Phase 2 source: Streamlink `check_status` for every favorite.
 * With browse-discovery dropped this is the only liveness path, until a
 * secretless Twitch source returns.
 */
class StreamlinkLivenessSource(private val engine: StreamEngine) : LivenessSource {
    override suspend fun status(url: String): StreamStatus = engine.checkStatus(url)
}

/** OS-level reachability, the NetInfo replacement. */
interface DeviceConnectivity {
    val isOffline: StateFlow<Boolean>
}

/**
 * Liveness redesign (plan §3): the server did O(N) Streamlink resolves per tick
 * against a shared cache; on a phone each one is radio + battery. Checks run
 * sequentially — the serialization IS the stagger, so no artificial gap is added
 * on top of a 1–5s check — and only while foregrounded.
 *
 * `bypass_cache` from StreamContext is gone with the server cache; there is no
 * shared TTL to bypass on-device.
 */
class LivenessRepository(
    private val favorites: FavoritesRepository,
    private val source: LivenessSource,
    private val nowMs: () -> Long,
    private val connectivity: DeviceConnectivity? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _streams = MutableStateFlow<List<LiveStream>>(emptyList())
    val streams: StateFlow<List<LiveStream>> = _streams.asStateFlow()

    /** Starts true, as StreamContext's `useState(true)` does. */
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Replaces `isBackendReachable`, which died with the /health endpoint. */
    private val _tickDegraded = MutableStateFlow(false)
    val tickDegraded: StateFlow<Boolean> = _tickDegraded.asStateFlow()

    private val inFlight = Mutex()

    /**
     * One tick. A tick can outrun the 5-min interval on a large list, so an
     * overlapping call is dropped rather than queued.
     */
    suspend fun refresh() {
        if (!inFlight.tryLock()) return
        try {
            _loading.value = true
            withContext(dispatcher) { tick() }
        } finally {
            _loading.value = false
            inFlight.unlock()
        }
    }

    private suspend fun tick() {
        val favs = try {
            favorites.getFavorites()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            // Leave last-known rows on screen, as StreamContext's catch did.
            _tickDegraded.value = true
            return
        }

        if (connectivity?.isOffline?.value == true) {
            _tickDegraded.value = true
            return
        }
        if (favs.isEmpty()) {
            _streams.value = emptyList()
            _tickDegraded.value = false
            return
        }

        val stamp = nowMs()
        val rows = ArrayList<LiveStream>(favs.size)
        var sawNetworkFailure = false
        for (fav in favs) {
            val status = try {
                source.status(fav.originalUrl)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // One dead URL must not abort the other N-1: unlike the server's
                // single batch POST, failures here are per-row.
                if (isNetworkError(t)) sawNetworkFailure = true
                StreamStatus(
                    url = fav.originalUrl,
                    status = STATUS_ERROR,
                    error = "${t::class.simpleName}: ${t.message}",
                )
            }

            // The engine reports transport failures as a normal result
            // (`status=error`, `type=plugin_error`) with an `error_class` flag,
            // so the throw-only check above cannot see them.
            if (status.errorDetails?.errorClass == NETWORK_ERROR_CLASS) {
                sawNetworkFailure = true
            }
            rows += enrich(fav, status, stamp)
        }
        _streams.value = rows
        _tickDegraded.value = sawNetworkFailure
    }

    private fun enrich(fav: Favorite, status: StreamStatus, stamp: Long) = LiveStream(
        id = fav.id,
        streamerName = fav.streamerName,
        url = fav.originalUrl,
        status = status.status.ifEmpty { STATUS_ERROR },
        title = status.title.orEmpty(),
        author = status.author.orEmpty(),
        thumbnail = status.thumbnail.orEmpty(),
        category = status.category,
        streamId = status.streamId,
        platform = status.platform,
        viewerCount = status.viewerCount,
        error = status.error,
        errorDetails = status.errorDetails,
        fetchedAt = stamp,
    )
}

/**
 * The 5-min cadence. Owned by the caller's lifecycle so it stops with the
 * foreground, per §3 ("only while app is foregrounded").
 */
suspend fun LivenessRepository.autoRefreshLoop(intervalMs: Long = AUTO_REFRESH_INTERVAL_MS) {
    while (true) {
        refresh()
        delay(intervalMs)
    }
}
