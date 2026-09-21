// Legwork pitch deck generator. `node build.js` writes Legwork-pitch.pptx next to this file.
const pptxgen = require("pptxgenjs");
const path = require("path");
const fs = require("fs");

const SHOTS = path.join(__dirname, "..", "screenshots");
const C = {
  bg: "FAF7F2", surface: "FFFFFF", alt: "F3EEE6", ink: "17130F", muted: "6E665D", line: "E8E1D8",
  accent: "FF5A1F", accentSoft: "FFE9DF", money: "0E9F6E", moneySoft: "DDF5EA", seeker: "7C3AED", seekerSoft: "EDE4FF",
  sky: "2563EB", skySoft: "DBEAFE", warn: "D97706",
};
const FONT = "Calibri";
const HEAD = "Cambria";

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.author = "Marc Donovici";
pres.title = "Legwork";

const W = 13.33, H = 7.5;

function base(dark = false) {
  const s = pres.addSlide();
  s.background = { color: dark ? C.ink : C.bg };
  return s;
}
function title(s, text, dark = false, y = 0.55) {
  s.addText(text, { x: 0.7, y, w: W - 1.4, h: 0.9, fontFace: HEAD, fontSize: 32, bold: true, color: dark ? "FFFFFF" : C.ink, isTextBox: true, margin: 0 });
}
function sub(s, text, dark = false, y = 1.35) {
  s.addText(text, { x: 0.7, y, w: W - 1.4, h: 0.5, fontFace: FONT, fontSize: 16, color: dark ? "CFC7BC" : C.muted, isTextBox: true, margin: 0 });
}
function footer(s, dark = false) {
  s.addText("Legwork · CLOCK IN, the Solana Mobile hackathon · Marc Donovici", {
    x: 0.7, y: H - 0.5, w: W - 1.4, h: 0.3, fontFace: FONT, fontSize: 10, color: dark ? "8F877D" : C.muted, isTextBox: true, margin: 0,
  });
}
function phone(s, file, x, y, h, caption) {
  const w = h * 1080 / 2400;
  s.addShape(pres.ShapeType.roundRect, { x: x - 0.06, y: y - 0.06, w: w + 0.12, h: h + 0.12, fill: { color: C.ink }, rectRadius: 0.25, line: { color: C.ink, width: 0 } });
  s.addImage({ path: path.join(SHOTS, file), x, y, w, h, rounding: false });
  if (caption) s.addText(caption, { x: x - 0.3, y: y + h + 0.1, w: w + 0.6, h: 0.35, fontFace: FONT, fontSize: 11, color: C.muted, align: "center", isTextBox: true, margin: 0 });
  return w;
}
function pill(s, text, x, y, bg, fg, w = 1.6) {
  s.addShape(pres.ShapeType.roundRect, { x, y, w, h: 0.36, fill: { color: bg }, rectRadius: 0.18, line: { color: bg, width: 0 } });
  s.addText(text, { x, y, w, h: 0.36, fontFace: FONT, fontSize: 11, bold: true, color: fg, align: "center", valign: "middle", isTextBox: true, margin: 0 });
}
function card(s, x, y, w, h, head, body, opts = {}) {
  s.addShape(pres.ShapeType.roundRect, { x, y, w, h, fill: { color: opts.fill || C.surface }, rectRadius: 0.18, line: { color: C.line, width: 1 } });
  if (opts.emoji) s.addText(opts.emoji, { x: x + 0.25, y: y + 0.2, w: 0.6, h: 0.6, fontSize: 24, isTextBox: true, margin: 0 });
  s.addText(head, { x: x + (opts.emoji ? 0.9 : 0.3), y: y + 0.2, w: w - (opts.emoji ? 1.2 : 0.6), h: 0.5, fontFace: FONT, fontSize: 16, bold: true, color: opts.headColor || C.ink, isTextBox: true, margin: 0, valign: "top" });
  s.addText(body, { x: x + 0.3, y: y + 0.75, w: w - 0.6, h: h - 0.95, fontFace: FONT, fontSize: 12, color: C.muted, isTextBox: true, margin: 0, valign: "top" });
}
function stat(s, x, y, w, big, label, color = C.ink) {
  s.addText(big, { x, y, w, h: 0.9, fontFace: HEAD, fontSize: 40, bold: true, color, isTextBox: true, margin: 0 });
  s.addText(label, { x, y: y + 0.9, w, h: 0.5, fontFace: FONT, fontSize: 12, color: C.muted, isTextBox: true, margin: 0 });
}

