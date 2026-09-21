package app.legwork.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Format
import app.legwork.ui.Routes
import app.legwork.ui.components.Card
import app.legwork.ui.components.CheckRow
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.SecondaryButton
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import app.legwork.vm.Phase
import kotlinx.coroutines.delay

private val steps = listOf("Checking location", "Reading the photo", "Matching the mission", "Releasing escrow on Solana")

@Composable
fun ResultScreen(vm: AppViewModel, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val verifying = state.phase == Phase.Verifying
    val r = state.lastResult
    val m = state.lastResultMission

    if (verifying || r == null) {
        var step by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(1_400); step = (step + 1).coerceAtMost(steps.lastIndex) } }
        Column(Modifier.fillMaxSize().background(Legwork.Bg).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(64.dp), color = Legwork.Accent, strokeWidth = 5.dp)
            Spacer(Modifier.height(24.dp))
            Text("Verifying your proof", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(steps[step] + "…", style = MaterialTheme.typography.bodyLarge, color = Legwork.Muted)
        }
        return
    }

    val approved = r.status == "approved"
    val scale by animateFloatAsState(1f, animationSpec = tween(500), label = "pop")

    Column(Modifier.fillMaxSize().background(if (approved) Legwork.Money else Legwork.Bg)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(96.dp).scale(scale).clip(CircleShape).background(if (approved) Color.White else Legwork.DangerSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (approved) Icons.Filled.Check else Icons.Filled.Close, null,
                    tint = if (approved) Legwork.Money else Legwork.Danger, modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            if (approved) {
                Text("Mission verified", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("+" + Format.usdc(r.amount ?: m?.reward ?: 0), style = MaterialTheme.typography.displayLarge, color = Color.White)
                Text("USDC paid to ${Format.shortKey(state.wallet)}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
            } else {
                Text(if (r.status == "rejected") "Not verified" else "Something went wrong", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(r.reason ?: "The proof did not match the mission.", style = MaterialTheme.typography.bodyLarge, color = Legwork.Muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(m?.title ?: "Mission", style = MaterialTheme.typography.titleMedium)
                    if (r.confidence != null) Text("Confidence ${r.confidence}%", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                    Spacer(Modifier.height(8.dp))
                    r.checks.forEach { CheckRow(it) }
                    if (r.answer != null && r.answer.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Answer recorded: ${r.answer}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (r.signature != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Transaction ${Format.shortKey(r.signature)}", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                    }
                }
            }
        }
        Column(Modifier.background(if (approved) Legwork.Money else Legwork.Bg).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (approved && r.signature != null) {
                SecondaryButton("View on explorer", onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.config.explorerTx(r.signature))))
                })
                SecondaryButton("Share", onClick = {
                    val text = "I just earned ${Format.usdc(r.amount ?: 0)} on Legwork for a real-world mission. Paid on Solana in seconds. ${state.config.explorerTx(r.signature)}"
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share"))
                })
                PrimaryButton("Find the next mission", color = Legwork.Ink, onClick = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } })
            } else {
                if (state.activeMission != null) PrimaryButton("Try again", onClick = { nav.navigate(Routes.ACTIVE) { popUpTo(Routes.HOME) } })
                SecondaryButton("Back to missions", onClick = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } })
            }
        }
    }
}
