"""Proof verification: location policy, image sanity, duplicate detection and the vision judge."""

from __future__ import annotations

import hashlib
import io
import json
import math
import time
from dataclasses import dataclass, field

from PIL import Image, ImageOps

from program import Mission
from settings import settings


@dataclass
class Check:
    name: str
    passed: bool
    detail: str = ""

    def to_json(self) -> dict:
        return {"name": self.name, "pass": self.passed, "detail": self.detail}


@dataclass
class Verdict:
    approved: bool
    checks: list[Check] = field(default_factory=list)
    confidence: int = 0
    answer: int = 0  # 0 unknown, 1 yes, 2 no
    answer_text: str = ""
    reason: str = ""
    proof_hash: bytes = b""

    def to_json(self) -> dict:
        return {
            "status": "approved" if self.approved else "rejected",
            "confidence": self.confidence,
            "checks": [c.to_json() for c in self.checks],
            "reason": self.reason or None,
            "answer": self.answer_text or None,
            "proofHash": self.proof_hash.hex() if self.proof_hash else None,
        }


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def location_checks(m: Mission, lat: float, lon: float, accuracy: float, timestamp: int, mock: bool, now: float | None = None) -> list[Check]:
    now = now or time.time()
    d = haversine_m(lat, lon, m.lat, m.lon)
    slack = min(accuracy, 50.0) + 25.0
    checks = [
        Check("At the mission location", d <= m.radius_m + slack, f"{d:.0f} m from the pin, radius {m.radius_m} m"),
        Check("GPS accuracy", accuracy <= settings.max_accuracy_m, f"±{accuracy:.0f} m"),
        Check("Fresh capture", abs(now - timestamp) <= settings.max_clock_skew_s, "captured just now" if abs(now - timestamp) <= settings.max_clock_skew_s else "timestamp too far from server time"),
        Check("Real location provider", not mock, "mock location flag set by the device" if mock else "device reports a real fix"),
    ]
    return checks


def image_checks(photo: bytes) -> tuple[list[Check], Image.Image | None, bytes]:
    proof_hash = hashlib.sha256(photo).digest()
    if len(photo) > settings.max_photo_bytes:
        return [Check("Photo", False, "file too large")], None, proof_hash
    try:
        img = Image.open(io.BytesIO(photo))
        img.load()
        img = ImageOps.exif_transpose(img).convert("RGB")
    except Exception:
        return [Check("Photo", False, "not a readable image")], None, proof_hash
    w, h = img.size
    ok = w >= 320 and h >= 320
    return [Check("Photo", ok, f"{w}×{h}")], (img if ok else None), proof_hash


def dhash(img: Image.Image) -> int:
    """64-bit difference hash for near-duplicate detection."""
    g = img.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    px = list(g.getdata())
    bits = 0
    for row in range(8):
        for col in range(8):
            left = px[row * 9 + col]
            right = px[row * 9 + col + 1]
            bits = (bits << 1) | (1 if left > right else 0)
    return bits


def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


JUDGE_PROMPT = """You are the proof verifier for Legwork, a marketplace where people are paid to photograph real-world things.

Mission title: {title}
Mission instructions: {instructions}
Question the creator wants answered from the photo: {question}

Look at the photo and decide whether it is a live, first-hand photo that fulfils the instructions.
A reproduction is a photo whose whole frame is another photo or a display showing a picture (a re-photographed
screen, a printed photo, stock imagery, an obviously rendered or edited image). A live scene that happens to
contain a screen or sign as its subject (a card terminal, a charger display, a shop sign) is NOT a reproduction.
Reject reproductions and images unrelated to the instructions.

Answer ONLY with JSON matching this schema:
{{
  "relevant": boolean,              // the photo shows the kind of place or object the mission is about
  "fulfils_instructions": boolean,  // the specific thing asked for is visible and usable
  "screen_or_reproduction": boolean,// the whole photo is a re-photographed screen, print or generated image
  "answer": "yes" | "no" | "unknown", // answer to the creator's question from what is visible
  "confidence": integer 0-100,      // confidence that this is a valid completion
  "requirements": [ {{"name": string, "pass": boolean, "detail": string}} ],  // 2 to 4 concrete checks you made
  "summary": string                 // one sentence, plain language, addressed to the worker
}}"""