// ---------------------------------------------------------------------------- 1 title
{
  const s = base(true);
  s.addText("Legwork", { x: 0.9, y: 1.7, w: 8, h: 1.4, fontFace: HEAD, fontSize: 72, bold: true, color: C.accent, isTextBox: true, margin: 0 });
  s.addText("Get paid for the legwork.", { x: 0.9, y: 3.05, w: 8, h: 0.8, fontFace: HEAD, fontSize: 32, color: "FFFFFF", isTextBox: true, margin: 0 });
  s.addText("Paid, verified, real-world missions for Seeker owners. Funded in USDC, settled on Solana, proven with the phone in your pocket.", {
    x: 0.9, y: 3.95, w: 7.2, h: 1.1, fontFace: FONT, fontSize: 16, color: "CFC7BC", isTextBox: true, margin: 0,
  });
  s.addText("CLOCK IN · Solana Mobile hackathon · October 2026", { x: 0.9, y: 5.6, w: 8, h: 0.4, fontFace: FONT, fontSize: 13, color: "8F877D", isTextBox: true, margin: 0 });
  s.addText("Marc Donovici · github.com/Marc-Dvci/Legwork", { x: 0.9, y: 6.0, w: 8, h: 0.4, fontFace: FONT, fontSize: 13, color: "8F877D", isTextBox: true, margin: 0 });
  phone(s, "01_map.png", 9.6, 0.6, 6.3);
}

// ---------------------------------------------------------------------------- 2 problem
{
  const s = base();
  title(s, "Real-world facts go stale when nobody is looking");
  sub(s, "Does that café still accept USDC? Is the charger working? Is our poster still up? Someone has to go and look.");
  const items = [
    ["🏪", "Merchants and directories", "Payment methods, opening hours and stock change weekly. A wrong listing costs a customer every time."],
    ["🔌", "Infrastructure operators", "Chargers, kiosks and signage fail in the field long before a ticket is raised."],
    ["🎪", "Events and communities", "Posters, booths and pop-ups exist for days. Verification by staff costs more than the campaign."],
    ["◎", "The Solana ecosystem", "\"Where can I actually pay with USDC?\" is still answered by hearsay."],
  ];
  items.forEach(([e, h, b], i) => card(s, 0.7 + (i % 2) * 6.1, 2.15 + Math.floor(i / 2) * 2.2, 5.85, 1.95, h, b, { emoji: e }));
  s.addText("Today the options are an employee's afternoon, a contractor, a phone call nobody answers, or a gig platform with a 30% take and no proof.", {
    x: 0.7, y: 6.55, w: W - 1.4, h: 0.4, fontFace: FONT, fontSize: 13, italic: true, color: C.muted, isTextBox: true, margin: 0,
  });
  footer(s);
}

// ---------------------------------------------------------------------------- 3 product
{
  const s = base();
  title(s, "Seeker owners become a paid verification network");
  sub(s, "A creator funds a mission in USDC. A nearby worker walks there, proves it with GPS and the camera, and is paid from escrow in seconds.");
  const y = 2.05, h = 4.7;
  let x = 0.9;
  for (const [f, cap] of [["01_map.png", "Missions near you, priced"], ["02_detail.png", "What to do, what proves it"], ["03_arrived.png", "GPS confirms arrival"], ["05_paid.png", "Verified and paid"]]) {
    const w = phone(s, f, x, y, h, cap);
    x += w + 0.55;
  }
  footer(s);
}

