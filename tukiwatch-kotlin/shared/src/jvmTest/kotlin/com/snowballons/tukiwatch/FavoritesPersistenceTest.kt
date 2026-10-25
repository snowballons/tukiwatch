package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.db.ExportedFavorite
import com.snowballons.tukiwatch.model.Favorite
import com.snowballons.tukiwatch.db.FavoritesBackup
import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.db.TukiWatchDatabase
import com.snowballons.tukiwatch.db.createFavoritesDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real SQLite (JDBC driver), so schema constraints are enforced, not mocked. */
class FavoritesPersistenceTest {
    private fun repo() = FavoritesRepository(createFavoritesDriver())

    @Test
    fun addThenListPreservesInsertOrderAndIds() = runBlocking {
        val r = repo()
        assertTrue(r.addFavorite("LofiGirl", "https://www.twitch.tv/lofigirl"))
        assertTrue(r.addFavorite("xqc", "https://kick.com/xqc"))
        val all = r.getFavorites()
        assertEquals(
            listOf("https://www.twitch.tv/lofigirl", "https://kick.com/xqc"),
            all.map { it.originalUrl },
        )
        // AUTOINCREMENT starts at 1: StreamContext joined status rows onto these ids.
        assertEquals(listOf(1L, 2L), all.map { it.id })
        assertEquals("LofiGirl", all.first().streamerName)
    }

    @Test
    fun duplicateUrlIsRejectedAndKeepsTheFirstRow() = runBlocking {
        val r = repo()
        assertTrue(r.addFavorite("Original", "https://twitch.tv/a"))
        assertFalse(r.addFavorite("Renamed", "https://twitch.tv/a"))
        assertEquals("Original", r.getFavorites().single().streamerName)
    }

    @Test
    fun uniqueIndexRejectsDuplicatesEvenWhenTheGuardIsBypassed() {
        // The pre-check in addFavorite() is convenience; the DB must also enforce
        // it, or a racing writer / imported row can create a duplicate.
        val queries = TukiWatchDatabase(createFavoritesDriver()).favoritesQueries
        queries.insert("A", "https://twitch.tv/a")
        assertFailsWith<Exception> { queries.insert("B", "https://twitch.tv/a") }
    }

    @Test
    fun isFavoriteTracksRemoval() = runBlocking {
        val r = repo()
        r.addFavorite("A", "https://twitch.tv/a")
        assertTrue(r.isFavorite("https://twitch.tv/a"))
        r.removeFavorite(r.getFavorites().single().id)
        assertFalse(r.isFavorite("https://twitch.tv/a"))
        assertTrue(r.getFavorites().isEmpty())
    }

    @Test
    fun removeFavoriteIgnoresUnknownId() = runBlocking {
        val r = repo()
        r.removeFavorite(999L)
        assertTrue(r.getFavorites().isEmpty())
    }

    @Test
    fun importCountsExistingUrlsAsSkipped() = runBlocking {
        val r = repo()
        r.addFavorite("Existing", "https://twitch.tv/a")
        val outcome = r.importAll(
            listOf(
                ExportedFavorite("Existing", "https://twitch.tv/a"),
                ExportedFavorite("New", "https://twitch.tv/b"),
            ),
        )
        assertEquals(1, outcome.imported)
        assertEquals(1, outcome.skipped)
        assertEquals(2, r.getFavorites().size)
    }

    @Test
    fun exportEmitsSnakeCaseV1Envelope() {
        // Property renames are the real regression risk: the old app is the only
        // producer of these files, so the wire names are frozen.
        val json = FavoritesBackup.serialize(
            listOf(Favorite(1, "LofiGirl", "https://www.twitch.tv/lofigirl")),
            "2026-10-25T00:00:00.000Z",
        )
        assertTrue("\"app\": \"tukiwatch\"" in json, json)
        assertTrue("\"version\": 1" in json, json)
        assertTrue("\"exportedAt\": \"2026-10-25T00:00:00.000Z\"" in json, json)
        assertTrue("\"streamer_name\": \"LofiGirl\"" in json, json)
        assertTrue("\"original_url\": \"https://www.twitch.tv/lofigirl\"" in json, json)
    }

    @Test
    fun exportRoundTripsThroughParse() {
        val source = listOf(
            Favorite(1, "A", "https://twitch.tv/a"),
            Favorite(2, "B", "https://kick.com/b"),
        )
        assertEquals(
            source.map { ExportedFavorite(it.streamerName, it.originalUrl) },
            FavoritesBackup.parse(FavoritesBackup.serialize(source, "2026-10-25T00:00:00.000Z")),
        )
    }

    @Test
    fun parseAcceptsBareArrayShape() {
        assertEquals(
            listOf(ExportedFavorite("A", "https://twitch.tv/a")),
            FavoritesBackup.parse(
                """[{"streamer_name":"A","original_url":"https://twitch.tv/a"}]""",
            ),
        )
    }

    @Test
    fun parseRejectsPayloadWithoutFavoritesArray() {
        assertFailsWith<IllegalArgumentException> { FavoritesBackup.parse("""{"foo":1}""") }
        assertFailsWith<IllegalArgumentException> { FavoritesBackup.parse("""42""") }
    }

    @Test
    fun parseSkipsBlankMalformedAndNullEntries() {
        // db.ts skipped these via `?.trim()`; a null field must not crash import.
        val items = FavoritesBackup.parse(
            """
            {"favorites":[
              {"streamer_name":"  ","original_url":"https://twitch.tv/a"},
              {"streamer_name":"B","original_url":null},
              {"original_url":"https://twitch.tv/c"},
              {"streamer_name":"D"},
              "not-an-object",
              {"streamer_name":"  E  ","original_url":"  https://twitch.tv/e  "}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf(ExportedFavorite("E", "https://twitch.tv/e")), items)
    }
}
