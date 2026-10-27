package com.snowballons.tukiwatch.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Desktop/test driver for `tukiwatch.db`.
 *
 * In-memory by default so unit tests are isolated; pass a path for the desktop
 * harness so favorites survive restarts.
 *
 * SQLDelight's generated `Schema.create` emits `CREATE TABLE favorites`
 * unconditionally, not `IF NOT EXISTS`, so calling it on an existing file
 * throws `table favorites already exists`. The schema version is tracked in
 * SQLite's `user_version` (set on first create); a database created before
 * version tracking existed is adopted by detecting the table.
 */
fun createFavoritesDriver(file: File? = null): SqlDriver {
    val url = if (file == null) {
        JdbcSqliteDriver.IN_MEMORY
    } else {
        file.parentFile?.mkdirs()
        "jdbc:sqlite:${file.absolutePath}"
    }
    val driver = JdbcSqliteDriver(url)
    val targetVersion = TukiWatchDatabase.Schema.version
    val currentVersion = driver.userVersion()

    when {
        currentVersion == targetVersion -> Unit
        // Pre-versioned harness DB: it already has the v1 schema.
        currentVersion == 0L && driver.hasFavoritesTable() -> driver.setUserVersion(targetVersion)
        currentVersion == 0L -> {
            TukiWatchDatabase.Schema.create(driver)
            driver.setUserVersion(targetVersion)
        }
        else -> error(
            "favorites schema v$currentVersion is older than v$targetVersion; " +
                "add a desktop migration before bumping the schema."
        )
    }
    return driver
}

private fun SqlDriver.userVersion(): Long {
    var version = 0L
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            if (cursor.next().value) version = cursor.getLong(0) ?: 0L
            QueryResult.Value(Unit)
        },
        parameters = 0,
    )
    return version
}

private fun SqlDriver.setUserVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}

private fun SqlDriver.hasFavoritesTable(): Boolean {
    var exists = false
    executeQuery(
        identifier = null,
        sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'favorites'",
        mapper = { cursor ->
            exists = cursor.next().value
            QueryResult.Value(Unit)
        },
        parameters = 0,
    )
    return exists
}
