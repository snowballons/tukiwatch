"""Make the bundled engine importable when running the host-side parity tests."""

import sys
from pathlib import Path

sys.dont_write_bytecode = True

ENGINE_DIR = Path(__file__).resolve().parents[2] / "main" / "python"
sys.path.insert(0, str(ENGINE_DIR))
