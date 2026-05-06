import asyncio
import logging
from app.services.supabase_service import supabase_service
from app.services import stream_service

logger = logging.getLogger(__name__)


async def check_community_liveness():
    """Background task to check liveness of all community streams."""
    logger.info("Starting hourly community liveness check...")

    streams = supabase_service.get_community_streams()
    if not streams:
        logger.info("No community streams found to check.")
        return

    logger.info(f"Found {len(streams)} streams to check.")

    # Process in chunks to avoid overwhelming the system or rate limits
    CHUNK_SIZE = 10
    for i in range(0, len(streams), CHUNK_SIZE):
        chunk = streams[i : i + CHUNK_SIZE]
        tasks = []
        for s in chunk:
            tasks.append(stream_service.check_single_stream(s["original_url"]))

        results = await asyncio.gather(*tasks, return_exceptions=True)

        for j, result in enumerate(results):
            stream_id = chunk[j]["id"]
            if isinstance(result, Exception):
                logger.error(
                    f"Error checking stream {chunk[j]['original_url']}: {result}"
                )
                # We might want to keep current status or set to error/offline
                continue

            is_online = result.status == "online"
            supabase_service.update_stream_status(stream_id, is_online)

        logger.info(
            f"Processed chunk {i // CHUNK_SIZE + 1}/{(len(streams) - 1) // CHUNK_SIZE + 1}"
        )
        # Small delay between chunks if needed
        await asyncio.sleep(1)

    logger.info("Community liveness check completed.")
