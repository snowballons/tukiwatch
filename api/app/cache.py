import time
from typing import Dict, Any, Optional
from dataclasses import dataclass


@dataclass
class CacheEntry:
    data: Any
    timestamp: float
    ttl: float

    def is_expired(self) -> bool:
        return time.time() - self.timestamp > self.ttl


class SimpleCache:
    def __init__(self):
        self._cache: Dict[str, CacheEntry] = {}

    def get(self, key: str) -> Optional[Any]:
        if key in self._cache:
            entry = self._cache[key]
            if not entry.is_expired():
                return entry.data
            else:
                del self._cache[key]
        return None

    def set(self, key: str, data: Any, ttl: float = 300) -> None:
        """Set cache entry with TTL in seconds (default 5 minutes)"""
        self._cache[key] = CacheEntry(data, time.time(), ttl)

    def clear(self) -> None:
        self._cache.clear()

    def size(self) -> int:
        # Clean expired entries
        expired_keys = [k for k, v in self._cache.items() if v.is_expired()]
        for k in expired_keys:
            del self._cache[k]
        return len(self._cache)


# Global cache instance
cache = SimpleCache()
