package com.snowballons.tukiwatch.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android driver over `tukiwatch.db` — same filename as the Expo app, which is
 * irrelevant across applicationIds but keeps the schema mentally continuous.
 * `AndroidSqliteDriver` applies `TukiWatchDatabase.Schema` on open, replacing
 * db.ts's `CREATE TABLE IF NOT EXISTS` + WAL pragma.
 */
fun createFavoritesDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(TukiWatchDatabase.Schema, context.applicationContext, "tukiwatch.db")
