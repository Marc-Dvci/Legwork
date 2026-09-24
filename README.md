# Legwork

**Get paid for the legwork.** Legwork is a Solana Mobile app where businesses and communities post small, location-bound missions ("photograph the payment sign at this café", "confirm this charger works", "is the poster still up?"), fund them in USDC, and nearby Seeker owners complete them with their phone. GPS proves presence, the in-app camera captures the evidence, a vision verifier judges it, and the escrow on Solana pays the worker in seconds.

Built for the **CLOCK IN** Solana Mobile hackathon (September to October 2026).

| | |
|---|---|
| Android app | Kotlin, Jetpack Compose, Mobile Wallet Adapter 2.0, MapLibre, CameraX |
| On-chain program | Anchor 1.0 (`program/`), devnet program id `86V9Vw6jPnoy4R6feJK6atwMK3x1oUootvvbqb8iZHD6` |
| Verifier | FastAPI oracle (`verifier/`), stateless, Gemini vision judge, signs payouts |
| Tokens | USDC escrow per mission, SKR staking for worker tiers and creator fees |
| Seeker | Seeker Genesis Token verification, one device = one worker on Seeker-only missions |

## The loop

```
Open Legwork  →  see "$4 · 215 m"  →  Start mission (slot reserved on chain)
   →  walk there ("You're at the mission location")  →  in-app camera
   →  verifier: GPS + freshness + duplicate + vision judge
   →  approve_completion: USDC leaves the mission vault, proof hash written on chain
   →  score, streak and earnings update  →  next mission
```

## Architecture

```
┌──────────────────────┐        ┌──────────────────────────┐        ┌───────────────────────────┐
│  Android (Compose)   │  MWA   │  Wallet app              │        │  Solana (devnet)           │
│  map · camera · GPS  │◄──────►│  Seed Vault / Phantom /  │        │  Legwork program           │
│  SIWS sign-in        │        │  Solflare                │        │   Config · Mission · Vault │
│  reads: balances,    │────────┼──────────────────────────┼───────►│   WorkerProfile · Creator  │
│  Seeker Genesis Tok. │  RPC   │                          │  RPC   │   Reservation · Completion │
└─────────┬────────────┘        └──────────────────────────┘        │   Device (SGT binding)     │
          │ HTTPS                                                   └─────────────▲──────────────┘
          ▼                                                                       │ signs reserve /
┌──────────────────────┐   getProgramAccounts (8 s cache)                         │ approve / verify
│  Verifier (FastAPI)  │──────────────────────────────────────────────────────────┘
│  SIWS → JWT          │
│  GPS policy · dhash  │   Gemini vision judge (structured JSON verdict)
│  builds unsigned txs │
└──────────────────────┘
```

**The chain is the database.** Missions, reservations, completions, reputation, streaks and stakes are accounts of the Legwork program. The verifier keeps an 8-second RPC cache and a rolling window of perceptual hashes, nothing else. Restarting it loses nothing.

**Who signs what**

| Action | Signer | Fee payer |
|---|---|---|
| Sign in (SIWS) | worker's wallet, via MWA | none |
| Create and fund a mission | creator's wallet, via MWA (`signTransactions`; the app submits) | creator |
| Stake / unstake SKR | wallet, via MWA | wallet |
| Reserve a slot | verifier | verifier (gasless for the worker) |
| Approve a completion and pay | verifier | verifier |
| Record a Seeker Genesis Token | verifier | verifier |
| Close a mission, refund the vault | creator | creator |

## On-chain program

`program/programs/legwork/src/lib.rs`, Anchor 1.0.2.

