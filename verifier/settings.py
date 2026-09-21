"""Runtime configuration for the Legwork verifier. Every value comes from the environment."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path

from solders.keypair import Keypair

# Circle's devnet USDC. On mainnet: EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v
DEFAULT_USDC_DEVNET = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU"
SKR_MAINNET = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"


def _load_keypair(value: str | None) -> Keypair | None:
    """Accepts a path to a Solana CLI JSON keypair or the JSON array itself."""
    if not value:
        return None
    if value.strip().startswith("["):
        return Keypair.from_bytes(bytes(json.loads(value)))
    return Keypair.from_bytes(bytes(json.loads(Path(value).read_text())))


@dataclass
class Settings:
    cluster: str = os.environ.get("CLUSTER", "devnet")
    rpc_url: str = os.environ.get("RPC_URL", "https://api.devnet.solana.com")
    # RPC the phones use for their own reads; differs from RPC_URL only when the verifier runs beside a local validator.
    public_rpc_url: str = os.environ.get("PUBLIC_RPC_URL") or os.environ.get("RPC_URL", "https://api.devnet.solana.com")
    program_id: str = os.environ.get("PROGRAM_ID", "86V9Vw6jPnoy4R6feJK6atwMK3x1oUootvvbqb8iZHD6")
    usdc_mint: str = os.environ.get("USDC_MINT", DEFAULT_USDC_DEVNET)
    skr_mint: str = os.environ.get("SKR_MINT", "")
    verifier: Keypair | None = field(default_factory=lambda: _load_keypair(os.environ.get("VERIFIER_KEYPAIR")))
    jwt_secret: str = os.environ.get("JWT_SECRET", "")
    identity_domain: str = os.environ.get("IDENTITY_DOMAIN", "legwork.app")
    gemini_api_key: str = os.environ.get("GEMINI_API_KEY", "")
    gemini_model: str = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
    vertex_project: str = os.environ.get("VERTEX_PROJECT", "")
    vertex_location: str = os.environ.get("VERTEX_LOCATION", "global")
    explorer_base: str = os.environ.get("EXPLORER_BASE", "https://explorer.solana.com")
    map_style_url: str = os.environ.get("MAP_STYLE_URL", "https://tiles.openfreemap.org/styles/liberty")
    # Optional display names for creators: {"<pubkey>": {"name": "...", "verified": true}}
    creators: dict = field(default_factory=lambda: json.loads(os.environ.get("CREATOR_NAMES", "{}")))
    # Proof policy
    max_accuracy_m: float = float(os.environ.get("MAX_ACCURACY_M", "100"))
    max_photo_bytes: int = int(os.environ.get("MAX_PHOTO_BYTES", str(6 * 1024 * 1024)))
    max_clock_skew_s: int = int(os.environ.get("MAX_CLOCK_SKEW_S", "600"))
    min_confidence: int = int(os.environ.get("MIN_CONFIDENCE", "60"))
    # When true, verification skips the vision model and approves on GPS alone (local tests only).
    skip_vision: bool = os.environ.get("SKIP_VISION", "") == "1"

    def require_verifier(self) -> Keypair:
        if self.verifier is None:
            raise RuntimeError("VERIFIER_KEYPAIR is not configured")
        return self.verifier


settings = Settings()
