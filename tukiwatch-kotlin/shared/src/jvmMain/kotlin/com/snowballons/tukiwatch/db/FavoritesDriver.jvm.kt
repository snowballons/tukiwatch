package com.snowballons.tukiwatch.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Desktop/test driver for `tukiwatch.db`.
 *
 * In-memory by default so unit tests are isolated; pass a path for the desktop
 * harness, which iterates on the engine rather than on persistence.
 * `Favorites.sq` uses CREATE TABLE IF NOT EXISTS, so re-running is safe.
 */
fun createFavoritesDriver(file: File? = null): SqlDriver {
    val url = if (file == null) {
        JdbcSqliteDriver.IN_MEMORY
    } else {
        file.parentFile?.mkdirs()
        "jdbc:sqlite:${file.absolutePath}"
    }
    val driver = JdbcSqliteDriver(url)
    TukiWatchDatabase.Schema.create(driver)
    return driver
}
