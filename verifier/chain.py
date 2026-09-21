"""Minimal JSON-RPC client over httpx plus typed reads of the Legwork program's accounts."""

from __future__ import annotations

import base64
import time
from dataclasses import dataclass, field

import base58
import httpx
from solders.hash import Hash
from solders.instruction import Instruction
from solders.keypair import Keypair
from solders.message import Message
from solders.pubkey import Pubkey
from solders.transaction import Transaction

from program import ACC_DISC, Completion, Config, CreatorProfile, Mission, Reservation, WorkerProfile

TOKEN_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
SGT_MINT_AUTHORITY = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"
SGT_GROUP = "GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te"


class RpcError(Exception):
    pass


@dataclass
class Rpc:
    url: str
    timeout: float = 30.0
    _client: httpx.Client = field(default_factory=lambda: httpx.Client(timeout=30.0))

    def call(self, method: str, params: list | None = None):
        r = self._client.post(self.url, json={"jsonrpc": "2.0", "id": 1, "method": method, "params": params or []})
        r.raise_for_status()
        body = r.json()
        if "error" in body:
            raise RpcError(body["error"])
        return body["result"]

    # ---- generic reads

    def account(self, pubkey: Pubkey) -> bytes | None:
        res = self.call("getAccountInfo", [str(pubkey), {"encoding": "base64", "commitment": "confirmed"}])
        v = res.get("value")
        if not v:
            return None
        return base64.b64decode(v["data"][0])

    def accounts_by_discriminator(self, program_id: Pubkey, disc: bytes, extra_filters: list | None = None) -> list[tuple[Pubkey, bytes]]:
        filters = [{"memcmp": {"offset": 0, "bytes": base58.b58encode(disc).decode()}}] + (extra_filters or [])
        res = self.call("getProgramAccounts", [str(program_id), {"encoding": "base64", "commitment": "confirmed", "filters": filters}])
        return [(Pubkey.from_string(a["pubkey"]), base64.b64decode(a["account"]["data"][0])) for a in res]

    def token_balance(self, owner: Pubkey, mint: Pubkey) -> int:
        res = self.call("getTokenAccountsByOwner", [str(owner), {"mint": str(mint)}, {"encoding": "jsonParsed", "commitment": "confirmed"}])
        return sum(int(a["account"]["data"]["parsed"]["info"]["tokenAmount"]["amount"]) for a in res["value"])

    def latest_blockhash(self) -> Hash:
        res = self.call("getLatestBlockhash", [{"commitment": "confirmed"}])
        return Hash.from_string(res["value"]["blockhash"])

    def send(self, tx: Transaction, skip_preflight: bool = False) -> str:
        raw = base64.b64encode(bytes(tx)).decode()
        return self.call("sendTransaction", [raw, {"encoding": "base64", "skipPreflight": skip_preflight, "preflightCommitment": "confirmed", "maxRetries": 5}])

    def status(self, signature: str) -> str | None:
        res = self.call("getSignatureStatuses", [[signature], {"searchTransactionHistory": True}])
        v = res["value"][0]
        if v is None:
            return None
        if v.get("err"):
            return "failed"
        return v.get("confirmationStatus")

    def confirm(self, signature: str, timeout_s: float = 45.0) -> bool:
        deadline = time.time() + timeout_s
        while time.time() < deadline:
            st = self.status(signature)
            if st in ("confirmed", "finalized"):
                return True
            if st == "failed":
                raise RpcError(f"transaction {signature} failed")
            time.sleep(0.8)
        return False

    def send_and_confirm(self, payer: Keypair, ixs: list[Instruction], signers: list[Keypair] | None = None) -> str:
        msg = Message.new_with_blockhash(ixs, payer.pubkey(), self.latest_blockhash())
        tx = Transaction.new_unsigned(msg)
        tx.sign([payer] + list(signers or []), msg.recent_blockhash)
        sig = self.send(tx)
        if not self.confirm(sig):
            raise RpcError(f"transaction {sig} not confirmed in time")
        return sig

    def unsigned_tx_b64(self, fee_payer: Pubkey, ixs: list[Instruction]) -> str:
        msg = Message.new_with_blockhash(ixs, fee_payer, self.latest_blockhash())
        return base64.b64encode(bytes(Transaction.new_unsigned(msg))).decode()

    # ---- Seeker Genesis Token

    def find_seeker_genesis_token(self, owner: Pubkey) -> str | None:
        res = self.call("getTokenAccountsByOwner", [str(owner), {"programId": TOKEN_2022}, {"encoding": "jsonParsed", "commitment": "confirmed"}])
        mints = [
            a["account"]["data"]["parsed"]["info"]["mint"] for a in res["value"]
            if int(a["account"]["data"]["parsed"]["info"]["tokenAmount"]["amount"]) > 0
        ]
        for mint in mints:
            info = self.call("getAccountInfo", [mint, {"encoding": "jsonParsed"}]).get("value")
            if not info:
                continue
            parsed = info["data"].get("parsed", {}).get("info", {})
            if parsed.get("mintAuthority") != SGT_MINT_AUTHORITY:
                continue
            metadata_ok = group_ok = False
            for ext in parsed.get("extensions", []):
                st = ext.get("state", {})
                if ext.get("extension") == "metadataPointer":
                    metadata_ok = st.get("authority") == SGT_GROUP and st.get("metadataAddress") == SGT_GROUP
                if ext.get("extension") == "tokenGroupMember":
                    group_ok = st.get("group") == SGT_GROUP
            if metadata_ok and group_ok:
                return mint
        return None


