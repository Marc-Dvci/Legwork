"""Legwork verifier: reads the mission board from Solana, verifies proofs and signs payouts.

The service holds no database. Missions, reservations, completions, reputation and stake are
accounts of the Legwork program; the verifier keeps only a short RPC cache and a rolling
window of perceptual hashes for duplicate detection.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Optional

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from solders.pubkey import Pubkey

import auth
from chain import Board, Rpc, RpcError
from program import Builder, CreateMissionArgs, Pdas, WorkerProfile, ata
from settings import settings
from verify import haversine_m, verify

log = logging.getLogger("legwork")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

app = FastAPI(title="Legwork verifier", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

PROGRAM_ID = Pubkey.from_string(settings.program_id)
rpc = Rpc(settings.rpc_url)
sgt_rpc = Rpc(settings.sgt_rpc_url)
board = Board(rpc, PROGRAM_ID)
pdas = Pdas(PROGRAM_ID)
_recent_dhashes: list[int] = []
_lock = threading.Lock()


def builder() -> Builder:
    cfg = board.config()
    if cfg is None:
        raise HTTPException(503, "Program is not initialised on this cluster")
    return Builder(PROGRAM_ID, cfg.usdc_mint, cfg.skr_mint)


def current_wallet(authorization: Optional[str] = Header(None)) -> Pubkey:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "Sign in first")
    try:
        return Pubkey.from_string(auth.read_token(settings.jwt_secret, authorization[7:]))
    except auth.AuthError as e:
        raise HTTPException(401, str(e))


def creator_meta(pubkey: Pubkey) -> tuple[str | None, bool]:
    c = settings.creators.get(str(pubkey))
    if not c:
        return None, False
    return c.get("name"), bool(c.get("verified"))


# ----------------------------------------------------------------------------- config and auth

@app.get("/")
def root():
    return {"service": "legwork-verifier", "cluster": settings.cluster, "program": settings.program_id}


@app.get("/config")
def config():
    cfg = board.config()
    return {
        "cluster": settings.cluster,
        "rpcUrl": settings.public_rpc_url,
        "programId": settings.program_id,
        "usdcMint": str(cfg.usdc_mint) if cfg else settings.usdc_mint,
        "skrMint": str(cfg.skr_mint) if cfg else settings.skr_mint,
        "explorerBase": settings.explorer_base,
        "feeBps": cfg.fee_bps if cfg else 1000,
        "creatorStakeThreshold": cfg.creator_stake_threshold if cfg else 0,
        "verifierWallet": str(settings.verifier.pubkey()) if settings.verifier else "",
        "mapStyleUrl": settings.map_style_url,
    }


class SiwsIn(BaseModel):
    wallet: str
    message: str
    signature: str


@app.post("/auth/siws")
def siws(body: SiwsIn):
    try:
        wallet = auth.verify_siws(body.message, body.signature, body.wallet, settings.identity_domain)
    except auth.AuthError as e:
        raise HTTPException(401, str(e))
    except Exception as e:
        raise HTTPException(400, f"Could not read sign-in: {e}")
    return {"token": auth.issue_token(settings.jwt_secret, wallet), "wallet": wallet}


# ----------------------------------------------------------------------------- missions

@app.get("/missions")
def missions(lat: float | None = None, lon: float | None = None, radius_km: float = 25.0, wallet: str | None = None):
    now = time.time()
    done: set[Pubkey] = set()
    if wallet:
        try:
            done = {c.mission for c in board.completions_for_worker(Pubkey.from_string(wallet))}
        except ValueError:
            pass
    out = []
    for m in board.missions():
        if m.status == 2:
            continue
        d = haversine_m(lat, lon, m.lat, m.lon) if lat is not None and lon is not None else None
        if m.deadline < now - 86_400 and m.address not in done:
            continue
        name, verified = creator_meta(m.creator)
        out.append(m.to_json(d, name, verified, m.address in done))
    out.sort(key=lambda x: (x["distanceM"] is None, x["distanceM"] or 0))
    nearby = [x for x in out if x["distanceM"] is None or x["distanceM"] <= radius_km * 1000]
    # A new area is never an empty board: with nothing in range, show the nearest open missions.
    return nearby or out[:20]


@app.get("/missions/{address}")
def mission(address: str, lat: float | None = None, lon: float | None = None):
    m = board.mission(Pubkey.from_string(address))
    if not m:
        raise HTTPException(404, "Mission not found")
    d = haversine_m(lat, lon, m.lat, m.lon) if lat is not None and lon is not None else None
    name, verified = creator_meta(m.creator)
    return m.to_json(d, name, verified)


@app.post("/missions/{address}/reserve")
def reserve(address: str, worker: Pubkey = Depends(current_wallet)):
    m = board.mission(Pubkey.from_string(address))
    if not m:
        raise HTTPException(404, "Mission not found")
    if m.status != 0 or m.filled >= m.slots:
        raise HTTPException(409, "This mission has no slots left")
    if m.deadline < time.time():
        raise HTTPException(409, "This mission has expired")
    if board.completions_for_mission(m.address) and any(c.worker == worker for c in board.completions_for_mission(m.address)):
        raise HTTPException(409, "You already completed this mission")
    existing = board.reservation(m.address, worker)
    if existing and existing.expires_at > time.time() + 60:
        return {"ok": True, "expiresAt": existing.expires_at, "signature": None}
    verifier = settings.require_verifier()
    try:
        sig = rpc.send_and_confirm(verifier, [builder().reserve(verifier.pubkey(), m.address, worker)])
    except RpcError as e:
        raise HTTPException(502, f"Reservation failed on chain: {e}")
    res = board.reservation(m.address, worker)
    return {"ok": True, "expiresAt": res.expires_at if res else int(time.time()) + 45 * 60, "signature": sig}


@app.post("/missions/{address}/submit")
async def submit(
    address: str,
    lat: float = Form(...),
    lon: float = Form(...),
    accuracy: float = Form(...),
    timestamp: int = Form(...),
    mock_location: bool = Form(False),
    device: str = Form(""),
    photo: UploadFile = File(...),
    worker: Pubkey = Depends(current_wallet),
):
    m = board.mission(Pubkey.from_string(address))
    if not m:
        raise HTTPException(404, "Mission not found")
    if m.status != 0 or m.filled >= m.slots or m.deadline < time.time():
        raise HTTPException(409, "This mission is no longer open")
    res = board.reservation(m.address, worker)
    if res is None:
        raise HTTPException(409, "Start the mission first so a slot is reserved for you")
    if res.expires_at < time.time():
        raise HTTPException(409, "Your reservation expired. Start the mission again.")
    profile = board.worker(worker)
    if m.requires_seeker and not (profile and profile.seeker_verified):
        raise HTTPException(403, "This mission is for verified Seekers")
    if m.min_score > (profile.score if profile else 0):
        raise HTTPException(403, f"This mission needs a Legwork score of {m.min_score}")

    data = await photo.read()
    known = {c.proof_hash for c in board.completions()}
    with _lock:
        verdict = verify(m, data, lat, lon, accuracy, timestamp, mock_location, known, _recent_dhashes)
    log.info("submit mission=%s worker=%s device=%s approved=%s conf=%s", m.id, worker, device, verdict.approved, verdict.confidence)

    verifier = settings.require_verifier()
    b = builder()
    out = verdict.to_json()
    if verdict.approved:
        try:
            sig = rpc.send_and_confirm(verifier, [b.approve_completion(verifier.pubkey(), m, worker, verdict.proof_hash, verdict.confidence, verdict.answer)])
        except RpcError as e:
            log.exception("approve failed")
            raise HTTPException(502, f"Payout failed on chain: {e}")
        board.invalidate()
        out.update({"signature": sig, "amount": m.reward})
    elif not verdict.judge_unavailable:
        try:
            rpc.send_and_confirm(verifier, [b.record_rejection(verifier.pubkey(), worker)])
        except RpcError:
            log.warning("could not record rejection for %s", worker)
        board.invalidate()
    return out


# ----------------------------------------------------------------------------- workers

@app.get("/workers/{wallet}")
def worker_profile(wallet: str):
    p = board.worker(Pubkey.from_string(wallet))
    return p.to_json() if p else WorkerProfile.empty(wallet)


@app.get("/workers/{wallet}/completions")
def worker_completions(wallet: str):
    titles = {m.address: m for m in board.missions()}
    out = []
    for c in board.completions_for_worker(Pubkey.from_string(wallet)):
        m = titles.get(c.mission)
        out.append(c.to_json(m.title if m else None, m.category if m else None))
    return sorted(out, key=lambda x: -x["approvedAt"])


@app.post("/workers/verify-seeker")
def verify_seeker(worker: Pubkey = Depends(current_wallet)):
    mint = sgt_rpc.find_seeker_genesis_token(worker)
    if not mint:
        return {"verified": False, "reason": "No Seeker Genesis Token found in this wallet"}
    verifier = settings.require_verifier()
    try:
        sig = rpc.send_and_confirm(verifier, [builder().verify_seeker(verifier.pubkey(), worker, Pubkey.from_string(mint))])
    except RpcError as e:
        return {"verified": False, "reason": f"Could not record on chain: {e}"}
    board.invalidate()
    return {"verified": True, "sgtMint": mint, "signature": sig}


@app.get("/leaderboard")
def leaderboard():
    rows = sorted(board.workers(), key=lambda w: (-w.approved, -w.score))[:50]
    return [{"wallet": str(w.wallet), "approved": w.approved, "score": w.score, "streak": w.streak, "seekerVerified": w.seeker_verified} for w in rows]


# ----------------------------------------------------------------------------- creators

@app.get("/creators/{wallet}")
def creator_profile(wallet: str):
    c = board.creator(Pubkey.from_string(wallet))
    return c.to_json() if c else {"wallet": wallet, "skrStaked": 0, "missions": 0, "totalFunded": 0, "totalPaid": 0, "exists": False}


@app.get("/creators/{wallet}/missions")
def creator_missions(wallet: str):
    w = Pubkey.from_string(wallet)
    out = []
    for m in sorted(board.missions(), key=lambda x: -x.id):
        if m.creator != w:
            continue
        comps = board.completions_for_mission(m.address)
        name, verified = creator_meta(m.creator)
        out.append({
            "mission": m.to_json(None, name, verified),
            "completions": [c.to_json(m.title, m.category) for c in sorted(comps, key=lambda c: -c.approved_at)],
            "yes": sum(1 for c in comps if c.answer == 1),
            "no": sum(1 for c in comps if c.answer == 2),
            "unknown": sum(1 for c in comps if c.answer == 0),
        })
    return out


# ----------------------------------------------------------------------------- transactions the wallet signs

class CreateMissionIn(BaseModel):
    creator: str
    title: str
    instructions: str
    question: str = ""
    category: int = 0
    proofKind: int = 0
    reward: int
    slots: int
    lat: float
    lon: float
    radiusM: int
    deadline: int
    requiresSeeker: bool = False
    minScore: int = 0


@app.post("/tx/create-mission")
def tx_create_mission(body: CreateMissionIn):
    cfg = board.config()
    if cfg is None:
        raise HTTPException(503, "Program is not initialised")
    if not (0 < len(body.title) <= 48 and 0 < len(body.instructions) <= 200 and len(body.question) <= 80):
        raise HTTPException(400, "Text too long")
    if body.reward <= 0 or body.slots <= 0 or not (10 <= body.radiusM <= 2000):
        raise HTTPException(400, "Invalid reward, slots or radius")
    creator = Pubkey.from_string(body.creator)
    b = Builder(PROGRAM_ID, cfg.usdc_mint, cfg.skr_mint)
    args = CreateMissionArgs(
        reward=body.reward, slots=body.slots, lat_e6=round(body.lat * 1e6), lon_e6=round(body.lon * 1e6),
        radius_m=body.radiusM, deadline=body.deadline, requires_seeker=body.requiresSeeker, min_score=body.minScore,
        category=body.category, proof_kind=body.proofKind, title=body.title, instructions=body.instructions, question=body.question,
    )
    ix = b.create_mission(creator, cfg.missions_created, cfg.treasury, args)
    board.invalidate()
    return {
        "transaction": rpc.unsigned_tx_b64(creator, [ix]),
        "missionAddress": str(pdas.mission(cfg.missions_created)),
        "summary": f"Fund {body.slots} × {body.reward / 1e6:.2f} USDC into escrow",
    }


class StakeIn(BaseModel):
    wallet: str
    amount: int
    role: str = "worker"
    unstake: bool = False


@app.post("/tx/stake")
def tx_stake(body: StakeIn):
    if body.amount <= 0:
        raise HTTPException(400, "Amount must be positive")
    w = Pubkey.from_string(body.wallet)
    b = builder()
    ix = b.creator_stake_skr(w, body.amount, body.unstake) if body.role == "creator" else b.stake_skr(w, body.amount, body.unstake)
    return {"transaction": rpc.unsigned_tx_b64(w, [ix]), "summary": f"{'Unstake' if body.unstake else 'Stake'} {body.amount / 1e6:.2f} SKR"}


@app.get("/tx/{signature}/confirm")
def tx_confirm(signature: str):
    st = rpc.status(signature)
    return {"confirmed": st in ("confirmed", "finalized"), "failed": st == "failed"}


@app.get("/healthz")
def healthz():
    return {"ok": True}
