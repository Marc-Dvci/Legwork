import base64
import io
import json
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("JWT_SECRET", "test-secret")

import base58
import pytest
from PIL import Image
from solders.keypair import Keypair
from solders.pubkey import Pubkey

import auth
import program
import verify
from program import ACC_DISC, Builder, CreateMissionArgs, Mission, Pdas, WorkerProfile


def make_mission(**over) -> Mission:
    base = dict(
        address=Pubkey.new_unique(), id=3, creator=Pubkey.new_unique(), vault=Pubkey.new_unique(), reward=4_000_000,
        slots=5, filled=1, lat_e6=48_583_000, lon_e6=7_745_000, radius_m=75, deadline=int(time.time()) + 86_400,
        requires_seeker=False, min_score=0, category=5, proof_kind=0, status=0, created_at=0, fee_paid=0,
        title="Verify USDC acceptance", instructions="Photograph the payment sign", question="Accepts USDC?", bump=254,
    )
    base.update(over)
    return Mission(**base)


def test_idl_discriminators_loaded():
    assert len(program.IX_DISC["create_mission"]) == 8
    assert ACC_DISC["Mission"] == bytes([170, 56, 116, 75, 24, 11, 109, 12])


def test_create_mission_args_roundtrip_through_decoder():
    args = CreateMissionArgs(4_000_000, 5, 48_583_000, 7_745_000, 75, 1_800_000_000, True, 20, 5, 0, "T", "Do this", "Q?")
    encoded = args.encode()
    # Mission account = disc + id + creator + vault + (args fields in the same order as the struct) ...
    # Build a synthetic Mission account body to check the decoder layout matches the program struct.
    body = (
        (3).to_bytes(8, "little") + bytes(Pubkey.new_unique()) + bytes(Pubkey.new_unique())
        + encoded[:10] + (1).to_bytes(2, "little")  # reward, slots, then the account-only `filled`
        + encoded[10:33]  # lat..proof_kind
        + b"\x00" + (0).to_bytes(8, "little") + (0).to_bytes(8, "little")  # status, created_at, fee_paid
        + encoded[33:]  # strings
        + b"\xfe"
    )
    m = Mission.decode(Pubkey.new_unique(), ACC_DISC["Mission"] + body)
    assert (m.reward, m.slots, m.lat_e6, m.lon_e6, m.radius_m, m.deadline) == (4_000_000, 5, 48_583_000, 7_745_000, 75, 1_800_000_000)
    assert m.requires_seeker is True and m.min_score == 20 and m.category == 5
    assert (m.title, m.instructions, m.question, m.bump) == ("T", "Do this", "Q?", 254)


def test_pdas_are_deterministic_and_distinct():
    p = Pdas(Pubkey.from_string(program.IDL["address"]))
    w = Pubkey.new_unique()
    assert p.config() == p.config()
    assert p.mission(0) != p.mission(1)
    assert p.worker(w) != p.creator(w)


def test_builder_account_order_matches_idl():
    pid = Pubkey.from_string(program.IDL["address"])
    b = Builder(pid, Pubkey.new_unique(), Pubkey.new_unique())
    m = make_mission()
    ix = b.approve_completion(Pubkey.new_unique(), m, Pubkey.new_unique(), bytes(32), 90, 1)
    idl_accounts = next(i for i in program.IDL["instructions"] if i["name"] == "approve_completion")["accounts"]
    assert len(ix.accounts) == len(idl_accounts)
    for meta, spec in zip(ix.accounts, idl_accounts):
        assert meta.is_signer == bool(spec.get("signer")), spec["name"]
        assert meta.is_writable == bool(spec.get("writable")), spec["name"]
    assert ix.data[:8] == program.IX_DISC["approve_completion"]
    assert len(ix.data) == 8 + 32 + 2


def test_worker_profile_decode():
    w = Pubkey.new_unique()
    body = bytes(w) + (7).to_bytes(4, "little") + (1).to_bytes(4, "little") + (3).to_bytes(2, "little") + (20000).to_bytes(4, "little") \
        + (100_000_000).to_bytes(8, "little") + b"\x01" + bytes(Pubkey.new_unique()) + (28_000_000).to_bytes(8, "little") \
        + (79).to_bytes(4, "little") + (0b100001).to_bytes(2, "little") + b"\xff"
    p = WorkerProfile.decode(ACC_DISC["WorkerProfile"] + body)
    assert p.wallet == w and p.approved == 7 and p.rejected == 1 and p.streak == 3 and p.skr_staked == 100_000_000
    assert p.seeker_verified and p.total_earned == 28_000_000 and p.score == 79 and p.categories == 33


def test_location_policy():
    m = make_mission()
    now = time.time()
    ok = verify.location_checks(m, 48.5831, 7.7451, 12.0, int(now), False, now)
    assert all(c.passed for c in ok)
    far = verify.location_checks(m, 48.59, 7.75, 12.0, int(now), False, now)
    assert not far[0].passed
    stale = verify.location_checks(m, 48.5831, 7.7451, 12.0, int(now) - 3600, False, now)
    assert not stale[2].passed
    mocked = verify.location_checks(m, 48.5831, 7.7451, 12.0, int(now), True, now)
    assert not mocked[3].passed


def _jpeg(color, size=(640, 480)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, "JPEG")
    return buf.getvalue()


def test_verify_rejects_bad_image_and_exact_duplicate(monkeypatch):
    monkeypatch.setattr(verify.settings, "skip_vision", True)
    m = make_mission()
    now = int(time.time())
    v = verify.verify(m, b"not an image", 48.5831, 7.7451, 10, now, False, set(), [])
    assert not v.approved and "readable" in v.reason
    photo = _jpeg((200, 30, 30))
    v1 = verify.verify(m, photo, 48.5831, 7.7451, 10, now, False, set(), [])
    assert v1.approved and len(v1.proof_hash) == 32
    v2 = verify.verify(m, photo, 48.5831, 7.7451, 10, now, False, {v1.proof_hash}, [])
    assert not v2.approved and "already submitted" in v2.reason


def test_dhash_near_duplicate():
    a = Image.new("RGB", (640, 480), (10, 200, 10))
    b = a.copy()
    b.putpixel((5, 5), (0, 0, 0))
    assert verify.hamming(verify.dhash(a), verify.dhash(b)) <= 6


def test_siws_roundtrip():
    kp = Keypair()
    wallet = str(kp.pubkey())
    issued = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    text = (
        f"legwork.app wants you to sign in with your Solana account:\n{wallet}\n\n"
        "Sign in to Legwork to find paid missions near you.\n\n"
        f"URI: https://legwork.app\nVersion: 1\nNonce: abcdefgh1234\nIssued At: {issued}"
    )
    raw = text.encode()
    sig = kp.sign_message(raw)
    assert auth.verify_siws(base64.b64encode(raw).decode(), base58.b58encode(bytes(sig)).decode(), wallet, "legwork.app") == wallet
    with pytest.raises(auth.AuthError):
        auth.verify_siws(base64.b64encode(raw).decode(), base58.b58encode(bytes(sig)).decode(), str(Keypair().pubkey()), "legwork.app")
    with pytest.raises(auth.AuthError):
        auth.verify_siws(base64.b64encode(raw).decode(), base58.b58encode(bytes(sig)).decode(), wallet, "evil.app")
    token = auth.issue_token("s", wallet)
    assert auth.read_token("s", token) == wallet
    with pytest.raises(auth.AuthError):
        auth.read_token("other", token)
