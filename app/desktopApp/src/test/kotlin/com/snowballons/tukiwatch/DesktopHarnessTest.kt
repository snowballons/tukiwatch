package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.createFavoritesDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopHarnessTest {
    @Test
    fun resolvePublishesMetadataAndQualities() = runBlocking {
        val (harness, engine) = newHarness()
        try {
            harness.url = "https://www.twitch.tv/fixture"
            val result = harness.resolveOnce()

            assertNotNull(result)
            assertEquals("online", result.status)
            assertEquals("Fixture stream", harness.result?.title)
            assertEquals("Fixture author", harness.result?.author)
            assertEquals(2, result.directPlayable.size)
            assertEquals("best", harness.selectedQuality)
            assertFalse(harness.resolving)
            assertNotNull(harness.elapsedMs)
            assertTrue(harness.message.orEmpty().contains("2 directly playable"))
            assertEquals("https://www.twitch.tv/fixture", engine.lastUrl)
        } finally {
            harness.close()
        }
    }

    @Test
    fun resolveSurfacesStructuredErrorDetails() = runBlocking {
        val engine = FakeEngine(
            result = EngineResult(
                status = "error",
                error = "plugin_error",
                errorDetails = ErrorDetails(
                    type = "plugin_error",
                    error = "Failed to extract stream",
                    reason = "Plugin reported an error during extraction",
                    errorClass = "network",
                ),
            )
        )
        val (harness, _) = newHarness(engine)
        try {
            harness.url = "https://example.invalid/live"
            harness.resolveOnce()

            assertEquals("Failed to extract stream", harness.message)
            assertEquals("network", harness.result?.errorDetails?.errorClass)
            assertNull(harness.selectedQuality)
        } finally {
            harness.close()
        }
    }

    @Test
    fun toggleAddsThenRemovesUsingTheResolvedUrl() = runBlocking {
        val favorites = FavoritesRepository(createFavoritesDriver(), Dispatchers.IO)
        val (harness, _) = newHarness(favorites = favorites)
        try {
            harness.url = "https://www.twitch.tv/fixture"
            harness.resolveOnce()
            assertFalse(harness.isFavorite!!)

            harness.toggleFavoriteOnce()
            assertTrue(harness.isFavorite!!)
            val saved = favorites.getFavorites().single()
            assertEquals("Fixture author", saved.streamerName)
            assertEquals("https://www.twitch.tv/fixture", saved.originalUrl)

            harness.toggleFavoriteOnce()
            assertFalse(harness.isFavorite!!)
            assertTrue(favorites.getFavorites().isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun favoriteBeforeResolveUsesTheFieldUrlAndDerivesAName() = runBlocking {
        val favorites = FavoritesRepository(createFavoritesDriver(), Dispatchers.IO)
        val (harness, _) = newHarness(favorites = favorites)
        try {
            harness.onUrlChanged("https://www.twitch.tv/some_channel")
            harness.toggleFavoriteOnce()

            assertTrue(harness.isFavorite!!)
            assertEquals(
                "some_channel",
                favorites.getFavorites().single().streamerName,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun initialUrlMembershipIsLoadedForTheButtonLabel() = runBlocking {
        val favorites = FavoritesRepository(createFavoritesDriver(), Dispatchers.IO)
        favorites.addFavorite("LofiGirl", "https://www.twitch.tv/lofigirl")
        val (harness, _) = newHarness(favorites = favorites)
        try {
            withTimeout(5_000) {
                while (harness.isFavorite == null) delay(10)
            }
            assertTrue(harness.isFavorite!!)
        } finally {
            harness.close()
        }
    }

    @Test
    fun changingTheUrlDropsStaleMembership() = runBlocking {
        val (harness, _) = newHarness()
        try {
            harness.url = "https://www.twitch.tv/fixture"
            harness.resolveOnce()
            harness.toggleFavoriteOnce()
            assertTrue(harness.isFavorite!!)

            harness.onUrlChanged("https://www.twitch.tv/another")
            assertNull(harness.isFavorite)
        } finally {
            harness.close()
        }
    }
}
