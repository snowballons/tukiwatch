from fastapi import FastAPI
from app.routers import streams

app = FastAPI()

app.include_router(streams.router)

@app.get("/")
def read_root():
    return {"status": "ok", "service": "streamlink-api"}
