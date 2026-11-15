package com.snowballons.tukiwatch.liveness

interface LivenessCache {
    suspend fun load(): List<LiveStream>
    suspend fun save(streams: List<LiveStream>)
}

/** In-memory cache implementation, useful for tests. */
class MemoryLivenessCache(initial: List<LiveStream> = emptyList()) : LivenessCache {
    private var cached = initial
    override suspend fun load(): List<LiveStream> = cached
    override suspend fun save(streams: List<LiveStream>) {
        cached = streams
    }
}