def _client():
    from google import genai
    if settings.gemini_api_key:
        return genai.Client(api_key=settings.gemini_api_key)
    if settings.vertex_project:
        return genai.Client(vertexai=True, project=settings.vertex_project, location=settings.vertex_location)
    raise RuntimeError("No GEMINI_API_KEY or VERTEX_PROJECT configured")


def vision_judge(m: Mission, img: Image.Image) -> dict:
    """Asks the vision model for a structured verdict. Raises on transport errors."""
    from google.genai import types
    buf = io.BytesIO()
    img2 = img.copy()
    img2.thumbnail((1024, 1024))
    img2.save(buf, format="JPEG", quality=85)
    prompt = JUDGE_PROMPT.format(title=m.title, instructions=m.instructions, question=m.question or "(none)")
    client = _client()
    resp = client.models.generate_content(
        model=settings.gemini_model,
        contents=[types.Part.from_bytes(data=buf.getvalue(), mime_type="image/jpeg"), prompt],
        config=types.GenerateContentConfig(response_mime_type="application/json", temperature=0.1),
    )
    text = resp.text or "{}"
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        return json.loads(text[start:end + 1])


def verify(m: Mission, photo: bytes, lat: float, lon: float, accuracy: float, timestamp: int, mock: bool,
           known_hashes: set[bytes], recent_dhashes: list[int]) -> Verdict:
    v = Verdict(approved=False)
    v.checks.extend(location_checks(m, lat, lon, accuracy, timestamp, mock))
    img_checks, img, proof_hash = image_checks(photo)
    v.checks.extend(img_checks)
    v.proof_hash = proof_hash
    if proof_hash in known_hashes:
        v.checks.append(Check("Original photo", False, "this exact photo was already submitted"))
    elif img is not None:
        h = dhash(img)
        dup = any(hamming(h, other) <= 6 for other in recent_dhashes)
        v.checks.append(Check("Original photo", not dup, "near-duplicate of a recent submission" if dup else "not seen before"))
        if not dup:
            recent_dhashes.append(h)
            del recent_dhashes[:-2000]
    if not all(c.passed for c in v.checks) or img is None:
        v.reason = next((c.detail for c in v.checks if not c.passed), "Checks failed")
        return v

    if settings.skip_vision:
        v.checks.append(Check("Photo matches the mission", True, "vision check skipped in test mode"))
        v.confidence, v.answer, v.answer_text, v.approved = 90, 0, "", True
        return v

    try:
        j = vision_judge(m, img)
    except Exception as e:  # transport or quota
        v.checks.append(Check("Photo matches the mission", False, f"verifier unavailable: {type(e).__name__}"))
        v.reason = "The vision verifier is temporarily unavailable. Try again in a moment."
        return v

    for req in (j.get("requirements") or [])[:4]:
        v.checks.append(Check(str(req.get("name", "Requirement"))[:60], bool(req.get("pass")), str(req.get("detail", ""))[:120]))
    live = not bool(j.get("screen_or_reproduction"))
    v.checks.append(Check("Live photo, not a screen", live, "" if live else "looks like a screen or a reproduction"))
    relevant = bool(j.get("relevant")) and bool(j.get("fulfils_instructions"))
    v.checks.append(Check("Photo matches the mission", relevant, str(j.get("summary", ""))[:160]))
    v.confidence = int(max(0, min(100, int(j.get("confidence", 0)))))
    ans = str(j.get("answer", "unknown")).lower()
    v.answer = 1 if ans == "yes" else 2 if ans == "no" else 0
    v.answer_text = ans if m.question else ""
    v.approved = live and relevant and v.confidence >= settings.min_confidence
    if not v.approved:
        v.reason = str(j.get("summary") or "The photo did not match the mission.")[:200]
    return v