// ---------------------------------------------------------------------------- 4 the loop
{
  const s = base();
  title(s, "One loop, six steps, every one of them on the phone");
  const steps = [
    ["1", "Discover", "Reward pins on a live map. Alerts when a mission lands within your radius."],
    ["2", "Reserve", "A slot is held for 45 minutes on chain. Gasless for the worker."],
    ["3", "Walk", "Live distance, navigate intent, and a green state the moment you are inside the radius."],
    ["4", "Prove", "In-app camera only. The photo carries GPS, accuracy, timestamp and device signals."],
    ["5", "Verify", "Location policy, freshness, duplicate detection, then a vision judge with named checks."],
    ["6", "Get paid", "approve_completion moves USDC from the vault to your wallet and writes the proof hash."],
  ];
  steps.forEach(([n, h, b], i) => {
    const x = 0.7 + (i % 3) * 4.05, y = 1.75 + Math.floor(i / 3) * 2.45;
    s.addShape(pres.ShapeType.roundRect, { x, y, w: 3.8, h: 2.2, fill: { color: C.surface }, rectRadius: 0.18, line: { color: C.line, width: 1 } });
    s.addShape(pres.ShapeType.ellipse, { x: x + 0.25, y: y + 0.25, w: 0.55, h: 0.55, fill: { color: i === 5 ? C.money : C.accent }, line: { color: C.accent, width: 0 } });
    s.addText(n, { x: x + 0.25, y: y + 0.25, w: 0.55, h: 0.55, fontFace: FONT, fontSize: 16, bold: true, color: "FFFFFF", align: "center", valign: "middle", isTextBox: true, margin: 0 });
    s.addText(h, { x: x + 0.95, y: y + 0.25, w: 2.6, h: 0.55, fontFace: FONT, fontSize: 18, bold: true, color: C.ink, valign: "middle", isTextBox: true, margin: 0 });
    s.addText(b, { x: x + 0.25, y: y + 0.95, w: 3.3, h: 1.1, fontFace: FONT, fontSize: 12.5, color: C.muted, isTextBox: true, margin: 0, valign: "top" });
  });
  s.addText("Score, streak and earnings update from chain, and the map already shows the next one.", { x: 0.7, y: 6.7, w: W - 1.4, h: 0.4, fontFace: FONT, fontSize: 13, italic: true, color: C.muted, isTextBox: true, margin: 0 });
  footer(s);
}

// ---------------------------------------------------------------------------- 5 why mobile
{
  const s = base();
  title(s, "Remove the phone and the product stops working");
  sub(s, "Legwork is not a web app in a wrapper. The phone is part of the protocol.");
  const rows = [
    ["📍", "GPS is the first proof", "Presence inside the mission radius, accuracy-aware, mock providers rejected."],
    ["📸", "The camera is the second", "Live capture only. No gallery uploads. Perceptual hashes catch re-used photos."],
    ["📱", "The Seeker is the third", "The Seeker Genesis Token binds one device to one worker for Seeker-only missions."],
    ["🔔", "Notifications bring people back", "\"$8 mission 400 m away\", computed on the device every 15 minutes from the last position."],
    ["🚶", "Movement is the mechanic", "Distance counts down live; arrival flips the screen green and unlocks the shutter."],
  ];
  rows.forEach(([e, h, b], i) => {
    const y = 2.0 + i * 0.95;
    s.addShape(pres.ShapeType.ellipse, { x: 0.7, y, w: 0.7, h: 0.7, fill: { color: C.accentSoft }, line: { color: C.accentSoft, width: 0 } });
    s.addText(e, { x: 0.7, y, w: 0.7, h: 0.7, fontSize: 20, align: "center", valign: "middle", isTextBox: true, margin: 0 });
    s.addText(h, { x: 1.6, y, w: 6.2, h: 0.35, fontFace: FONT, fontSize: 16, bold: true, color: C.ink, isTextBox: true, margin: 0 });
    s.addText(b, { x: 1.6, y: y + 0.35, w: 6.2, h: 0.45, fontFace: FONT, fontSize: 12.5, color: C.muted, isTextBox: true, margin: 0 });
  });
  phone(s, "04_capture.png", 9.0, 1.9, 4.9, "Checklist on the capture screen");
  footer(s);
}

