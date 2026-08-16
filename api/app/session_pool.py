"""Session pool for Streamlink sessions.

Thin shim over the canonical implementation in streamwatch-core. The global
``session_pool`` instance keeps existing import sites
(``from app.session_pool import session_pool``) working unchanged.
"""

from streamwatch_core.session_pool import StreamlinkSessionPool

session_pool = StreamlinkSessionPool()