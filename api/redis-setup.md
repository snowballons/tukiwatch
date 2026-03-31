# Migrating to Redis Caching System

This guide outlines the step-by-step process for migrating your current custom in-memory caching system to Redis. Redis offers enhanced persistence, scalability, and a richer feature set, making it a more robust and cost-effective solution for production environments.

## 1. Why Migrate to Redis?

The current custom in-memory cache, while simple, has several limitations:
*   **Ephemeral Data:** All cached data is lost when the application restarts.
*   **No Scalability:** In a distributed setup (multiple application instances), each instance has its own cache, leading to inconsistencies and lower cache hit rates across the cluster.
*   **Limited Features:** Lacks advanced caching features like persistence, replication, and diverse data structures.
*   **Maintenance Overhead:** You are responsible for all aspects of the custom cache's development and maintenance.

Redis, an open-source, in-memory data structure store, addresses these issues by providing:
*   **Persistence:** Data can be saved to disk, surviving application restarts.
*   **Scalability:** A single Redis instance can serve multiple application instances, ensuring consistent caching across your entire infrastructure.
*   **High Performance:** Extremely fast read/write operations due to its in-memory nature.
*   **Rich Features:** Supports various data structures (strings, hashes, lists, sets, etc.), atomic operations, publish/subscribe, and more.
*   **Maturity & Ecosystem:** Battle-tested in production environments with robust client libraries (like `redis-py` for Python) and extensive community support.

## 2. Prerequisites

Before you begin, ensure you have:
*   Access to a running Redis server (local or hosted).
*   Your project's Python environment set up.

## 3. Step 1: Install Redis Server

If you don't already have a Redis server, here's how to install it locally on a common Linux distribution (e.g., Ubuntu):

```bash
sudo apt update
sudo apt install redis-server
sudo systemctl enable redis-server
sudo systemctl start redis-server
```

**For other operating systems or managed Redis services (e.g., AWS ElastiCache, Google Cloud Memorystore), refer to their respective documentation for installation and connection details.**

## 4. Step 2: Install `redis-py`

Add the `redis-py` client library to your project's dependencies:

```bash
pip install redis
```
Then update your `requirements.txt`:
```bash
pip freeze > requirements.txt
```

## 5. Step 3: Update Configuration (`config.py`)

Add Redis connection details to your `config.py` file. It's best practice to use environment variables for sensitive information like passwords and hostnames.

**`config.py` (Example changes):**

```python
# ... other configurations

# Redis Configuration
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
REDIS_DB = int(os.getenv("REDIS_DB", 0))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", None) # Set to None if no password
REDIS_DECODE_RESPONSES = True # Decode responses to Python strings by default
```

You'll need to set these environment variables in your deployment environment or locally for testing. For example:
```bash
export REDIS_HOST="your_redis_host"
export REDIS_PORT="your_redis_port"
export REDIS_PASSWORD="your_redis_password"
```

## 6. Step 4: Refactor `app/cache.py` to use Redis

We will modify `app/cache.py` to switch from the in-memory dictionary to Redis. The goal is to keep the public interface (`get`, `set`, `delete`) as similar as possible to minimize changes in other parts of the codebase.

**`app/cache.py` (Updated content):**

```python
import redis
import os
import json
from typing import Any, Optional

# Assuming your config.py is structured to be importable
# from config import REDIS_HOST, REDIS_PORT, REDIS_DB, REDIS_PASSWORD, REDIS_DECODE_RESPONSES
# For simplicity in this guide, we'll mimic direct env var access or default config
# You should adapt this to your actual config loading mechanism.

class RedisCache:
    """
    A Redis-backed caching system for storing key-value pairs with TTL.
    """
    def __init__(self):
        redis_host = os.getenv("REDIS_HOST", "localhost")
        redis_port = int(os.getenv("REDIS_PORT", 6379))
        redis_db = int(os.getenv("REDIS_DB", 0))
        redis_password = os.getenv("REDIS_PASSWORD", None)

        self._redis_client = redis.StrictRedis(
            host=redis_host,
            port=redis_port,
            db=redis_db,
            password=redis_password,
            decode_responses=True # Decode Redis responses to Python strings
        )
        print(f"Connecting to Redis at {redis_host}:{redis_port}/{redis_db}")

    def get(self, key: str) -> Optional[Any]:
        """
        Retrieves a value from the cache.
        Redis handles TTL automatically, so no explicit expiration check needed here.
        """
        value = self._redis_client.get(key)
        if value:
            try:
                # Assuming stored values might be JSON-encoded
                return json.loads(value)
            except json.JSONDecodeError:
                return value # Return as-is if not JSON
        return None

    def set(self, key: str, value: Any, ttl: int) -> None:
        """
        Stores a value in the cache with a given Time-To-Live (TTL) in seconds.
        """
        # Store complex objects as JSON strings
        if not isinstance(value, (str, bytes, int, float)):
            value = json.dumps(value)
        self._redis_client.setex(key, ttl, value)

    def delete(self, key: str) -> None:
        """
        Removes a value from the cache.
        """
        self._redis_client.delete(key)

    def get_stats(self) -> dict:
        """
        Returns basic statistics about the cache.
        Note: Getting exact cache size (number of keys) in a large Redis instance
        can be an expensive operation. This provides an estimate.
        """
        return {
            "type": "RedisCache",
            "connected_to": f"{self._redis_client.connection_pool.host}:{self._redis_client.connection_pool.port}/{self._redis_client.connection_pool.db}",
            "estimated_keys": self._redis_client.dbsize(), # Number of keys in the current DB
            # Add more Redis info if needed, e.g., self._redis_client.info()
        }

# Global cache instance
cache = RedisCache()
```
**Note:** The previous `CacheEntry` class and `SimpleCache` are no longer needed and can be removed. The `print` statement in `__init__` is for debugging during migration and can be removed in production.

