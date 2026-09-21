package app.legwork.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

private data class Page(val emoji: String, val title: String, val body: String, val bg: Color)

private val pages = listOf(
    Page("🥾", "Get paid for the legwork", "Businesses and communities need someone on the ground. Legwork pays you in USDC to be that someone.", Legwork.AccentSoft),
    Page("📍", "Missions appear around you", "Check a store still accepts USDC. Confirm a poster is up. Photograph a shelf. Each one is a few minutes away.", Legwork.SkySoft),
    Page("📸", "Prove it with your phone", "GPS confirms you are there, the camera captures the evidence, and your Seeker vouches for you.", Legwork.MoneySoft),
    Page("◎", "Paid the moment it verifies", "Rewards sit in escrow on Solana. Once your proof clears, USDC lands in your wallet in seconds.", Legwork.SeekerSoft),
)

@Composable
fun OnboardingScreen(vm: AppViewModel, sender: ActivityResultSender, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var index by remember { mutableIntStateOf(0) }
    val last = index == pages.lastIndex

    Column(Modifier.fillMaxSize().background(Legwork.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Legwork", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Legwork.Accent)
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onClick = { index = pages.lastIndex }) { Text("Skip", color = Legwork.Muted) }
        }
        AnimatedContent(
            targetState = index, modifier = Modifier.weight(1f),
            transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
            label = "onboarding",
        ) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(32.dp)).background(p.bg),
                    contentAlignment = Alignment.Center,
                ) { Text(p.emoji, style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 2)) }
                Spacer(Modifier.height(32.dp))
                Text(p.title, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(12.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = Legwork.Muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == index) 22.dp else 8.dp, 8.dp)
                        .clip(CircleShape).background(if (i == index) Legwork.Accent else Legwork.Line)
                )
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (last) {
                PrimaryButton("Connect wallet", loading = state.walletBusy, onClick = {
                    vm.connectWallet(sender) { ok -> if (ok) onDone() }
                })
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.skipOnboarding(); onDone() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse missions first", color = Legwork.Muted)
                }
            } else {
                PrimaryButton("Continue", onClick = { index++ })
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
