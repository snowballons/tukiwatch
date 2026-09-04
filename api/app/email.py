"""Email service for supporter notifications."""

import logging

logger = logging.getLogger(__name__)


class EmailService:
    """Service for sending supporter notifications via email."""

    def __init__(self):
        self._client = None

    def is_configured(self) -> bool:
        """Check if email service is configured."""
        return self._client is not None

    async def send_supporter_notification(self, email: str, details: dict) -> bool:
        """Send supporter notification email.

        Args:
            email: Recipient email address
            details: Supporter notification details (license, tier, etc.)

        Returns:
            bool: True if email sent successfully, False otherwise
        """
        logger.warning(
            "Supporter notifications are now handled through Lemon Squeezy webhook notifications. "
            "Email sending for %s disabled.",
            email,
        )
        return False


# Global instance
email_service = EmailService()
