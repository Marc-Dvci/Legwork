"""Sign-in-with-Solana verification and session tokens."""

from __future__ import annotations

import base64
import re
import time
from datetime import datetime, timezone

import base58
import jwt
from solders.pubkey import Pubkey
from solders.signature import Signature


class AuthError(Exception):
    pass


def parse_siws(message: str) -> dict:
    """Parses the SIWS text the wallet signed. Returns domain, address and the key/value fields."""
    lines = message.split("\n")
    m = re.match(r"^(?P<domain>[^ ]+) wants you to sign in with your Solana account:$", lines[0])
    if not m or len(lines) < 2:
        raise AuthError("Malformed sign-in message")
    fields = {}
    for line in lines[2:]:
        if ": " in line:
            k, v = line.split(": ", 1)
            fields[k.strip()] = v.strip()
    return {"domain": m.group("domain"), "address": lines[1].strip(), **fields}


def verify_siws(message_b64: str, signature_b58: str, wallet: str, expected_domain: str, max_age_s: int = 600) -> str:
    """Checks the signature, the address, the domain and the issue time. Returns the wallet."""
    raw = base64.b64decode(message_b64)
    text = raw.decode("utf-8")
    parsed = parse_siws(text)
    if parsed["address"] != wallet:
        raise AuthError("Signed address does not match wallet")
    if parsed["domain"] != expected_domain:
        raise AuthError(f"Sign-in domain {parsed['domain']} is not {expected_domain}")
    issued = parsed.get("Issued At")
    if issued:
        try:
            ts = datetime.fromisoformat(issued.replace("Z", "+00:00")).timestamp()
        except ValueError as e:
            raise AuthError("Bad Issued At") from e
        if abs(time.time() - ts) > max_age_s:
            raise AuthError("Sign-in message is stale")
    pubkey = Pubkey.from_string(wallet)
    sig = Signature.from_bytes(base58.b58decode(signature_b58))
    if not sig.verify(pubkey, raw):
        raise AuthError("Signature does not verify")
    return wallet


def issue_token(secret: str, wallet: str, ttl_s: int = 30 * 86_400) -> str:
    now = int(time.time())
    return jwt.encode({"sub": wallet, "iat": now, "exp": now + ttl_s}, secret, algorithm="HS256")


def read_token(secret: str, token: str) -> str:
    try:
        return jwt.decode(token, secret, algorithms=["HS256"])["sub"]
    except jwt.PyJWTError as e:
        raise AuthError("Invalid session") from e