class Board:
    """Typed reads of the mission board with a short cache, so the phone never hits RPC limits."""

    def __init__(self, rpc: Rpc, program_id: Pubkey, ttl_s: float = 8.0):
        self.rpc = rpc
        self.program_id = program_id
        self.ttl = ttl_s
        self._cache: dict[str, tuple[float, object]] = {}

    def _cached(self, key: str, fn):
        now = time.time()
        hit = self._cache.get(key)
        if hit and now - hit[0] < self.ttl:
            return hit[1]
        value = fn()
        self._cache[key] = (now, value)
        return value

    def invalidate(self):
        self._cache.clear()

    def config(self) -> Config | None:
        from program import Pdas
        data = self.rpc.account(Pdas(self.program_id).config())
        return Config.decode(data) if data else None

    def missions(self) -> list[Mission]:
        def load():
            rows = self.rpc.accounts_by_discriminator(self.program_id, ACC_DISC["Mission"])
            return [Mission.decode(pk, d) for pk, d in rows]
        return self._cached("missions", load)

    def mission(self, address: Pubkey) -> Mission | None:
        for m in self.missions():
            if m.address == address:
                return m
        data = self.rpc.account(address)
        if data and data[:8] == ACC_DISC["Mission"]:
            return Mission.decode(address, data)
        return None

    def worker(self, wallet: Pubkey) -> WorkerProfile | None:
        from program import Pdas
        data = self.rpc.account(Pdas(self.program_id).worker(wallet))
        return WorkerProfile.decode(data) if data else None

    def workers(self) -> list[WorkerProfile]:
        def load():
            rows = self.rpc.accounts_by_discriminator(self.program_id, ACC_DISC["WorkerProfile"])
            return [WorkerProfile.decode(d) for _, d in rows]
        return self._cached("workers", load)

    def creator(self, wallet: Pubkey) -> CreatorProfile | None:
        from program import Pdas
        data = self.rpc.account(Pdas(self.program_id).creator(wallet))
        return CreatorProfile.decode(data) if data else None

    def reservation(self, mission: Pubkey, worker: Pubkey) -> Reservation | None:
        from program import Pdas
        data = self.rpc.account(Pdas(self.program_id).reservation(mission, worker))
        return Reservation.decode(data) if data else None

    def completions(self) -> list[Completion]:
        def load():
            rows = self.rpc.accounts_by_discriminator(self.program_id, ACC_DISC["Completion"])
            return [Completion.decode(pk, d) for pk, d in rows]
        return self._cached("completions", load)

    def completions_for_worker(self, wallet: Pubkey) -> list[Completion]:
        return [c for c in self.completions() if c.worker == wallet]

    def completions_for_mission(self, mission: Pubkey) -> list[Completion]:
        return [c for c in self.completions() if c.mission == mission]
