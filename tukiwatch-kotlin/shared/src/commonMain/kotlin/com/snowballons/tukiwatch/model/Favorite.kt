package com.snowballons.tukiwatch.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the Expo `Favorite` type (app/src/types/index.ts) and the `selectAll`
 * projection: `created_at` is deliberately not exposed.
 *
 * Lives outside `com.snowballons.tukiwatch.db` because SQLDelight generates a
 * row class named `Favorite` from the `favorites` table in that package.
 *
 * Kotlin properties are camelCase; wire names stay snake_case via [SerialName]
 * because the JSON export v1 format is the migration contract.
 */
@Serializable
data class Favorite(
    val id: Long,
    @SerialName("streamer_name") val streamerName: String,
    @SerialName("original_url") val originalUrl: String,
)
