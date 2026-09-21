package app.legwork.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Categories
import app.legwork.core.Format
import app.legwork.core.Tiers
import app.legwork.ui.Routes
import app.legwork.ui.components.Card
import app.legwork.ui.components.CategoryDot
import app.legwork.ui.components.EmptyState
import app.legwork.ui.components.Pill
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.SecondaryButton
import app.legwork.ui.components.SectionTitle
import app.legwork.ui.components.SeekerBadge
import app.legwork.ui.components.StatTile
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Legwork.Muted)
        }
        trailing?.invoke()
    }
}

@Composable
fun ConnectPrompt(vm: AppViewModel, sender: ActivityResultSender, why: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◎", style = MaterialTheme.typography.displayLarge, color = Legwork.Accent)
        Spacer(Modifier.height(8.dp))
        Text("Connect your wallet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(why, style = MaterialTheme.typography.bodyMedium, color = Legwork.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Connect wallet", loading = state.walletBusy, onClick = { vm.connectWallet(sender) {} })
    }
}

// ---------------------------------------------------------------------------------------------
// Activity: earnings and history
// ---------------------------------------------------------------------------------------------

@Composable
fun ActivityTab(vm: AppViewModel, sender: ActivityResultSender, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(state.wallet) { if (state.wallet != null) vm.refreshWorker() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Earnings", "Paid in USDC, straight to your wallet")
        if (state.wallet == null) { ConnectPrompt(vm, sender, "Your earnings, streak and reputation live on Solana under your wallet."); return }
        val p = state.profile
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(Modifier.padding(20.dp)) {
                        Text("Today", style = MaterialTheme.typography.labelMedium, color = Legwork.Muted)
                        Text(Format.usdc(state.todayEarned), style = MaterialTheme.typography.displayMedium, color = Legwork.Money)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("This week", Format.usdc(state.weekEarned), Modifier.weight(1f))
                            StatTile("Lifetime", Format.usdc(p?.totalEarned ?: 0), Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Missions", "${p?.approved ?: 0}", Modifier.weight(1f))
                    StatTile("Approval", "${p?.approvalRate ?: 100}%", Modifier.weight(1f))
                    StatTile("Streak", "${if (p?.streakAlive == true) p.streak else 0}d", Modifier.weight(1f), accent = Legwork.Accent)
                }
            }
            item {
                Card {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = Legwork.Accent)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            val alive = p?.streakAlive == true
                            val today = (System.currentTimeMillis() / 1000 / 86_400).toInt()
                            val doneToday = p != null && p.lastDay == today
                            Text(
                                when {
                                    p == null || p.streak == 0 -> "Start a streak"
                                    doneToday -> "${p.streak}-day streak, safe for today"
                                    alive -> "${p.streak}-day streak"
                                    else -> "Streak reset"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (doneToday) "Come back tomorrow to keep it going" else "Complete one mission today to keep your streak",
                                style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                            )
                        }
                    }
                }
            }
            item { SectionTitle("History", Modifier.padding(top = 4.dp)) }
            if (state.completions.isEmpty()) item {
                EmptyState("Nothing yet", "Your first paid mission will show up here with its transaction.")
            }
            items(state.completions, key = { it.address }) { c ->
                Card(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.config.explorerAddress(c.address))))
                }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(c.missionCategory ?: 0, 36)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.missionTitle ?: Format.shortKey(c.mission), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(c.approvedAt * 1000)) + " · ${c.confidence}% confidence",
                                style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                            )
                        }
                        Text("+" + Format.usdc(c.amount), style = MaterialTheme.typography.titleMedium, color = Legwork.Money)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------------------------

