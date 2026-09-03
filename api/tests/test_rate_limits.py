from app.rate_limit import RateLimitConfig
from app.rate_limiter import rate_limiter_factory


def test_free_tier_rate_limit(client):
    """Test that free tier users hit rate limits."""
    # Save original state
    original_free_limits = RateLimitConfig.FREE_LIMITS.copy()

    try:
        # Reset rate limiter state to ensure clean slate
        limiter = rate_limiter_factory.create()
        if hasattr(limiter, '_requests'):
            limiter._requests.clear()

        # Set low limits for testing
        RateLimitConfig.FREE_LIMITS = {'default': (1, 60)}

        test_url = "https://twitch.tv/testchannel"

        # Free tier should hit limit after 1 request
        free_resp1 = client.get('/api/resolve', params={'url': test_url})
        free_resp2 = client.get('/api/resolve', params={'url': test_url})
        assert free_resp1.status_code == 200
        assert free_resp2.status_code == 429
    finally:
        # Restore original state
        RateLimitConfig.FREE_LIMITS = original_free_limits