// ---------------------------------------------------------------------------- 6 why solana
{
  const s = base();
  title(s, "The chain is the database");
  sub(s, "Missions, escrow, reservations, completions, reputation, streaks and stake are accounts of one Anchor program. The verifier holds no state.");
  const left = [
    ["Mission + vault", "A PDA per mission with its own USDC token account. The creator funds reward × slots in one signature."],
    ["Reservation", "Held for a worker by the verifier's signature. The worker never pays a fee."],
    ["Completion", "Proof hash, confidence and the yes/no answer, written in the same transaction as the payout."],
    ["WorkerProfile", "Approved, rejected, streak, score, categories, SKR stake, Seeker binding. Readable by any client."],
  ];
  left.forEach(([h, b], i) => card(s, 0.7, 2.1 + i * 1.15, 6.4, 1.07, h, b));
  const right = [
    ["Escrow you can inspect", "\"$12.00 still locked\" on the mission page is the vault balance."],
    ["Instant global settlement", "A worker in Strasbourg is paid by a creator anywhere, in seconds, with a transaction link."],
    ["Permissionless reads", "Leaderboards and creator analytics are getProgramAccounts calls."],
    ["Verifier is a key, not a database", "It can only pay a reserved worker from that mission's vault, once."],
  ];
  right.forEach(([h, b], i) => card(s, 7.35, 2.1 + i * 1.15, 5.3, 1.07, h, b, { fill: C.alt }));
  footer(s);
}

// ---------------------------------------------------------------------------- 7 why seeker
{
  const s = base();
  title(s, "Seeker hardware gets real marketplace utility");
  const blocks = [
    ["Seeker verified badge", "The app inspects the wallet's Token-2022 accounts for a Seeker Genesis Token (mint authority, metadata pointer, token group), then the verifier binds it on chain.", C.seekerSoft],
    ["One device, one worker", "A Device PDA maps each Genesis Token mint to a single wallet. Sponsored campaigns can pay once per Seeker, not once per keypair.", C.seekerSoft],
    ["Seeker-only missions", "Creators tick \"Seeker only\"; the program refuses approvals from unbound wallets. Ecosystem campaigns land in Seeker owners' pockets first.", C.seekerSoft],
    ["Verified tier from day one", "A bound Seeker skips the Explorer tier: priority verification and a badge on the leaderboard.", C.seekerSoft],
  ];
  blocks.forEach(([h, b, f], i) => card(s, 0.7 + (i % 2) * 4.55, 1.75 + Math.floor(i / 2) * 2.35, 4.3, 2.1, h, b, { fill: f, headColor: C.seeker }));
  phone(s, "07_profile.png", 10.0, 1.4, 5.5, "Profile: tier, stake, Seeker check");
  footer(s);
}

// ---------------------------------------------------------------------------- 8 SKR
{
  const s = base();
  title(s, "SKR is the commitment behind trust");
  sub(s, "Remove SKR and the marketplace loses its trust tiers and its fee schedule. Staking is enforced by the program, not by a spreadsheet.");
  const tiers = [["Explorer", "Standard missions", "0 missions"], ["Verified", "Badge, priority verification", "3 missions or a Seeker"], ["Trusted", "Higher-value missions, two reservations", "10 missions + 100 SKR"], ["Expert", "Premium campaigns, dispute reviewer", "40 missions + 1000 SKR"]];
  tiers.forEach(([n, perk, req], i) => {
    const y = 2.2 + i * 0.95;
    pill(s, n, 0.7, y + 0.08, i === 0 ? C.alt : C.seekerSoft, i === 0 ? C.muted : C.seeker, 1.5);
    s.addText(perk, { x: 2.4, y, w: 3.6, h: 0.5, fontFace: FONT, fontSize: 14, bold: true, color: C.ink, valign: "middle", isTextBox: true, margin: 0 });
    s.addText(req, { x: 6.0, y, w: 2.4, h: 0.5, fontFace: FONT, fontSize: 12.5, color: C.muted, valign: "middle", isTextBox: true, margin: 0 });
  });
  card(s, 8.9, 2.2, 3.75, 1.7, "Creators stake 500 SKR", "Platform fee drops from 10% to 5% on every mission they fund. Computed on chain at funding time.", { fill: C.seekerSoft, headColor: C.seeker });
  card(s, 8.9, 4.1, 3.75, 1.7, "Workers stake to climb", "Stake is locked in a config-owned vault and can be withdrawn at any time. Creators can require a minimum score.", { fill: C.seekerSoft, headColor: C.seeker });
  s.addText("Mainnet SKR mint SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3 · a test mint stands in on devnet", { x: 0.7, y: 6.3, w: W - 1.4, h: 0.4, fontFace: FONT, fontSize: 11, color: C.muted, isTextBox: true, margin: 0 });
  footer(s);
}

