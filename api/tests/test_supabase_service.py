import pytest
from unittest.mock import MagicMock, patch
from app.services.supabase_service import SupabaseService
from config import config


@pytest.fixture
def mock_supabase_client():
    with patch("app.services.supabase_service.create_client") as mock_create:
        mock_client = MagicMock()
        mock_create.return_value = mock_client
        yield mock_client


@pytest.fixture
def supabase_service(mock_supabase_client):
    # Ensure config has some values so lazy init works
    with patch.object(config, "SUPABASE_URL", "https://example.supabase.co"):
        with patch.object(config, "SUPABASE_SERVICE_KEY", "fake-key"):
            service = SupabaseService()
            yield service


def test_get_community_streams_success(supabase_service, mock_supabase_client):
    # Setup mock response
    mock_data = [{"id": "1", "original_url": "https://twitch.tv/stream1"}]
    mock_supabase_client.table.return_value.select.return_value.execute.return_value.data = mock_data

    streams = supabase_service.get_community_streams()

    assert streams == mock_data
    mock_supabase_client.table.assert_called_with("community_streams")
    mock_supabase_client.table().select.assert_called_with("id, original_url")


def test_get_community_streams_error(supabase_service, mock_supabase_client):
    # Setup mock to raise error
    mock_supabase_client.table.side_effect = Exception("DB Error")

    streams = supabase_service.get_community_streams()

    assert streams == []


def test_update_stream_status_success(supabase_service, mock_supabase_client):
    supabase_service.update_stream_status("123", True)

    mock_supabase_client.table.assert_called_with("community_streams")
    mock_supabase_client.table().update.assert_called()
    # Check that it called eq("id", "123")
    mock_supabase_client.table().update().eq.assert_called_with("id", "123")


def test_lazy_initialization_error():
    # Test that it raises ValueError if config is missing
    with patch.object(config, "SUPABASE_URL", ""):
        with patch.object(config, "SUPABASE_SERVICE_KEY", ""):
            service = SupabaseService()
            with pytest.raises(ValueError, match="Supabase configuration missing"):
                _ = service.supabase
