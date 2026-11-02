package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.createFavoritesDriver
import com.snowballons.tukiwatch.liveness.DeviceConnectivity
import com.snowballons.tukiwatch.liveness.LivenessRepository
import com.snowballons.tukiwatch.liveness.LivenessSource
import com.snowballons.tukiwatch.liveness.STATUS_ERROR
import com.snowballons.tukiwatch.liveness.STATUS_OFFLINE
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE
import com.snowballons.tukiwatch.net.NETWORK_ERROR_CLASS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Favorites come from real SQLite; only the network source is faked.
 *
 * A single-threaded dispatcher is used deliberately: `JdbcSqliteDriver` owns one
 * connection, and production serializes ticks anyway, so confining the DB to
 * one thread matches the real concurrency profile instead of racing it.
 *
 * The test coroutines deliberately do NOT run on that dispatcher. `runBlocking`
 * on the same single thread the repository dispatches onto deadlocks as soon as
 * a DB call suspends — the thread is busy running the test body, so the queued
 * `withContext` can never resume.
 */
class LivenessRepositoryTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val dbDispatcher = executor.asCoroutineDispatcher()

    @AfterTest
    fun tearDown() {
        dbDispatcher.close()
        executor.shutdown()
    }

    private class FakeConnectivity : DeviceConnectivity {
        private val state = MutableStateFlow(false)
        override val isOffline: StateFlow<Boolean> = state
        fun set(offline: Boolean) {
            state.value = offline
        }
    }

    private suspend fun repoWith(
        source: LivenessSource,
        connectivity: DeviceConnectivity? = null,
        seed: suspend (FavoritesRepository) -> Unit,
    ): Pair<LivenessRepository, FavoritesRepository> {
        val favorites = FavoritesRepository(createFavoritesDriver(), dbDispatcher)
        seed(favorites)
        return LivenessRepository(
            favorites,
            source,
            nowMs = { 1234L },
            connectivity = connectivity,
            dispatcher = dbDispatcher,
        ) to favorites
    }

    private val online = LivenessSource { url ->
        StreamStatus(
            url = url,
            status = STATUS_ONLINE,
            title = "T",
            author = "A",
            thumbnail = "th",
            platform = "twitch",
        )
    }

    @Test
    fun tickEnrichesFavoritesAndStampsOnce() = runBlocking {
        val (liveness, _) = repoWith(online) { f ->
            f.addFavorite("A", "https://twitch.tv/a")
            f.addFavorite("B", "https://twitch.tv/b")
        }
        liveness.refresh()
        val rows = liveness.streams.value
        assertEquals(2, rows.size)
        assertEquals(listOf("A", "B"), rows.map { it.streamerName })
        // One stamp per tick: Home and Library must agree on row age.
        assertTrue(rows.all { it.fetchedAt == 1234L })
        val a = rows.single { it.streamerName == "A" }
        assertEquals(STATUS_ONLINE, a.status)
        assertEquals("T", a.title)
        assertEquals("twitch", a.platform)
        assertFalse(liveness.loading.value)
        assertFalse(liveness.tickDegraded.value)
    }

    @Test
    fun oneFailingUrlDoesNotAbortTheTick() = runBlocking {
        val source = LivenessSource { url ->
            if (url.endsWith("bad")) throw SocketTimeoutException("radio died")
            StreamStatus(url = url, status = STATUS_OFFLINE)
        }
        val (liveness, _) = repoWith(source) { f ->
            f.addFavorite("good", "https://twitch.tv/good")
            f.addFavorite("bad", "https://twitch.tv/bad")
        }
        liveness.refresh()
        val rows = liveness.streams.value
        assertEquals(2, rows.size, "every favorite must still get a row")
        assertEquals(STATUS_OFFLINE, rows.single { it.streamerName == "good" }.status)
        val bad = rows.single { it.streamerName == "bad" }
        assertEquals(STATUS_ERROR, bad.status)
        assertTrue(bad.error.orEmpty().contains("SocketTimeoutException"), bad.error)
        // An IO failure is a connectivity problem, so the offline banner shows.
        assertTrue(liveness.tickDegraded.value)
    }

    @Test
    fun pluginFailureIsNotReportedAsDegraded() = runBlocking {
        val source = LivenessSource { throw IllegalStateException("No plugin matches this URL") }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://example.com/a") }
        liveness.refresh()
        assertEquals(STATUS_ERROR, liveness.streams.value.single().status)
        assertFalse(liveness.tickDegraded.value, "a missing plugin is not a connectivity problem")
    }

    @Test
    fun pythonClassifiedNetworkFailureMarksDegraded() = runBlocking {
        // The engine returns transport failures as a normal result; only the
        // typed error_class distinguishes them from a plugin verdict.
        val source = LivenessSource { url ->
            StreamStatus(
                url = url,
                status = STATUS_ERROR,
                error = "plugin_error",
                errorDetails = ErrorDetails(
                    type = "plugin_error",
                    message = "Failed to resolve 'example.invalid'",
                    errorClass = NETWORK_ERROR_CLASS,
                ),
            )
        }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://twitch.tv/a") }
        liveness.refresh()
        assertEquals(STATUS_ERROR, liveness.streams.value.single().status)
        assertTrue(liveness.tickDegraded.value)
    }

    @Test
    fun statusShapedPluginFailureIsNotDegraded() = runBlocking {
        val source = LivenessSource { url ->
            StreamStatus(
                url = url,
                status = STATUS_ERROR,
                error = "plugin_error",
                errorDetails = ErrorDetails(type = "plugin_error", message = "extraction failed"),
            )
        }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://example.com/a") }
        liveness.refresh()
        assertEquals(STATUS_ERROR, liveness.streams.value.single().status)
        assertFalse(liveness.tickDegraded.value)
    }

    @Test
    fun blankStatusFallsBackToError() = runBlocking {
        // Python always fills `status`; a bridge returning "{}" must not yield a
        // row whose status the UI cannot branch on.
        val source = LivenessSource { url -> StreamStatus(url = url) }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://twitch.tv/a") }
        liveness.refresh()
        assertEquals(STATUS_ERROR, liveness.streams.value.single().status)
    }

    @Test
    fun offlineShortCircuitKeepsLastKnownRows() = runBlocking {
        val connectivity = FakeConnectivity()
        var generation = 0
        val source = LivenessSource { url ->
            StreamStatus(url = url, status = STATUS_ONLINE, title = "gen$generation")
        }
        val (liveness, _) = repoWith(source, connectivity) {
            it.addFavorite("A", "https://twitch.tv/a")
        }

        liveness.refresh()
        assertEquals("gen0", liveness.streams.value.single().title)

        generation = 1
        connectivity.set(true)
        liveness.refresh()
        assertEquals("gen0", liveness.streams.value.single().title, "stale rows beat no rows")
        assertTrue(liveness.tickDegraded.value)
    }

    @Test
    fun emptyFavoritesClearsStreams() = runBlocking {
        val (liveness, _) = repoWith(online) { }
        liveness.refresh()
        assertTrue(liveness.streams.value.isEmpty())
        assertFalse(liveness.loading.value)
        assertFalse(liveness.tickDegraded.value)
    }

    @Test
    fun removedFavoriteDropsOnNextTick() = runBlocking {
        val (liveness, favorites) = repoWith(online) { f ->
            f.addFavorite("A", "https://twitch.tv/a")
            f.addFavorite("B", "https://twitch.tv/b")
        }
        liveness.refresh()
        assertEquals(2, liveness.streams.value.size)
        val id = favorites.getFavorites().single { it.streamerName == "B" }.id
        favorites.removeFavorite(id)
        liveness.refresh()
        assertEquals(listOf("A"), liveness.streams.value.map { it.streamerName })
    }

    @Test
    fun refreshIsDroppedWhileOneIsInFlight() = runBlocking {
        // A tick can outrun the 5-min interval on a large list; an overlapping
        // call must be dropped rather than queued into N Streamlink resolves.
        // Deterministic: the first source call suspends until the second refresh
        // has been attempted, proving the in-flight lock was held. Suspending
        // (CompletableDeferred) rather than blocking keeps the DB dispatcher
        // free to finish the first tick.
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val source = LivenessSource { url ->
            if (calls++ == 0) {
                entered.complete(Unit)
                release.await()
            }
            StreamStatus(url = url, status = STATUS_ONLINE)
        }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://twitch.tv/a") }

        val first = launch { liveness.refresh() }
        withTimeout(5_000) { entered.await() }
        liveness.refresh()
        release.complete(Unit)
        first.join()
        assertEquals(1, calls, "second refresh must be dropped while one is in flight")
    }
}
