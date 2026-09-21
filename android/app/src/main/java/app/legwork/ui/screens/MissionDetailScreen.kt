package app.legwork.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Categories
import app.legwork.core.Format
import app.legwork.core.Geo
import app.legwork.ui.Routes
import app.legwork.ui.components.Card
import app.legwork.ui.components.MissionMap
import app.legwork.ui.components.Pill
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.RewardTag
import app.legwork.ui.components.SeekerBadge
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import app.legwork.vm.Phase
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun MissionDetailScreen(vm: AppViewModel, sender: ActivityResultSender, address: String, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val m = vm.mission(address)
    if (m == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text("Mission not found. Pull to refresh on the map.", color = Legwork.Muted)
        }
        return
    }
    val d = vm.distanceTo(m)
    val cat = Categories.byId(m.category)
    val isActive = state.activeMission?.address == m.address
    val blockedBySeeker = m.requiresSeeker && state.profile?.seekerVerified != true
    val blockedByScore = (state.profile?.score ?: 0) < m.minScore

    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                MissionMap(state.config.mapStyleUrl, listOf(m), state.fix, target = m, modifier = Modifier.fillMaxSize())
                Row(Modifier.statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.clip(CircleShape).background(Legwork.Surface)) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Legwork.Ink)
                    }
                }
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("${cat.emoji} ${cat.name}", Legwork.SurfaceAlt, Legwork.Ink)
                    Spacer(Modifier.width(8.dp))
                    if (m.requiresSeeker) SeekerBadge(small = true)
                    Spacer(Modifier.weight(1f))
                    Text(Format.relativeDeadline(m.deadline), style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(m.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    RewardTag(m.reward, large = true)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Geo.formatDistance(d)} away · about ${Geo.walkMinutes(d)} min on foot · ${m.remaining} of ${m.slots} slots left",
                    style = MaterialTheme.typography.bodyMedium, color = Legwork.Muted,
                )
                Spacer(Modifier.height(20.dp))

                Text("What to do", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(m.instructions, style = MaterialTheme.typography.bodyLarge)
                if (m.question.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Card {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, tint = Legwork.Sky)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("The creator wants to know", style = MaterialTheme.typography.labelMedium, color = Legwork.Muted)
                                Text(m.question, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                Text("Proof", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ProofLine("Be within ${m.radiusM} m of the pin (GPS)")
                ProofLine(if (m.proofKind == 1) "Scan the mission QR code" else "One photo taken in the app")
                ProofLine("Verified automatically, usually within seconds")
                if (m.minScore > 0) ProofLine("Legwork score of ${m.minScore} or more")
                Spacer(Modifier.height(20.dp))

                Text("Paid by", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Card {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Legwork.AccentSoft), contentAlignment = Alignment.Center) {
                            Text((m.creatorName ?: m.creator).take(1).uppercase(), color = Legwork.Accent, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.creatorName ?: Format.shortKey(m.creator), style = MaterialTheme.typography.titleSmall)
                            Text("Escrow funded on Solana · ${Format.usdc(m.reward * m.remaining)} still locked", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                        }
                        if (m.creatorVerified) Icon(Icons.Filled.Check, null, tint = Legwork.Money)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Flag, null, tint = Legwork.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Stay on public ground. Never photograph people without permission. Leave if you feel unsafe.",
                        style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "View escrow on explorer",
                    style = MaterialTheme.typography.labelMedium, color = Legwork.Accent,
                    modifier = Modifier.padding(vertical = 4.dp).clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.config.explorerAddress(m.address))))
                    },
                )
            }
        }
        Column(Modifier.background(Legwork.Surface).navigationBarsPadding().padding(16.dp)) {
            when {
                state.wallet == null -> PrimaryButton("Connect wallet to start", loading = state.walletBusy, onClick = { vm.connectWallet(sender) {} })
                m.completedByMe -> PrimaryButton("Completed", onClick = {}, enabled = false)
                isActive -> PrimaryButton("Continue mission", onClick = { nav.navigate(Routes.ACTIVE) })
                !m.isOpen -> PrimaryButton("No slots left", onClick = {}, enabled = false)
                blockedBySeeker -> PrimaryButton("Seeker verification required", onClick = {}, enabled = false)
                blockedByScore -> PrimaryButton("Score ${m.minScore} required", onClick = {}, enabled = false)
                else -> PrimaryButton(
                    "Start mission", loading = state.phase == Phase.Reserving,
                    onClick = { vm.startMission(m) { ok -> if (ok) nav.navigate(Routes.ACTIVE) } },
                )
            }
        }
    }
}

@Composable
private fun ProofLine(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Legwork.MoneySoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Legwork.Money, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
