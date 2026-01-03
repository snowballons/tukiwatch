from fastapi import APIRouter, HTTPException
import asyncio
from app.models import BatchRequest, StreamStatus
from app.services import stream_service
from app.exceptions import StreamlinkAPIException
from app.cache import cache
from app.validators import validate_url, validate_batch_request

router = APIRouter()


@router.get("/resolve")
async def get_stream_url(url: str, bypass_cache: bool = False):
    validated_url = validate_url(url)

    # Clear cache if bypass requested
    if bypass_cache:
        cache_key = f"resolve:{validated_url}"
        cache._cache.pop(cache_key, None)

    try:
        return await asyncio.to_thread(
            stream_service.resolve_stream_details, validated_url
        )
    except StreamlinkAPIException:
        raise  # Re-raise our custom exceptions with proper HTTP codes
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")


@router.post("/status-batch")
async def get_batch_status(request_data: BatchRequest, bypass_cache: bool = False):
    validated_urls = validate_batch_request(request_data.urls)

    # Clear cache if bypass requested
    if bypass_cache:
        for url in validated_urls:
            cache_key = f"status:{url}"
            cache._cache.pop(cache_key, None)

    # Process all URLs concurrently
    tasks = [stream_service.check_single_stream(url) for url in validated_urls]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    # Handle any exceptions from gather
    statuses = []
    for i, result in enumerate(results):
        if isinstance(result, Exception):
            statuses.append(
                StreamStatus(url=validated_urls[i], status="error", error=str(result))
            )
        else:
            statuses.append(result)

    return {"results": statuses}
