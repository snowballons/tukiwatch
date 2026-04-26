import pytest
from fastapi.testclient import TestClient
import os
from unittest.mock import patch, MagicMock

# Set environment variables for testing
os.environ["API_KEY"] = "test-api-key"
os.environ["REDIS_URL"] = "" # Disable redis for tests

@pytest.fixture(autouse=True)
def mock_redis():
    """Mock redis to avoid connection errors during tests"""
    with patch("redis.from_url") as mock:
        mock_instance = MagicMock()
        mock.return_value = mock_instance
        yield mock_instance

@pytest.fixture
def client():
    """FastAPI test client fixture"""
    from main import app
    with TestClient(app) as c:
        yield c

@pytest.fixture
def auth_headers():
    """Headers for authenticated requests"""
    return {"X-API-Key": "test-api-key"}
