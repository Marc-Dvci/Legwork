"""Vercel entry point: serves the FastAPI verifier in verifier/ from the repository root."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "verifier"))

from app import app  # noqa: E402,F401
