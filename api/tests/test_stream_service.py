import pytest
from unittest.mock import MagicMock, patch
from app.services.stream_service import _resolve_stream_sync, resolve_stream_details
from app.models import StreamStatus
from streamlink.exceptions import NoPluginError, NoStreamsError

@patch("app.services.stream_service.session_pool")
@patch("app.services.stream_service.extract_platform_from_url")
def test_resolve_stream_sync_online(mock_extract, mock_pool):
    mock_extract.return_value = "twitch"
    
    mock_session = MagicMock()
    mock_pool.get_session.return_value = mock_session
    
    mock_plugin_instance = MagicMock()
    mock_plugin_instance.streams.return_value = {"best": MagicMock(url="http://best-url")}
    mock_plugin_instance.get_metadata.return_value = {
        "title": "Test Title",
        "author": "Test Author",
        "category": "Gaming",
        "id": "123"
    }
    
    mock_plugin_class = MagicMock(return_value=mock_plugin_instance)
    mock_session.resolve_url.return_value = ("twitch", mock_plugin_class, "https://twitch.tv/test")
    
    result = _resolve_stream_sync("https://twitch.tv/test")
    
    assert isinstance(result, StreamStatus)
    assert result.status == "online"
    assert result.title == "Test Title"
    assert result.author == "Test Author"
    assert result.platform == "twitch"
    mock_pool.return_session.assert_called_once()

@patch("app.services.stream_service.session_pool")
@patch("app.services.stream_service.extract_platform_from_url")
def test_resolve_stream_sync_offline(mock_extract, mock_pool):
    mock_extract.return_value = "twitch"
    
    mock_session = MagicMock()
    mock_pool.get_session.return_value = mock_session
    
    mock_plugin_instance = MagicMock()
    mock_plugin_instance.streams.return_value = {} # No streams means offline
    
    mock_plugin_class = MagicMock(return_value=mock_plugin_instance)
    mock_session.resolve_url.return_value = ("twitch", mock_plugin_class, "https://twitch.tv/test")
    
    result = _resolve_stream_sync("https://twitch.tv/test")
    
    assert result.status == "offline"

@patch("app.services.stream_service.session_pool")
@patch("app.services.stream_service.extract_platform_from_url")
def test_resolve_stream_sync_no_plugin(mock_extract, mock_pool):
    mock_extract.return_value = "unknown"
    
    mock_session = MagicMock()
    mock_pool.get_session.return_value = mock_session
    mock_session.resolve_url.side_effect = NoPluginError()
    
    result = _resolve_stream_sync("https://unknown.com/test")
    
    assert result.status == "error"
    assert "No plugin available" in result.error

@patch("app.services.stream_service.session_pool")
@patch("app.services.stream_service.extract_platform_from_url")
def test_resolve_stream_details_success(mock_extract, mock_pool):
    mock_extract.return_value = "twitch"
    
    mock_session = MagicMock()
    mock_pool.get_session.return_value = mock_session
    
    mock_plugin_instance = MagicMock()
    best_stream = MagicMock()
    best_stream.url = "http://playback-url"
    mock_plugin_instance.streams.return_value = {"best": best_stream, "720p": best_stream}
    mock_plugin_instance.get_metadata.return_value = {
        "title": "Test Stream",
        "author": "Tester",
    }
    
    mock_plugin_class = MagicMock(return_value=mock_plugin_instance)
    mock_session.resolve_url.return_value = ("twitch", mock_plugin_class, "https://twitch.tv/test")
    
    result = resolve_stream_details("https://twitch.tv/test")
    
    assert result["status"] == "online"
    assert result["best_quality"] == "http://playback-url"
    assert "720p" in result["all_qualities"]