## 7. Step 5: Update `app/services/stream_service.py`

The `get` and `set` methods of the new `RedisCache` class are designed to be compatible with the old `SimpleCache` interface. Therefore, changes in `app/services/stream_service.py` should be minimal, if any. You just need to ensure the `cache` object imported is the new Redis-backed one.

**`app/services/stream_service.py` (Verify imports):**

```python
# Ensure this imports the updated cache instance
from app.cache import cache
# ... rest of your code
```
The logic where you call `cache.get(key)` and `cache.set(key, value, ttl)` should remain functionally the same. Redis will now handle the expiration.

## 8. Step 6: Update `app/routers/streams.py`

The manual cache invalidation logic needs to be updated to use the `delete` method of the `RedisCache` instance instead of directly popping from `_cache`.

**`app/routers/streams.py` (Example changes):**

```python
from app.cache import cache
# ... other imports

@router.get("/{stream_id}")
async def get_stream_info(
    stream_id: str,
    bypass_cache: bool = Query(False, description="Bypass cache and fetch fresh data"),
    # ... other parameters
):
    if bypass_cache:
        # Before: if stream_id in cache._cache: cache._cache.pop(stream_id)
        # Now:
        cache.delete(stream_id) # Use the delete method of RedisCache
        logger.info(f"Cache for {stream_id} bypassed and cleared.")

    # ... rest of your route logic which calls stream_service methods
    # that internally use cache.get() and cache.set()
```

## 9. Step 7: Update `main.py` (Cache Stats Endpoint)

The `/cache/stats` endpoint in `main.py` should now call the `get_stats` method of the `RedisCache` instance.

**`main.py` (Example changes for stats endpoint):**

```python
from app.cache import cache # Ensure this is the Redis-backed cache instance
# ... other imports

@app.get("/cache/stats", summary="Get cache statistics")
async def get_cache_stats():
    """
    Returns statistics about the caching system.
    """
    try:
        stats = cache.get_stats()
        return {"status": "success", "cache_stats": stats}
    except Exception as e:
        logger.error(f"Failed to retrieve cache stats: {e}")
        raise HTTPException(status_code=500, detail="Failed to retrieve cache statistics")
```

## 10. Step 8: Testing

Thoroughly test your application after the migration:

1.  **Start Redis Server:** Ensure your Redis server is running.
2.  **Start Application:** Run your Python application.
3.  **Verify Caching:**
    *   Make a request that should be cached.
    *   Make the same request again and verify it's faster (indicating a cache hit).
    *   Use `redis-cli` to inspect Redis keys: `redis-cli KEYS "*"` or `redis-cli GET <your_cache_key>`.
4.  **Verify Cache Invalidation:**
    *   Make a request.
    *   Make the same request with `bypass_cache=true` and verify the cache entry is removed from Redis (using `redis-cli`).
5.  **Verify Persistence:**
    *   Cache some data.
    *   Restart your application (but not Redis).
    *   Make a request for the cached data and verify it's still present in the cache.
6.  **Error Handling:** Test scenarios where Redis might not be available to ensure your application handles connection errors gracefully.

## 11. Step 9: Deployment Considerations

*   **Managed Redis Services:** For production, strongly consider using a managed Redis service (e.g., AWS ElastiCache, Google Cloud Memorystore, Azure Cache for Redis). These services handle patching, backups, scaling, and high availability, significantly reducing operational burden.
*   **Self-Hosted Redis:** If self-hosting, ensure you set up replication, persistence, and monitoring for your Redis instances to ensure high availability and data integrity.
*   **Security:** Always secure your Redis instance with a strong password and network access controls. Do not expose Redis directly to the public internet.

## 12. Cleanup (Optional)

Once you've successfully migrated and verified that Redis is working correctly, you can remove any old, unused `CacheEntry` and `SimpleCache` class definitions from `app/cache.py` to keep your codebase clean.
