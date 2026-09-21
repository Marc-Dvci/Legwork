#!/usr/bin/env bash
# Starts a local Solana validator (Docker) with the Legwork program preloaded, RPC on :8899.
# Usage: scripts/localnet.sh          (foreground; Ctrl-C stops it)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROGRAM_ID="$(grep -o '"address": "[^"]*"' "$ROOT/program/idl/legwork.json" | head -1 | cut -d'"' -f4)"
docker rm -f legwork-localnet >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 docker run --name legwork-localnet --rm --security-opt seccomp=unconfined -p 8899:8899 -p 8900:8900 \
  -v "$ROOT/program/target/deploy:/deploy:ro" \
  solanafoundation/anchor:v1.0.2 bash -lc \
  "solana-test-validator --reset --quiet --bind-address $(hostname -i) --rpc-port 8899 --bpf-program $PROGRAM_ID /deploy/legwork.so"
