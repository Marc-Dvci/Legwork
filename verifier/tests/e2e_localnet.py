"""End-to-end run against a live verifier: sign in with a keypair, reserve a mission, submit a proof, read the payout.

    python tests/e2e_localnet.py --verifier http://localhost:8000 --key ../../keys/demo-worker.json --photo sample.jpg
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import time
from pathlib import Path

import base58
import httpx
from PIL import Image, ImageDraw
from solders.keypair import Keypair


def sign_in(client: httpx.Client, kp: Keypair, domain: str) -> str:
    wallet = str(kp.pubkey())
    issued = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    text = (
        f"{domain} wants you to sign in with your Solana account:\n{wallet}\n\n"
        "Sign in to Legwork to find paid missions near you.\n\n"
        f"URI: https://{domain}\nVersion: 1\nNonce: e2eNonce12345\nIssued At: {issued}"
    )
    raw = text.encode()
    sig = kp.sign_message(raw)
    r = client.post("/auth/siws", json={"wallet": wallet, "message": base64.b64encode(raw).decode(), "signature": base58.b58encode(bytes(sig)).decode()})
    r.raise_for_status()
    return r.json()["token"]


def synthetic_photo(text: str) -> bytes:
    img = Image.new("RGB", (1024, 768), (235, 225, 210))
    d = ImageDraw.Draw(img)
    d.rectangle((120, 200, 900, 560), fill=(255, 255, 255), outline=(30, 30, 30), width=6)
    d.text((160, 260), text, fill=(20, 20, 20))
    d.text((160, 320), "WE ACCEPT: VISA  MASTERCARD  USDC  SOL", fill=(20, 20, 20))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=90)
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verifier", default="http://localhost:8000")
    ap.add_argument("--key", required=True)
    ap.add_argument("--photo")
    ap.add_argument("--domain", default="legwork.app")
    ap.add_argument("--mission-index", type=int, default=0)
    ap.add_argument("--title", help="substring of the mission title to target")
    a = ap.parse_args()

    kp = Keypair.from_bytes(bytes(json.loads(Path(a.key).read_text())))
    c = httpx.Client(base_url=a.verifier, timeout=120)
    print("config:", c.get("/config").json())
    token = sign_in(c, kp, a.domain)
    c.headers["Authorization"] = f"Bearer {token}"
    print("signed in as", kp.pubkey())

    ms = c.get("/missions", params={"lat": 48.5831, "lon": 7.7451, "wallet": str(kp.pubkey())}).json()
    print(f"{len(ms)} missions; nearest {ms[0]['title']!r} at {ms[0]['distanceM']:.0f} m")
    open_ms = [x for x in ms if not x["completedByMe"] and not x["requiresSeeker"]]
    m = next(x for x in open_ms if a.title.lower() in x["title"].lower()) if a.title else open_ms[a.mission_index]
    print("target:", m["title"], m["address"])

    r = c.post(f"/missions/{m['address']}/reserve")
    print("reserve:", r.status_code, r.json())

    photo = Path(a.photo).read_bytes() if a.photo else synthetic_photo(m["title"])
    t0 = time.time()
    r = c.post(
        f"/missions/{m['address']}/submit",
        data={"lat": m["lat"] + 0.0002, "lon": m["lon"], "accuracy": 8.0, "timestamp": int(time.time()), "mock_location": "false", "device": "e2e"},
        files={"photo": ("proof.jpg", photo, "image/jpeg")},
    )
    print(f"submit: {r.status_code} in {time.time() - t0:.1f}s")
    print(json.dumps(r.json(), indent=2))

    p = c.get(f"/workers/{kp.pubkey()}").json()
    print("profile:", p)
    print("completions:", c.get(f"/workers/{kp.pubkey()}/completions").json())
    print("leaderboard:", c.get("/leaderboard").json())
    print("creator missions:", [(x["mission"]["title"], x["mission"]["filled"], x["yes"], x["no"]) for x in c.get(f"/creators/{m['creator']}/missions").json()])


if __name__ == "__main__":
    main()
