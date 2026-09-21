"""Devnet operations for Legwork: initialise the program, mint test SKR, seed demo missions.

Usage (from the repo root, with the verifier venv):
  python scripts/devnet.py status
  python scripts/devnet.py init          # one-time: Config account + SKR vault (authority pays)
  python scripts/devnet.py mint skr      # create the devnet test SKR mint (6 decimals); `mint usdc` on localnet
  python scripts/devnet.py fund skr <wallet> <amount>
  python scripts/devnet.py seed --lat 48.8590 --lon 2.3480   # demo missions around a point
  python scripts/devnet.py close-all     # creator closes its open missions and takes the escrow back

Keys are read from ../keys (LEGWORK_KEYS overrides): authority.json, verifier.json, demo-creator.json.
"""

from __future__ import annotations

import argparse
import json
import os
import struct
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "verifier"))

from solders.instruction import AccountMeta, Instruction  # noqa: E402
from solders.keypair import Keypair  # noqa: E402
from solders.pubkey import Pubkey  # noqa: E402

from chain import Board, Rpc  # noqa: E402
from program import ASSOCIATED_TOKEN_PROGRAM, SYSTEM_PROGRAM, TOKEN_PROGRAM, Builder, CreateMissionArgs, Pdas, ata  # noqa: E402

KEYS = Path(os.environ.get("LEGWORK_KEYS", ROOT.parent / "keys"))
STATE = KEYS / (os.environ.get("LEGWORK_STATE") or "devnet-state.json")
RPC_URL = os.environ.get("RPC_URL", "https://api.devnet.solana.com")
PROGRAM_ID = Pubkey.from_string(os.environ.get("PROGRAM_ID", "86V9Vw6jPnoy4R6feJK6atwMK3x1oUootvvbqb8iZHD6"))
def usdc_mint() -> Pubkey:
    """Circle's devnet USDC by default; a local test mint when `mint usdc` was run (localnet)."""
    return Pubkey.from_string(os.environ.get("USDC_MINT") or state().get("usdc_mint") or "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU")
RENT_EXEMPT_MINT = 1_461_600  # lamports for an 82-byte mint account


def load_key(name: str) -> Keypair:
    return Keypair.from_bytes(bytes(json.loads((KEYS / f"{name}.json").read_text())))


def state() -> dict:
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save_state(d: dict):
    STATE.write_text(json.dumps(d, indent=2))


def create_ata_ix(payer: Pubkey, owner: Pubkey, mint: Pubkey) -> Instruction:
    return Instruction(ASSOCIATED_TOKEN_PROGRAM, bytes([1]), [
        AccountMeta(payer, True, True), AccountMeta(ata(owner, mint), False, True), AccountMeta(owner, False, False),
        AccountMeta(mint, False, False), AccountMeta(SYSTEM_PROGRAM, False, False), AccountMeta(TOKEN_PROGRAM, False, False),
    ])


def mint_to_ix(mint: Pubkey, dest: Pubkey, authority: Pubkey, amount: int) -> Instruction:
    return Instruction(TOKEN_PROGRAM, bytes([7]) + struct.pack("<Q", amount), [
        AccountMeta(mint, False, True), AccountMeta(dest, False, True), AccountMeta(authority, True, False),
    ])


def cmd_status(rpc: Rpc):
    for name in ("authority", "verifier", "demo-creator", "demo-worker"):
        kp = load_key(name)
        lamports = rpc.call("getBalance", [str(kp.pubkey())])["value"]
        print(f"{name:13} {kp.pubkey()}  {lamports / 1e9:.3f} SOL")
    board = Board(rpc, PROGRAM_ID)
    cfg = board.config()
    print("config:", "initialised" if cfg else "NOT initialised")
    if cfg:
        print(f"  verifier {cfg.verifier}\n  usdc {cfg.usdc_mint}\n  skr {cfg.skr_mint}\n  fee {cfg.fee_bps} bps, missions {cfg.missions_created}")
        for m in board.missions():
            print(f"  #{m.id} {m.title!r} {m.reward / 1e6} USDC {m.filled}/{m.slots} status={m.status} at {m.lat:.5f},{m.lon:.5f}")


