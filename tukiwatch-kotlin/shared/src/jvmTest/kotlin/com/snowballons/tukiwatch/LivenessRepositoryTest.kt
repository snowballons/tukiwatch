package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.createFavoritesDriver
import com.snowballons.tukiwatch.liveness.DeviceConnectivity
import com.snowballons.tukiwatch.liveness.LivenessRepository
import com.snowballons.tukiwatch.liveness.LivenessSource
import com.snowballons.tukiwatch.liveness.STATUS_ERROR
import com.snowballons.tukiwatch.liveness.STATUS_OFFLINE
import com.snowballons.tukiwatch.liveness.STATUS_ONLINE
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
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
 * connection, and production serializes ticks anyway, so confining the work to
 * one thread matches the real concurrency profile instead of racing it.
 */
class LivenessRepositoryTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val testDispatcher = executor.asCoroutineDispatcher()

    @AfterTest
    fun tearDown() {
        testDispatcher.close()
        executor.shutdown()
    }

    private class FakeConnectivity : DeviceConnectivity {
        private val state = MutableStateFlow(false)
        override val isOffline: StateFlow<Boolean> = state
        fun set(offline: Boolean) {
            state.value = offline
        }
    }

    private fun repoWith(
        source: LivenessSource,
        connectivity: DeviceConnectivity? = null,
        seed: suspend (FavoritesRepository) -> Unit,
    ): Pair<LivenessRepository, FavoritesRepository> {
        val favorites = FavoritesRepository(createFavoritesDriver(), testDispatcher)
        runBlocking(testDispatcher) { seed(favorites) }
        return LivenessRepository(
            favorites,
            source,
            nowMs = { 1234L },
            connectivity = connectivity,
            dispatcher = testDispatcher,
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
    fun tickEnrichesFavoritesAndStampsOnce() = runBlocking(testDispatcher) {
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
    fun oneFailingUrlDoesNotAbortTheTick() = runBlocking(testDispatcher) {
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
    fun pluginFailureIsNotReportedAsDegraded() = runBlocking(testDispatcher) {
        val source = LivenessSource { throw IllegalStateException("No plugin matches this URL") }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://example.com/a") }
        liveness.refresh()
        assertEquals(STATUS_ERROR, liveness.streams.value.single().status)
        assertFalse(liveness.tickDegraded.value, "a missing plugin is not a connectivity problem")
    }

    @Test
    fun blankStatusFallsBackToError() = runBlocking(testDispatcher) {
        // Python always fills `status`; a bridge returning "{}" must not yield a
        // row whose status the UI cannot branch on.
        val source = LivenessSource { url -> StreamStatus(url = url) }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://twitch.tv/a") }
        liveness.refresh()
        assertEquals(STATUS_ERROR, liveness.streams.value.single().status)
    }

    @Test
    fun offlineShortCircuitKeepsLastKnownRows() = runBlocking(testDispatcher) {
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
    fun emptyFavoritesClearsStreams() = runBlocking(testDispatcher) {
        val (liveness, _) = repoWith(online) { }
        liveness.refresh()
        assertTrue(liveness.streams.value.isEmpty())
        assertFalse(liveness.loading.value)
        assertFalse(liveness.tickDegraded.value)
    }

    @Test
    fun removedFavoriteDropsOnNextTick() = runBlocking(testDispatcher) {
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
    fun refreshIsDroppedWhileOneIsInFlight() = runBlocking(testDispatcher) {
        // A tick can outrun the 5-min interval on a large list; an overlapping
        // call must be dropped rather than queued into N Streamlink resolves.
        // Deterministic: the first source call blocks until the second refresh
        // has been attempted, proving the in-flight lock was held.
        var calls = 0
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val source = LivenessSource { url ->
            if (calls++ == 0) {
                entered.countDown()
                release.await()
            }
            StreamStatus(url = url, status = STATUS_ONLINE)
        }
        val (liveness, _) = repoWith(source) { it.addFavorite("A", "https://twitch.tv/a") }

        val first = launch { liveness.refresh() }
        withTimeout(5_000) { entered.await() }
        liveness.refresh()
        release.countDown()
        first.join()
        assertEquals(1, calls, "second refresh must be dropped while one is in flight")
    }
}
