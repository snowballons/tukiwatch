package com.snowballons.tukiwatch.liveness

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class JsonFileLivenessCache(private val file: File) : LivenessCache {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): List<LiveStream> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<LiveStream>>(file.readText())
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun save(streams: List<LiveStream>) = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            file.writeText(json.encodeToString(streams))
        } catch (_: Throwable) {
        }
    }
}
