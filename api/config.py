import os
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()


class Config:
    ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "*").split(",")

    # Twitch-specific configuration (optional)
    TWITCH_OAUTH_TOKEN = os.getenv(
        "TWITCH_OAUTH_TOKEN", ""
    )  # For ad-free streams (Twitch Turbo)

    # Redis configuration
    REDIS_URL = os.getenv("REDIS_URL", "")
    REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
    REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
    REDIS_DB = int(os.getenv("REDIS_DB", 0))
    REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "") or None


config = Config()
