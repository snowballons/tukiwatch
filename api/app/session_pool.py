import queue
import threading
import time
from streamlink.session import Streamlink
from config import config


class StreamlinkSessionPool:
    """Simple session pool with 3 pre-configured Streamlink sessions"""

    def __init__(self, pool_size: int = 3):
        self.pool_size = pool_size
        self.sessions = queue.Queue(maxsize=pool_size)
        self.lock = threading.Lock()
        self.created_at = time.time()
        self.refresh_interval = 3600  # Refresh sessions every hour

        # Create initial sessions
        self._create_sessions()

    def _create_session(self) -> Streamlink:
        """Create a pre-configured Streamlink session"""
        session = Streamlink()
        session.set_option("webbrowser-executable", config.CHROME_PATH)
        session.set_option(
            "http-headers",
            "User-Agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
        )
        return session

    def _create_sessions(self):
        """Fill the pool with sessions"""
        for _ in range(self.pool_size):
            session = self._create_session()
            self.sessions.put(session)

    def get_session(self) -> Streamlink:
        """Get a session from the pool"""
        try:
            # Check if sessions need refresh
            if time.time() - self.created_at > self.refresh_interval:
                self._refresh_pool()

            # Get session with timeout
            return self.sessions.get(timeout=5)
        except queue.Empty:
            # Fallback: create new session if pool is empty
            return self._create_session()

    def return_session(self, session: Streamlink):
        """Return a session to the pool"""
        try:
            self.sessions.put_nowait(session)
        except queue.Full:
            # Pool is full, discard the session
            pass

    def _refresh_pool(self):
        """Refresh all sessions in the pool"""
        with self.lock:
            # Only refresh if we haven't refreshed recently
            if time.time() - self.created_at < self.refresh_interval:
                return

            # Clear old sessions
            while not self.sessions.empty():
                try:
                    self.sessions.get_nowait()
                except queue.Empty:
                    break

            # Create fresh sessions
            self._create_sessions()
            self.created_at = time.time()

    def size(self) -> int:
        """Get current pool size"""
        return self.sessions.qsize()


# Global session pool instance
session_pool = StreamlinkSessionPool()
