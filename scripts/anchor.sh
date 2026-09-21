#!/usr/bin/env bash
# Run any anchor/solana command inside the pinned Anchor image.
# Usage: scripts/anchor.sh anchor build
#        scripts/anchor.sh anchor deploy --provider.cluster devnet
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYS="${LEGWORK_KEYS:-$ROOT/../keys}"
mkdir -p "$ROOT/program/target/deploy"
[ -f "$KEYS/legwork-program.json" ] && cp "$KEYS/legwork-program.json" "$ROOT/program/target/deploy/legwork-keypair.json"
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$ROOT/program:/work" \
  -v "$KEYS:/keys" \
  -v legwork-cargo-registry:/root/.cargo/registry \
  -v legwork-cargo-git:/root/.cargo/git \
  -v legwork-solana-cache:/root/.cache/solana \
  -w /work \
  solanafoundation/anchor:v1.0.2 bash -lc "$*"
