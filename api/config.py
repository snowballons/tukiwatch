import os
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

class Config:
    CHROME_PATH = os.getenv("CHROME_PATH", "/usr/bin/google-chrome")
    ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "*").split(",")

config = Config()