def cmd_mint(rpc: Rpc, token: str):
    st = state()
    key = f"{token}_mint"
    if st.get(key):
        print(f"{token} mint exists:", st[key]); return
    authority = load_key("authority")
    mint = Keypair()
    create = Instruction(SYSTEM_PROGRAM, struct.pack("<IQQ", 0, RENT_EXEMPT_MINT, 82) + bytes(TOKEN_PROGRAM), [
        AccountMeta(authority.pubkey(), True, True), AccountMeta(mint.pubkey(), True, True),
    ])
    init = Instruction(TOKEN_PROGRAM, bytes([20, 6]) + bytes(authority.pubkey()) + bytes([0]), [AccountMeta(mint.pubkey(), False, True)])
    sig = rpc.send_and_confirm(authority, [create, init], [mint])
    st[key] = str(mint.pubkey())
    save_state(st)
    print(f"{token} test mint", mint.pubkey(), sig)


def cmd_fund(rpc: Rpc, token: str, wallet: str, amount: float):
    st = state()
    mint = Pubkey.from_string(st[f"{token}_mint"])
    authority = load_key("authority")
    owner = Pubkey.from_string(wallet)
    ixs = [create_ata_ix(authority.pubkey(), owner, mint), mint_to_ix(mint, ata(owner, mint), authority.pubkey(), int(amount * 1e6))]
    print("funded", wallet, amount, token.upper(), rpc.send_and_confirm(authority, ixs))


def cmd_init(rpc: Rpc, fee_bps: int, threshold_skr: float):
    st = state()
    authority = load_key("authority")
    verifier = load_key("verifier")
    skr_mint = Pubkey.from_string(st["skr_mint"])
    usdc = usdc_mint()
    b = Builder(PROGRAM_ID, usdc, skr_mint)
    board = Board(rpc, PROGRAM_ID)
    if board.config():
        print("already initialised"); return
    treasury = ata(authority.pubkey(), usdc)
    ixs = [
        create_ata_ix(authority.pubkey(), authority.pubkey(), usdc),
        b.initialize(authority.pubkey(), verifier.pubkey(), treasury, fee_bps, int(threshold_skr * 1e6)),
    ]
    print("initialised", rpc.send_and_confirm(authority, ixs))


DEMO = [
    # (title, instructions, question, category, reward USDC, slots, radius, dlat, dlon)
    ("Verify USDC acceptance at the café", "Photograph the payment options sign or card terminal at the counter so the accepted payment methods are readable.", "Does this café accept USDC or crypto?", 5, 4.0, 5, 75, 0.0018, 0.0011),
    ("Confirm the CLOCK IN poster is up", "Photograph the CLOCK IN hackathon poster where it hangs, with enough of the wall to show where it is.", "Is the poster still displayed and intact?", 1, 2.0, 10, 60, -0.0012, 0.0026),
    ("Check the EV charger", "Photograph the charging station's screen and connector so the status light or message is visible.", "Is the charger operational right now?", 2, 5.0, 3, 80, 0.0031, -0.0019),
    ("Shelf check: sparkling water", "Photograph the sparkling water shelf including the price labels.", "Is sparkling water in stock?", 0, 3.0, 8, 60, -0.0024, -0.0014),
    ("Verify pharmacy opening hours", "Photograph the opening hours sign on the door or window so the hours are readable.", "Is the pharmacy open right now?", 5, 2.5, 6, 50, 0.0009, -0.0033),
    ("Seeker meetup check-in", "Photograph the meetup sign or banner at the venue entrance.", "Is the venue open and the event visible?", 7, 8.0, 20, 120, 0.0044, 0.0037),
]