// ---------------------------------------------------------------------------- 9 creators
{
  const s = base();
  title(s, "Creators deploy people in one signature");
  const items = [
    ["Templates", "USDC acceptance, charger check, poster check, shelf availability, opening hours."],
    ["Pin on the map, set the radius", "Reward, number of completions, deadline, Seeker-only, minimum score."],
    ["Fund and publish", "One wallet signature creates the mission, its vault, the escrow and the fee."],
    ["Answers, not just photos", "A yes/no question is answered on chain per completion; the dashboard tallies Yes / No / Unclear."],
    ["Close and recover", "Whatever is not claimed comes back to the creator's wallet."],
  ];
  items.forEach(([h, b], i) => card(s, 0.7, 1.55 + i * 1.08, 7.4, 1.0, h, b));
  phone(s, "08_create.png", 9.0, 1.4, 5.5, "New mission with funding summary");
  footer(s);
}

// ---------------------------------------------------------------------------- 10 trust and privacy
{
  const s = base();
  title(s, "Every proof passes named checks the worker can see");
  const checks = [
    ["At the mission location", "distance ≤ radius + min(accuracy, 50 m) + 25 m"],
    ["GPS accuracy", "≤ 100 m, mock-location flag rejected"],
    ["Fresh capture", "timestamp within 10 min of server time"],
    ["Original photo", "SHA-256 not on chain, dhash Hamming > 6 from recent"],
    ["Live photo, not a screen", "vision judge flags re-photographed screens and prints"],
    ["Photo matches the mission", "relevant, fulfils the instructions, confidence ≥ 60"],
    ["2 to 4 mission-specific checks", "e.g. \"accepted payment methods are readable\""],
  ];
  checks.forEach(([h, b], i) => {
    const y = 1.7 + i * 0.68;
    s.addShape(pres.ShapeType.ellipse, { x: 0.7, y: y + 0.08, w: 0.4, h: 0.4, fill: { color: C.money }, line: { color: C.money, width: 0 } });
    s.addText("✓", { x: 0.7, y: y + 0.08, w: 0.4, h: 0.4, fontSize: 14, bold: true, color: "FFFFFF", align: "center", valign: "middle", isTextBox: true, margin: 0 });
    s.addText(h, { x: 1.3, y, w: 3.4, h: 0.55, fontFace: FONT, fontSize: 14, bold: true, color: C.ink, valign: "middle", isTextBox: true, margin: 0 });
    s.addText(b, { x: 4.7, y, w: 4.2, h: 0.55, fontFace: FONT, fontSize: 12, color: C.muted, valign: "middle", isTextBox: true, margin: 0 });
  });
  card(s, 9.3, 1.7, 3.35, 2.2, "Privacy by construction", "Photos are judged in memory and discarded. Only the hash goes on chain. Precise location is read only while a mission screen is open.", { fill: C.moneySoft, headColor: C.money });
  card(s, 9.3, 4.1, 3.35, 2.2, "Keys stay in the wallet", "Sign-in, funding and staking go through Mobile Wallet Adapter. The verifier key can only pay a reserved worker from that mission's vault.", { fill: C.moneySoft, headColor: C.money });
  footer(s);
}

