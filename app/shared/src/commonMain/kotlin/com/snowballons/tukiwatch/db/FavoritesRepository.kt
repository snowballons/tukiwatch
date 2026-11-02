package com.snowballons.tukiwatch.db

import app.cash.sqldelight.db.SqlDriver
import com.snowballons.tukiwatch.model.Favorite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of a backup import, matching db.ts `{ imported, skipped }`. */
data class ImportOutcome(val imported: Int, val skipped: Int)

/**
 * 1:1 port of `app/lib/db.ts` over the same `favorites` table
 * (`Favorites.sq` keeps every column name, order, type and the UNIQUE).
 *
 * @param dispatcher blocking-DB dispatcher. Defaults to [Dispatchers.Default]
 *   because `Dispatchers.IO` does not exist in common code; the Android app
 *   passes IO, tests pass a confined single-thread dispatcher.
 */
class FavoritesRepository(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val queries = TukiWatchDatabase(driver).favoritesQueries

    suspend fun getFavorites(): List<Favorite> = withContext(dispatcher) {
        queries.selectAll { id, streamerName, originalUrl ->
            Favorite(id, streamerName, originalUrl)
        }.executeAsList()
    }

    suspend fun isFavorite(originalUrl: String): Boolean = withContext(dispatcher) {
        queries.existsByUrl(originalUrl).executeAsOneOrNull() != null
    }

    /** False when the URL is already favorited — db.ts's exact return contract. */
    suspend fun addFavorite(streamerName: String, originalUrl: String): Boolean {
        if (isFavorite(originalUrl)) return false
        withContext(dispatcher) { queries.insert(streamerName, originalUrl) }
        return true
    }

    suspend fun removeFavorite(id: Long) {
        withContext(dispatcher) { queries.deleteById(id) }
    }

    /** Id of a stored URL, for removal-by-URL callers (db.ts removed by id). */
    suspend fun idForUrl(originalUrl: String): Long? = withContext(dispatcher) {
        queries.existsByUrl(originalUrl).executeAsOneOrNull()
    }

    /**
     * Sequential import, as db.ts does it: existing URLs count as skipped.
     * The UNIQUE index is the backstop if a row appears between check and insert.
     */
    suspend fun importAll(items: List<ExportedFavorite>): ImportOutcome {
        var imported = 0
        var skipped = 0
        for (item in items) {
            if (addFavorite(item.streamerName, item.originalUrl)) imported++ else skipped++
        }
        return ImportOutcome(imported, skipped)
    }
}
