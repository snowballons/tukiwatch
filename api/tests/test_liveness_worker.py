import pytest
from unittest.mock import patch, AsyncMock
from app.services.liveness_worker import check_community_liveness
from app.models import StreamStatus


@pytest.mark.asyncio
async def test_check_community_liveness_success():
    # Mock supabase_service.get_community_streams
    mock_streams = [
        {"id": "1", "original_url": "https://twitch.tv/online"},
        {"id": "2", "original_url": "https://twitch.tv/offline"},
    ]

    with patch("app.services.liveness_worker.supabase_service") as mock_supabase:
        mock_supabase.get_community_streams.return_value = mock_streams

        # Mock stream_service.check_single_stream
        with patch(
            "app.services.liveness_worker.stream_service.check_single_stream",
            new_callable=AsyncMock,
        ) as mock_check:
            # First stream online, second offline
            mock_check.side_effect = [
                StreamStatus(url="...", status="online"),
                StreamStatus(url="...", status="offline"),
            ]

            # Run the worker
            await check_community_liveness()

            # Verify supabase updates
            assert mock_supabase.update_stream_status.call_count == 2
            mock_supabase.update_stream_status.assert_any_call("1", True)
            mock_supabase.update_stream_status.assert_any_call("2", False)


@pytest.mark.asyncio
async def test_check_community_liveness_no_streams():
    with patch("app.services.liveness_worker.supabase_service") as mock_supabase:
        mock_supabase.get_community_streams.return_value = []

        await check_community_liveness()

        # Should not call check_single_stream or update_stream_status
        assert mock_supabase.update_stream_status.call_count == 0


@pytest.mark.asyncio
async def test_check_community_liveness_with_error():
    mock_streams = [{"id": "1", "original_url": "https://twitch.tv/error"}]

    with patch("app.services.liveness_worker.supabase_service") as mock_supabase:
        mock_supabase.get_community_streams.return_value = mock_streams

        with patch(
            "app.services.liveness_worker.stream_service.check_single_stream",
            new_callable=AsyncMock,
        ) as mock_check:
            # Simulate an exception in check_single_stream
            mock_check.side_effect = Exception("Streamlink error")

            await check_community_liveness()

            # Should log error and NOT call update_stream_status for the failed one
            assert mock_supabase.update_stream_status.call_count == 0