def cmd_seed(rpc: Rpc, lat: float, lon: float, days: int, seeker_only_last: bool):
    board = Board(rpc, PROGRAM_ID)
    cfg = board.config()
    creator = load_key("demo-creator")
    b = Builder(PROGRAM_ID, cfg.usdc_mint, cfg.skr_mint)
    bal = rpc.token_balance(creator.pubkey(), cfg.usdc_mint)
    need = sum(r * s for _, _, _, _, r, s, _, _, _ in DEMO) * 1e6 * (1 + cfg.fee_bps / 10_000)
    print(f"creator USDC balance {bal / 1e6:.2f}, needed {need / 1e6:.2f}")
    next_id = cfg.missions_created
    for i, (title, instr, q, cat, reward, slots, radius, dlat, dlon) in enumerate(DEMO):
        if bal < (reward * slots * 1e6) * (1 + cfg.fee_bps / 10_000):
            print("skipping (not enough USDC):", title); continue
        args = CreateMissionArgs(
            reward=int(reward * 1e6), slots=slots, lat_e6=round((lat + dlat) * 1e6), lon_e6=round((lon + dlon) * 1e6),
            radius_m=radius, deadline=int(time.time()) + days * 86_400, requires_seeker=(seeker_only_last and i == len(DEMO) - 1),
            min_score=0, category=cat, proof_kind=0, title=title, instructions=instr, question=q,
        )
        ix = b.create_mission(creator.pubkey(), next_id, cfg.treasury, args)
        sig = rpc.send_and_confirm(creator, [ix])
        print(f"mission #{next_id} {title!r} -> {sig}")
        next_id += 1
        bal -= reward * slots * 1e6 * (1 + cfg.fee_bps / 10_000)
        board.invalidate()
        cfg = board.config()


def cmd_close_all(rpc: Rpc):
    board = Board(rpc, PROGRAM_ID)
    cfg = board.config()
    creator = load_key("demo-creator")
    b = Builder(PROGRAM_ID, cfg.usdc_mint, cfg.skr_mint)
    for m in board.missions():
        if m.creator == creator.pubkey() and m.status != 2:
            print("closing", m.id, rpc.send_and_confirm(creator, [b.close_mission(creator.pubkey(), m)]))


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status")
    p = sub.add_parser("mint"); p.add_argument("token", choices=["skr", "usdc"])
    p = sub.add_parser("fund"); p.add_argument("token", choices=["skr", "usdc"]); p.add_argument("wallet"); p.add_argument("amount", type=float)
    p = sub.add_parser("airdrop"); p.add_argument("wallet"); p.add_argument("sol", type=float)
    p = sub.add_parser("init"); p.add_argument("--fee-bps", type=int, default=1000); p.add_argument("--threshold-skr", type=float, default=500)
    p = sub.add_parser("seed"); p.add_argument("--lat", type=float, required=True); p.add_argument("--lon", type=float, required=True)
    p.add_argument("--days", type=int, default=45); p.add_argument("--seeker-only-last", action="store_true")
    sub.add_parser("close-all")
    a = ap.parse_args()
    rpc = Rpc(RPC_URL)
    if a.cmd == "status": cmd_status(rpc)
    elif a.cmd == "mint": cmd_mint(rpc, a.token)
    elif a.cmd == "fund": cmd_fund(rpc, a.token, a.wallet, a.amount)
    elif a.cmd == "airdrop": print(rpc.call("requestAirdrop", [a.wallet, int(a.sol * 1e9)]))
    elif a.cmd == "init": cmd_init(rpc, a.fee_bps, a.threshold_skr)
    elif a.cmd == "seed": cmd_seed(rpc, a.lat, a.lon, a.days, a.seeker_only_last)
    elif a.cmd == "close-all": cmd_close_all(rpc)


if __name__ == "__main__":
    main()