@Composable
fun ProfileTab(vm: AppViewModel, sender: ActivityResultSender, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var seekerMsg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(state.wallet) { if (state.wallet != null) vm.refreshWorker() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Profile", state.session.walletLabel ?: Format.shortKey(state.wallet)) {
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Settings", tint = Legwork.Muted) }
        }
        if (state.wallet == null) { ConnectPrompt(vm, sender, "Your reputation is an account on Solana. Connect to see it."); return }
        val p = state.profile ?: app.legwork.data.WorkerProfile(state.wallet!!, exists = false)
        val tier = Tiers.of(p.approved, p.skrStaked, p.seekerVerified)
        val next = Tiers.next(tier)

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Legwork score", style = MaterialTheme.typography.labelMedium, color = Legwork.Muted)
                                Text("${p.score}", style = MaterialTheme.typography.displayMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Pill(tier.name, Legwork.AccentSoft, Legwork.Accent)
                                Spacer(Modifier.height(6.dp))
                                if (p.seekerVerified) SeekerBadge()
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (next != null) {
                            val needMissions = (next.minApproved - p.approved).coerceAtLeast(0)
                            val needSkr = (next.minSkr - p.skrStaked).coerceAtLeast(0)
                            val parts = mutableListOf<String>()
                            if (needMissions > 0) parts += "$needMissions more missions"
                            if (needSkr > 0) parts += "stake ${Format.skr(needSkr)}"
                            Text(
                                "Next: ${next.name}. " + (if (parts.isEmpty()) "Unlocked" else parts.joinToString(" and ")) + ".",
                                style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                            )
                            Spacer(Modifier.height(6.dp))
                            val progress = if (next.minApproved == 0) 1f else (p.approved.toFloat() / next.minApproved).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Legwork.Accent, trackColor = Legwork.Line)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(tier.perk, style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Missions", "${p.approved}", Modifier.weight(1f))
                    StatTile("Approval", "${p.approvalRate}%", Modifier.weight(1f))
                    StatTile("Categories", "${Integer.bitCount(p.categories)}", Modifier.weight(1f))
                }
            }
            item {
                Card(onClick = { nav.navigate(Routes.STAKE) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Legwork.SeekerSoft), contentAlignment = Alignment.Center) {
                            Text("S", style = MaterialTheme.typography.titleLarge, color = Legwork.Seeker, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SKR stake", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (p.skrStaked > 0) "${Format.skr(p.skrStaked)} locked · raises your tier" else "Stake SKR to unlock Trusted missions",
                                style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Legwork.Muted)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Verified, null, tint = Legwork.Seeker)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (p.seekerVerified) "Seeker verified" else "Verify your Seeker", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (p.seekerVerified) "Your Seeker Genesis Token is bound to this wallet. Seeker-only missions are open to you."
                                    else "Holding a Seeker Genesis Token unlocks Seeker-only missions and counts as one device, one worker.",
                                    style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                                )
                            }
                        }
                        if (!p.seekerVerified) {
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton(if (checking) "Checking…" else "Check for Genesis Token", enabled = !checking, onClick = {
                                checking = true
                                vm.verifySeeker { ok, reason -> checking = false; seekerMsg = if (ok) "Seeker verified" else reason }
                            })
                        }
                        seekerMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = Legwork.Muted) }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Wallet", style = MaterialTheme.typography.labelMedium, color = Legwork.Muted)
                        Text(state.wallet ?: "", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("USDC", state.usdcBalance?.let { Format.usdc(it) } ?: "—", Modifier.weight(1f), Legwork.Money)
                            StatTile("SKR", state.skrBalance?.let { Format.skr(it) } ?: "—", Modifier.weight(1f), Legwork.Seeker)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Leaderboard", { nav.navigate(Routes.LEADERBOARD) }, Modifier.weight(1f))
                            SecondaryButton("Disconnect", { vm.disconnect(sender) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Text(
                    "Explorer: " + Format.shortKey(state.config.programId),
                    style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                    modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.config.explorerAddress(state.config.programId)))) }
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Create: creator dashboard + new mission
// ---------------------------------------------------------------------------------------------

@Composable
fun CreateTab(vm: AppViewModel, sender: ActivityResultSender, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(state.wallet) { if (state.wallet != null) vm.refreshCreator() }

    if (composing) { CreateMissionScreen(vm, sender, onClose = { composing = false }); return }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Create", "Send people into the real world")
        if (state.wallet == null) { ConnectPrompt(vm, sender, "Missions are funded from your wallet into an escrow on Solana."); return }
        val cp = state.creatorProfile
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PrimaryButton("New mission", onClick = { composing = true }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Missions", "${cp?.missions ?: 0}", Modifier.weight(1f))
                    StatTile("Funded", Format.usdc(cp?.totalFunded ?: 0), Modifier.weight(1f))
                    StatTile("Paid out", Format.usdc(cp?.totalPaid ?: 0), Modifier.weight(1f), Legwork.Money)
                }
            }
            item {
                val staked = cp?.skrStaked ?: 0
                val threshold = state.config.creatorStakeThreshold
                Card(onClick = { nav.navigate(Routes.STAKE) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (staked >= threshold && threshold > 0) "Half fees active" else "Halve your platform fee", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (staked >= threshold && threshold > 0) "${Format.skr(staked)} staked · ${state.config.feeBps / 200}% fee on every mission"
                                else "Stake ${Format.skr(threshold)} as a creator to pay ${state.config.feeBps / 200}% instead of ${state.config.feeBps / 100}%",
                                style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Legwork.Muted)
                    }
                }
            }
            item { SectionTitle("Your missions") }
            if (state.creatorMissions.isEmpty()) item {
                EmptyState("No missions yet", "Fund one and nearby Seekers will see it on their map within seconds.")
            }
            items(state.creatorMissions, key = { it.mission.address }) { s ->
                val m = s.mission
                Card(onClick = { nav.navigate(Routes.creatorMission(m.address)) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryDot(m.category, 36)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text("${m.filled}/${m.slots} completed · ${Format.usdc(m.reward)} each", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                            }
                            Pill(
                                when (m.status) { 0 -> if (m.deadline > System.currentTimeMillis() / 1000) "Open" else "Expired"; 1 -> "Filled"; else -> "Closed" },
                                if (m.status == 0) Legwork.MoneySoft else Legwork.SurfaceAlt, if (m.status == 0) Legwork.Money else Legwork.Muted,
                            )
                        }
                        if (m.question.isNotBlank() && (s.yes + s.no + s.unknown) > 0) {
                            Spacer(Modifier.height(10.dp))
                            AnswerBar(s.yes, s.no, s.unknown)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnswerBar(yes: Int, no: Int, unknown: Int) {
    val total = (yes + no + unknown).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            if (yes > 0) Box(Modifier.weight(yes.toFloat()).fillMaxSize().background(Legwork.Money))
            if (no > 0) Box(Modifier.weight(no.toFloat()).fillMaxSize().background(Legwork.Danger))
            if (unknown > 0) Box(Modifier.weight(unknown.toFloat()).fillMaxSize().background(Legwork.Line))
        }
        Spacer(Modifier.height(4.dp))
        Text("Yes $yes · No $no · Unclear $unknown", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
    }
}
