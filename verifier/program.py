"""Encoders and decoders for the Legwork Anchor program, driven by its IDL."""

from __future__ import annotations

import json
import struct
from dataclasses import dataclass
from pathlib import Path

from solders.instruction import AccountMeta, Instruction
from solders.pubkey import Pubkey

IDL = json.loads((Path(__file__).parent / "legwork_idl.json").read_text())

TOKEN_PROGRAM = Pubkey.from_string("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
TOKEN_2022_PROGRAM = Pubkey.from_string("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")
ASSOCIATED_TOKEN_PROGRAM = Pubkey.from_string("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
SYSTEM_PROGRAM = Pubkey.from_string("11111111111111111111111111111111")

IX_DISC = {ix["name"]: bytes(ix["discriminator"]) for ix in IDL["instructions"]}
ACC_DISC = {a["name"]: bytes(a["discriminator"]) for a in IDL["accounts"]}


# ----------------------------------------------------------------------------- PDAs

class Pdas:
    def __init__(self, program_id: Pubkey):
        self.program_id = program_id

    def config(self) -> Pubkey:
        return Pubkey.find_program_address([b"config"], self.program_id)[0]

    def mission(self, mission_id: int) -> Pubkey:
        return Pubkey.find_program_address([b"mission", mission_id.to_bytes(8, "little")], self.program_id)[0]

    def worker(self, wallet: Pubkey) -> Pubkey:
        return Pubkey.find_program_address([b"worker", bytes(wallet)], self.program_id)[0]

    def creator(self, wallet: Pubkey) -> Pubkey:
        return Pubkey.find_program_address([b"creator", bytes(wallet)], self.program_id)[0]

    def reservation(self, mission: Pubkey, worker: Pubkey) -> Pubkey:
        return Pubkey.find_program_address([b"reservation", bytes(mission), bytes(worker)], self.program_id)[0]

    def completion(self, mission: Pubkey, worker: Pubkey) -> Pubkey:
        return Pubkey.find_program_address([b"completion", bytes(mission), bytes(worker)], self.program_id)[0]

    def device(self, sgt_mint: Pubkey) -> Pubkey:
        return Pubkey.find_program_address([b"device", bytes(sgt_mint)], self.program_id)[0]


def ata(owner: Pubkey, mint: Pubkey, token_program: Pubkey = TOKEN_PROGRAM) -> Pubkey:
    return Pubkey.find_program_address([bytes(owner), bytes(token_program), bytes(mint)], ASSOCIATED_TOKEN_PROGRAM)[0]


# ----------------------------------------------------------------------------- borsh helpers

def _string(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack("<I", len(b)) + b


class Reader:
    def __init__(self, data: bytes, offset: int = 8):
        self.d = data
        self.o = offset

    def u8(self) -> int:
        v = self.d[self.o]; self.o += 1; return v

    def bool(self) -> bool:
        return self.u8() != 0

    def u16(self) -> int:
        v = struct.unpack_from("<H", self.d, self.o)[0]; self.o += 2; return v

    def u32(self) -> int:
        v = struct.unpack_from("<I", self.d, self.o)[0]; self.o += 4; return v

    def i32(self) -> int:
        v = struct.unpack_from("<i", self.d, self.o)[0]; self.o += 4; return v

    def u64(self) -> int:
        v = struct.unpack_from("<Q", self.d, self.o)[0]; self.o += 8; return v

    def i64(self) -> int:
        v = struct.unpack_from("<q", self.d, self.o)[0]; self.o += 8; return v

    def pubkey(self) -> Pubkey:
        v = Pubkey.from_bytes(self.d[self.o:self.o + 32]); self.o += 32; return v

    def bytes32(self) -> bytes:
        v = self.d[self.o:self.o + 32]; self.o += 32; return v

    def string(self) -> str:
        n = self.u32()
        v = self.d[self.o:self.o + n].decode("utf-8", "replace"); self.o += n; return v


# ----------------------------------------------------------------------------- accounts

@dataclass
class Config:
    authority: Pubkey
    verifier: Pubkey
    usdc_mint: Pubkey
    skr_mint: Pubkey
    treasury: Pubkey
    fee_bps: int
    creator_stake_threshold: int
    missions_created: int
    bump: int

    @classmethod
    def decode(cls, data: bytes) -> "Config":
        r = Reader(data)
        return cls(r.pubkey(), r.pubkey(), r.pubkey(), r.pubkey(), r.pubkey(), r.u16(), r.u64(), r.u64(), r.u8())


@dataclass
class Mission:
    address: Pubkey
    id: int
    creator: Pubkey
    vault: Pubkey
    reward: int
    slots: int
    filled: int
    lat_e6: int
    lon_e6: int
    radius_m: int
    deadline: int
    requires_seeker: bool
    min_score: int
    category: int
    proof_kind: int
    status: int
    created_at: int
    fee_paid: int
    title: str
    instructions: str
    question: str
    bump: int

    @classmethod
    def decode(cls, address: Pubkey, data: bytes) -> "Mission":
        r = Reader(data)
        return cls(
            address, r.u64(), r.pubkey(), r.pubkey(), r.u64(), r.u16(), r.u16(), r.i32(), r.i32(), r.u16(), r.i64(),
            r.bool(), r.u16(), r.u8(), r.u8(), r.u8(), r.i64(), r.u64(), r.string(), r.string(), r.string(), r.u8(),
        )

    @property
    def lat(self) -> float:
        return self.lat_e6 / 1e6

    @property
    def lon(self) -> float:
        return self.lon_e6 / 1e6

    def to_json(self, distance_m: float | None = None, creator_name: str | None = None,
                creator_verified: bool = False, completed_by_me: bool = False) -> dict:
        return {
            "address": str(self.address), "id": self.id, "creator": str(self.creator), "title": self.title,
            "instructions": self.instructions, "question": self.question, "category": self.category,
            "proofKind": self.proof_kind, "reward": self.reward, "slots": self.slots, "filled": self.filled,
            "lat": self.lat, "lon": self.lon, "radiusM": self.radius_m, "deadline": self.deadline,
            "requiresSeeker": self.requires_seeker, "minScore": self.min_score, "status": self.status,
            "createdAt": self.created_at, "distanceM": distance_m, "creatorName": creator_name,
            "creatorVerified": creator_verified, "completedByMe": completed_by_me,
        }


@dataclass
class WorkerProfile:
    wallet: Pubkey
    approved: int
    rejected: int
    streak: int
    last_day: int
    skr_staked: int
    seeker_verified: bool
    sgt_mint: Pubkey
    total_earned: int
    score: int
    categories: int
    bump: int

    @classmethod
    def decode(cls, data: bytes) -> "WorkerProfile":
        r = Reader(data)
        return cls(r.pubkey(), r.u32(), r.u32(), r.u16(), r.u32(), r.u64(), r.bool(), r.pubkey(), r.u64(), r.u32(), r.u16(), r.u8())

    def to_json(self) -> dict:
        return {
            "wallet": str(self.wallet), "approved": self.approved, "rejected": self.rejected, "streak": self.streak,
            "lastDay": self.last_day, "skrStaked": self.skr_staked, "seekerVerified": self.seeker_verified,
            "totalEarned": self.total_earned, "score": self.score, "categories": self.categories, "exists": True,
        }

    @staticmethod
    def empty(wallet: str) -> dict:
        return {"wallet": wallet, "approved": 0, "rejected": 0, "streak": 0, "lastDay": 0, "skrStaked": 0,
                "seekerVerified": False, "totalEarned": 0, "score": 0, "categories": 0, "exists": False}


@dataclass
class CreatorProfile:
    wallet: Pubkey
    skr_staked: int
    missions: int
    total_funded: int
    total_paid: int
    bump: int

    @classmethod
    def decode(cls, data: bytes) -> "CreatorProfile":
        r = Reader(data)
        return cls(r.pubkey(), r.u64(), r.u32(), r.u64(), r.u64(), r.u8())

    def to_json(self) -> dict:
        return {"wallet": str(self.wallet), "skrStaked": self.skr_staked, "missions": self.missions,
                "totalFunded": self.total_funded, "totalPaid": self.total_paid, "exists": True}


@dataclass
class Reservation:
    mission: Pubkey
    worker: Pubkey
    expires_at: int

    @classmethod
    def decode(cls, data: bytes) -> "Reservation":
        r = Reader(data)
        return cls(r.pubkey(), r.pubkey(), r.i64())


@dataclass
class Completion:
    address: Pubkey
    mission: Pubkey
    worker: Pubkey
    proof_hash: bytes
    confidence: int
    answer: int
    amount: int
    approved_at: int

    @classmethod
    def decode(cls, address: Pubkey, data: bytes) -> "Completion":
        r = Reader(data)
        return cls(address, r.pubkey(), r.pubkey(), r.bytes32(), r.u8(), r.u8(), r.u64(), r.i64())

    def to_json(self, title: str | None = None, category: int | None = None) -> dict:
        return {"address": str(self.address), "mission": str(self.mission), "worker": str(self.worker),
                "proofHash": self.proof_hash.hex(), "confidence": self.confidence, "answer": self.answer,
                "amount": self.amount, "approvedAt": self.approved_at, "missionTitle": title, "missionCategory": category}


# ----------------------------------------------------------------------------- instructions

@dataclass
class CreateMissionArgs:
    reward: int
    slots: int
    lat_e6: int
    lon_e6: int
    radius_m: int
    deadline: int
    requires_seeker: bool
    min_score: int
    category: int
    proof_kind: int
    title: str
    instructions: str
    question: str

    def encode(self) -> bytes:
        return (
            struct.pack("<QHiiHq?HBB", self.reward, self.slots, self.lat_e6, self.lon_e6, self.radius_m,
                        self.deadline, self.requires_seeker, self.min_score, self.category, self.proof_kind)
            + _string(self.title) + _string(self.instructions) + _string(self.question)
        )


class Builder:
    """Builds instructions with the account order the IDL fixes."""

    def __init__(self, program_id: Pubkey, usdc_mint: Pubkey, skr_mint: Pubkey,
                 usdc_token_program: Pubkey = TOKEN_PROGRAM, skr_token_program: Pubkey = TOKEN_PROGRAM):
        self.program_id = program_id
        self.pda = Pdas(program_id)
        self.usdc_mint = usdc_mint
        self.skr_mint = skr_mint
        self.usdc_tp = usdc_token_program
        self.skr_tp = skr_token_program

    def _ix(self, name: str, metas: list[AccountMeta], data: bytes = b"") -> Instruction:
        return Instruction(self.program_id, IX_DISC[name] + data, metas)

    def initialize(self, authority: Pubkey, verifier: Pubkey, treasury: Pubkey, fee_bps: int, creator_stake_threshold: int) -> Instruction:
        config = self.pda.config()
        metas = [
            AccountMeta(authority, True, True),
            AccountMeta(verifier, False, False),
            AccountMeta(config, False, True),
            AccountMeta(self.usdc_mint, False, False),
            AccountMeta(self.skr_mint, False, False),
            AccountMeta(treasury, False, False),
            AccountMeta(ata(config, self.skr_mint, self.skr_tp), False, True),
            AccountMeta(self.skr_tp, False, False),
            AccountMeta(ASSOCIATED_TOKEN_PROGRAM, False, False),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        return self._ix("initialize", metas, struct.pack("<HQ", fee_bps, creator_stake_threshold))

    def create_mission(self, creator: Pubkey, mission_id: int, treasury: Pubkey, args: CreateMissionArgs) -> Instruction:
        mission = self.pda.mission(mission_id)
        metas = [
            AccountMeta(creator, True, True),
            AccountMeta(self.pda.config(), False, True),
            AccountMeta(self.pda.creator(creator), False, True),
            AccountMeta(mission, False, True),
            AccountMeta(self.usdc_mint, False, False),
            AccountMeta(ata(mission, self.usdc_mint, self.usdc_tp), False, True),
            AccountMeta(ata(creator, self.usdc_mint, self.usdc_tp), False, True),
            AccountMeta(treasury, False, True),
            AccountMeta(self.usdc_tp, False, False),
            AccountMeta(ASSOCIATED_TOKEN_PROGRAM, False, False),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        return self._ix("create_mission", metas, args.encode())

    def reserve(self, verifier: Pubkey, mission: Pubkey, worker: Pubkey) -> Instruction:
        metas = [
            AccountMeta(verifier, True, True),
            AccountMeta(self.pda.config(), False, False),
            AccountMeta(mission, False, False),
            AccountMeta(worker, False, False),
            AccountMeta(self.pda.reservation(mission, worker), False, True),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        return self._ix("reserve", metas)

    def approve_completion(self, verifier: Pubkey, m: Mission, worker: Pubkey, proof_hash: bytes, confidence: int, answer: int) -> Instruction:
        metas = [
            AccountMeta(verifier, True, True),
            AccountMeta(self.pda.config(), False, False),
            AccountMeta(m.address, False, True),
            AccountMeta(m.vault, False, True),
            AccountMeta(self.pda.creator(m.creator), False, True),
            AccountMeta(worker, False, False),
            AccountMeta(self.pda.worker(worker), False, True),
            AccountMeta(self.pda.reservation(m.address, worker), False, True),
            AccountMeta(self.pda.completion(m.address, worker), False, True),
            AccountMeta(self.usdc_mint, False, False),
            AccountMeta(ata(worker, self.usdc_mint, self.usdc_tp), False, True),
            AccountMeta(self.usdc_tp, False, False),
            AccountMeta(ASSOCIATED_TOKEN_PROGRAM, False, False),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        assert len(proof_hash) == 32
        return self._ix("approve_completion", metas, proof_hash + struct.pack("<BB", confidence, answer))

    def record_rejection(self, verifier: Pubkey, worker: Pubkey) -> Instruction:
        metas = [
            AccountMeta(verifier, True, True),
            AccountMeta(self.pda.config(), False, False),
            AccountMeta(worker, False, False),
            AccountMeta(self.pda.worker(worker), False, True),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        return self._ix("record_rejection", metas)

    def verify_seeker(self, verifier: Pubkey, worker: Pubkey, sgt_mint: Pubkey) -> Instruction:
        metas = [
            AccountMeta(verifier, True, True),
            AccountMeta(self.pda.config(), False, False),
            AccountMeta(worker, False, False),
            AccountMeta(self.pda.worker(worker), False, True),
            AccountMeta(self.pda.device(sgt_mint), False, True),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        return self._ix("verify_seeker", metas, bytes(sgt_mint))

    def _stake(self, name: str, signer: Pubkey, profile: Pubkey, amount: int) -> Instruction:
        config = self.pda.config()
        metas = [
            AccountMeta(signer, True, True),
            AccountMeta(config, False, False),
            AccountMeta(profile, False, True),
            AccountMeta(self.skr_mint, False, False),
            AccountMeta(ata(signer, self.skr_mint, self.skr_tp), False, True),
            AccountMeta(ata(config, self.skr_mint, self.skr_tp), False, True),
            AccountMeta(self.skr_tp, False, False),
            AccountMeta(SYSTEM_PROGRAM, False, False),
        ]
        return self._ix(name, metas, struct.pack("<Q", amount))

    def stake_skr(self, worker: Pubkey, amount: int, unstake: bool = False) -> Instruction:
        return self._stake("unstake_skr" if unstake else "stake_skr", worker, self.pda.worker(worker), amount)

    def creator_stake_skr(self, creator: Pubkey, amount: int, unstake: bool = False) -> Instruction:
        return self._stake("creator_unstake_skr" if unstake else "creator_stake_skr", creator, self.pda.creator(creator), amount)

    def close_mission(self, creator: Pubkey, m: Mission) -> Instruction:
        metas = [
            AccountMeta(creator, True, True),
            AccountMeta(self.pda.config(), False, False),
            AccountMeta(m.address, False, True),
            AccountMeta(m.vault, False, True),
            AccountMeta(self.usdc_mint, False, False),
            AccountMeta(ata(creator, self.usdc_mint, self.usdc_tp), False, True),
            AccountMeta(self.usdc_tp, False, False),
        ]
        return self._ix("close_mission", metas)
