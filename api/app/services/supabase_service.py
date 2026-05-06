from supabase import create_client, Client
from config import config
import logging
from datetime import datetime

logger = logging.getLogger(__name__)


class SupabaseService:
    def __init__(self):
        self.supabase: Client = create_client(
            config.SUPABASE_URL, config.SUPABASE_SERVICE_KEY
        )

    def get_community_streams(self):
        """Fetch all community streams that need liveness checks."""
        try:
            response = (
                self.supabase.table("community_streams")
                .select("id, original_url")
                .execute()
            )
            return response.data
        except Exception as e:
            logger.error(f"Error fetching community streams: {e}")
            return []

    def update_stream_status(self, stream_id: str, is_online: bool):
        """Update is_online and last_checked for a specific stream."""
        try:
            self.supabase.table("community_streams").update(
                {"is_online": is_online, "last_checked": datetime.utcnow().isoformat()}
            ).eq("id", stream_id).execute()
        except Exception as e:
            logger.error(f"Error updating stream {stream_id} status: {e}")

    async def update_stream_status_batch(self, updates: list):
        """Update multiple streams status (if Supabase supports efficient bulk update via RPC or similar).
        For now, we'll do individual updates in the worker or a simple loop here.
        """
        # Note: True bulk update usually requires a custom RPC function in Supabase/PostgreSQL.
        # For simplicity and given standard row limits, we'll iterate.
        for update in updates:
            self.update_stream_status(update["id"], update["is_online"])


supabase_service = SupabaseService()
