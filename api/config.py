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


config = Config()