// ---------------------------------------------------------------------------- 11 business model and market
{
  const s = base();
  title(s, "A marketplace fee, paid at funding time, on chain");
  stat(s, 0.7, 1.7, 3.0, "10%", "platform fee on funded rewards", C.accent);
  stat(s, 3.9, 1.7, 3.0, "5%", "for creators staking 500 SKR", C.seeker);
  stat(s, 7.1, 1.7, 3.0, "$0", "for workers: reservations and payouts are gasless", C.money);
  s.addText("Worker reward $5 · creator pays $5.50 · Legwork treasury receives $0.50, in the same transaction that funds the escrow.", { x: 0.7, y: 3.35, w: W - 1.4, h: 0.4, fontFace: FONT, fontSize: 13, italic: true, color: C.muted, isTextBox: true, margin: 0 });
  const wedges = [
    ["🏪", "Commerce intelligence", "Payment methods, shelf checks, price and opening-hour verification for directories and brands."],
    ["🔌", "Infrastructure", "Chargers, kiosks, signage and accessibility checks for operators and cities."],
    ["🎪", "Events", "Poster presence, booth check-ins, conference scavenger missions."],
    ["◎", "Solana ecosystem", "\"Pay with USDC here\" merchant maps, Seeker-only campaigns, community verification drives."],
  ];
  wedges.forEach(([e, h, b], i) => card(s, 0.7 + i * 3.08, 4.0, 2.85, 2.6, h, b, { emoji: e }));
  footer(s);
}

// ---------------------------------------------------------------------------- 12 what is built
{
  const s = base(true);
  title(s, "Built and running on devnet", true);
  const facts = [
    ["9", "program instructions", C.accent],
    ["7", "on-chain account types", C.accent],
    ["14", "app screens", C.money],
    ["7", "named proof checks", C.money],
  ];
  facts.forEach(([n, l, c], i) => {
    s.addText(n, { x: 0.8 + i * 2.35, y: 1.6, w: 2.2, h: 1.0, fontFace: HEAD, fontSize: 48, bold: true, color: c, isTextBox: true, margin: 0 });
    s.addText(l, { x: 0.8 + i * 2.35, y: 2.6, w: 2.2, h: 0.5, fontFace: FONT, fontSize: 13, color: "CFC7BC", isTextBox: true, margin: 0 });
  });
  const lines = [
    "Android: Kotlin, Jetpack Compose, Mobile Wallet Adapter 2.0 (Sign-in-with-Solana, signTransactions), MapLibre, CameraX, WorkManager",
    "Program: Anchor 1.0, program id 86V9Vw6jPnoy4R6feJK6atwMK3x1oUootvvbqb8iZHD6, committed IDL",
    "Verifier: FastAPI, stateless, Gemini vision judge with structured verdicts, dhash duplicate detection, SIWS sessions",
    "Repository someone else can clone and run: pinned Docker build for the program, local validator script, devnet seeding, unit tests",
  ];
  lines.forEach((t, i) => s.addText("·  " + t, { x: 0.8, y: 3.5 + i * 0.5, w: 8.4, h: 0.5, fontFace: FONT, fontSize: 12.5, color: "E8E1D8", isTextBox: true, margin: 0 }));
  s.addText("github.com/Marc-Dvci/Legwork", { x: 0.8, y: 5.8, w: 8, h: 0.4, fontFace: FONT, fontSize: 16, bold: true, color: C.accent, isTextBox: true, margin: 0 });
  s.addText("Legwork. Get paid for the legwork.", { x: 0.8, y: 6.3, w: 8, h: 0.5, fontFace: HEAD, fontSize: 20, color: "FFFFFF", isTextBox: true, margin: 0 });
  phone(s, "05_paid.png", 10.0, 0.7, 6.1);
  footer(s, true);
}

const out = path.join(__dirname, "Legwork-pitch.pptx");
pres.writeFile({ fileName: out }).then(() => console.log("wrote", out, fs.statSync(out).size, "bytes"));
