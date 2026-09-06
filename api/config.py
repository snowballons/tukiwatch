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
    REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
    REDIS_DB = int(os.getenv("REDIS_DB", "0"))
    REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "") or None
    # Polar (sandbox first, then prod)
    POLAR_ACCESS_TOKEN=os.getenv("POLAR_ACCESS_TOKEN", "")
    POLAR_ORG_ID=os.getenv("POLAR_ORG_ID", "")
    POLAR_PRODUCT_ID=os.getenv("POLAR_PRODUCT_ID","")
    POLAR_BENEFIT_ID=os.getenv("POLAR_BENEFIT_ID","")
    POLAR_WEBHOOK_SECRET=os.getenv("POLAR_WEBHOOK_SECRET","")
    POLAR_API_BASE=os.getenv("POLAR_API_BASE", "https://api.polar.sh")
    # Session TTLs (mirror LS plan)
    SESSION_TTL_HOURS=int(os.getenv("SESSION_TTL_HOURS","24"))
    SESSION_AUDIT_TTL_DAYS=int(os.getenv("SESSION_AUDIT_TTL_DAYS","7"))


config = Config()