| Instruction | What it does |
|---|---|
| `initialize(fee_bps, creator_stake_threshold)` | Config PDA, verifier key, USDC and SKR mints, treasury, SKR stake vault |
| `create_mission(args)` | Creates the Mission PDA and its USDC vault, moves `reward × slots` in, sends the platform fee to the treasury (halved when the creator's SKR stake is above the threshold) |
| `reserve()` | Reservation PDA for (mission, worker), 45 minutes |
| `approve_completion(proof_hash, confidence, answer)` | Pays the worker from the vault, writes a Completion PDA (proof hash, confidence, yes/no answer), updates approved count, score, streak, category mask, closes the reservation |
| `record_rejection()` | Increments the worker's rejected count |
| `verify_seeker(sgt_mint)` | Binds a Seeker Genesis Token mint to one wallet (Device PDA) and flags the profile |
| `stake_skr` / `unstake_skr` | Worker stake in the config-owned vault |
| `creator_stake_skr` / `creator_unstake_skr` | Creator stake, halves the fee |
| `close_mission()` | Creator takes back whatever is left in the vault |

Guarantees the program enforces: a creator cannot spend committed rewards; only the verifier key approves; one completion per (mission, worker); Seeker-only missions require a bound Genesis Token; `min_score` is checked on chain; every payout carries the proof hash.

The IDL is committed at `program/idl/legwork.json` and mirrored in `verifier/legwork_idl.json`.

## Verifier

`verifier/app.py`. Endpoints:

| Route | Purpose |
|---|---|
| `GET /config` | cluster, RPC, program id, mints, fee schedule, map style |
| `POST /auth/siws` | verifies the Sign-in-with-Solana message and signature, returns a JWT |
| `GET /missions?lat&lon&radius_km&wallet` | open missions with distance and `completedByMe` |
| `POST /missions/{address}/reserve` | reserves on chain |
| `POST /missions/{address}/submit` | multipart photo + GPS; runs the policy and the judge; pays on approval |
| `GET /workers/{wallet}`, `/completions` | profile and history from chain |
| `POST /workers/verify-seeker` | checks the Token-2022 accounts for a Seeker Genesis Token, binds it on chain |
| `GET /leaderboard` | WorkerProfile accounts ranked by approved missions |
| `GET /creators/{wallet}/missions` | per-mission completions and yes/no/unclear tallies |
| `POST /tx/create-mission`, `POST /tx/stake` | build unsigned transactions for the wallet to sign |

Proof policy (`verifier/verify.py`): distance to the pin within `radius + min(accuracy, 50) + 25` m, accuracy ≤ 100 m, capture time within 10 minutes of server time, no mock-location flag, readable image ≥ 320 px, SHA-256 not already on chain, perceptual hash (dhash, Hamming ≤ 6) not in the recent window. The vision judge returns `relevant`, `fulfils_instructions`, `screen_or_reproduction`, `answer`, `confidence` and 2 to 4 named requirement checks; approval needs a live, relevant photo at confidence ≥ 60. The worker sees every check by name.

Photos are hashed and judged in memory. Only the hash goes on chain.

### Run locally

```bash
cd verifier
python -m venv .venv && .venv/Scripts/pip install -r requirements.txt   # or bin/pip on Linux
cp .env.example .env   # fill VERIFIER_KEYPAIR, JWT_SECRET, GEMINI_API_KEY
set -a; source .env; set +a
uvicorn app:app --port 8000
pytest tests            # unit tests: encoders vs IDL, PDAs, GPS policy, duplicates, SIWS
```

`GEMINI_API_KEY` is a Google AI Studio key. `VERTEX_PROJECT` with application default credentials works too and is tried first when set. `GEMINI_MODEL` is a comma-separated chain: the judge moves to the next model when one is overloaded or out of quota. `SKIP_VISION=1` approves on the GPS policy alone for local runs.

### Deploy

The live verifier runs on Vercel's Hobby plan at https://legwork-verifier.vercel.app (FastAPI, zero config). Import the repository in Vercel (`vercel.json` routes every path to `api/index.py`, which serves `verifier/`), then set `VERIFIER_KEYPAIR` (JSON array), `GEMINI_API_KEY`, `JWT_SECRET` and the public values from `verifier/.env.example`. `verifier/Dockerfile` runs the same service on any container host.

## Android app

`android/`, package `app.legwork`, minSdk 26, targetSdk 36.

```bash
cd android
./gradlew assembleDebug                                      # default verifier URL
./gradlew assembleDebug -PverifierUrl=http://10.0.2.2:8000   # verifier on the developer machine
../scripts/build_apk.sh                                      # release APK into release/legwork.apk
```

JDK 17 and the Android SDK (platform 36) are required. `local.properties` needs `sdk.dir`.

Screens: onboarding → Missions (map with reward pins, or list) → Mission detail (radius ring, proof requirements, escrow status) → Active mission (live distance, arrival state, navigate intent) → Capture (CameraX, checklist) → Result (checks, payout, explorer link, share) · Activity (today / week / lifetime, streak, history) · Create (templates, map pin, funding summary, Fund and publish) · Creator mission stats (completions, yes/no tallies) · Profile (score, tier, Seeker badge, stake, balances) · Stake · Leaderboard · Settings.

Mobile Wallet Adapter: `wallet/WalletManager.kt` wraps `MobileWalletAdapter` from `mobile-wallet-adapter-clientlib-ktx`. Sign-in uses `SignInWithSolana.Payload`; transactions use `signTransactions` and the app submits the signed bytes to the cluster from `/config`, so any MWA wallet works on devnet.

Seeker Genesis Token: `data/SolanaRpc.kt` lists the wallet's Token-2022 accounts and checks the mint authority, the metadata pointer and the token group member extension against the Solana Mobile constants, then asks the verifier to bind the mint on chain.

Location is read only while a mission screen is open. The 15-minute nearby scan (`notify/NearbyWorker.kt`) uses the last known position and posts a local notification: "$8 mission 400 m away".

Map tiles come from OpenFreeMap (no key in the APK).

## SKR

SKR is the commitment behind trust.

- **Workers** stake SKR to reach the Trusted (100 SKR + 10 missions) and Expert (1000 SKR + 40 missions) tiers. Creators can require a minimum score, and the program enforces it.
- **Creators** who stake 500 SKR pay a 5% platform fee instead of 10% on every mission they fund. The fee is computed on chain at funding time.
- Stake is held in a config-owned vault and can be withdrawn at any time.

On devnet a test SKR mint stands in for `SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3`.

## Seeker

A wallet holding a Seeker Genesis Token gets the Seeker verified badge, access to Seeker-only missions, and the Verified tier from the first mission. One Genesis Token binds to one wallet, which is what makes "one device, one worker" enforceable for sponsored campaigns.

## Devnet operations

```bash
scripts/anchor.sh "anchor build"                       # builds in the pinned Docker image
scripts/anchor.sh "anchor deploy --provider.cluster devnet"
python scripts/devnet.py status
python scripts/devnet.py mint skr                      # devnet test SKR
python scripts/devnet.py init                          # Config + stake vault
python scripts/devnet.py seed --lat 48.8590 --lon 2.3480 --seeker-only-last
```

`scripts/localnet.sh` runs `solana-test-validator` with the program preloaded for a fully local loop (`python scripts/devnet.py mint usdc` creates a local USDC stand-in).

## Repository layout

```
android/     Kotlin + Compose app
program/     Anchor workspace, committed IDL in program/idl
verifier/    FastAPI oracle, tests, Dockerfile (api/index.py + vercel.json host it on Vercel)
scripts/     anchor.sh, localnet.sh, devnet.py, build_apk.sh
docs/        pitch deck, demo script, screenshots
```

## Security notes

- The app never sees a private key. Authorization, sign-in and every signature go through the wallet app over MWA.
- The verifier key can only move funds from a mission vault to a worker who holds a reservation, and only once per (mission, worker). It cannot withdraw to itself.
- SIWS messages are bound to the `legwork.app` domain and expire after 10 minutes; sessions are HS256 JWTs.
- Mock-location devices are rejected; timestamps must match server time within 10 minutes; exact and near-duplicate photos are refused.
- Proof images are judged in memory and discarded; only the SHA-256 is written on chain.

## Licence

MIT.
