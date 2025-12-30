from fastapi import APIRouter, HTTPException
import asyncio
from typing import List
from app.models import BatchRequest, StreamStatus
from app.services import stream_service

router = APIRouter()

@router.get("/resolve")
async def get_stream_url(url: str):
    try:
        # We need to run the synchronous resolve_stream_details in a thread pool
        # because it uses blocking streamlink calls
        return await asyncio.to_thread(stream_service.resolve_stream_details, url)
    except ValueError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/status-batch")
async def get_batch_status(request_data: BatchRequest):
    # Limit batch size
    if len(request_data.urls) > 20:
        raise HTTPException(status_code=400, detail="Maximum 20 URLs per batch")
    
    # Process all URLs concurrently
    tasks = [stream_service.check_single_stream(url) for url in request_data.urls]
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    # Handle any exceptions from gather
    statuses = []
    for i, result in enumerate(results):
        if isinstance(result, Exception):
            statuses.append(StreamStatus(
                url=request_data.urls[i],
                status="error", 
                error=str(result)
            ))
        else:
            statuses.append(result)
    
    return {"results": statuses}
