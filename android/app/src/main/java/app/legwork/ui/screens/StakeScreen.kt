package app.legwork.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Format
import app.legwork.core.Tiers
import app.legwork.ui.components.Card
import app.legwork.ui.components.Pill
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.SecondaryButton
import app.legwork.ui.components.StatTile
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun StakeScreen(vm: AppViewModel, sender: ActivityResultSender, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    var role by remember { mutableStateOf("worker") }
    var amount by remember { mutableStateOf("100") }
    var done by remember { mutableStateOf<String?>(null) }
    val p = state.profile
    val cp = state.creatorProfile
    val staked = if (role == "worker") p?.skrStaked ?: 0 else cp?.skrStaked ?: 0
    val micro = (amount.toDoubleOrNull() ?: 0.0).let { (it * 1_000_000).toLong() }

    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text("Stake SKR", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "SKR is the commitment behind trust on Legwork. Workers stake to climb tiers and reach higher-value missions; creators stake to halve the platform fee. Stake is yours to withdraw at any time.",
                style = MaterialTheme.typography.bodyMedium, color = Legwork.Muted,
            )
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Legwork.SurfaceAlt).padding(3.dp)) {
                listOf("worker" to "As a worker", "creator" to "As a creator").forEach { (id, label) ->
                    val on = role == id
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) Legwork.Surface else Legwork.SurfaceAlt)
                            .clickable { role = id }.padding(vertical = 8.dp), contentAlignment = Alignment.Center,
                    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Legwork.Ink else Legwork.Muted) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Staked", Format.skr(staked), Modifier.weight(1f), Legwork.Seeker)
                StatTile("In wallet", state.skrBalance?.let { Format.skr(it) } ?: "—", Modifier.weight(1f))
            }
            if (role == "worker") {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tiers", style = MaterialTheme.typography.titleMedium)
                        val current = Tiers.of(p?.approved ?: 0, p?.skrStaked ?: 0, p?.seekerVerified == true)
                        Tiers.all.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(t.name, if (t == current) Legwork.AccentSoft else Legwork.SurfaceAlt, if (t == current) Legwork.Accent else Legwork.Muted)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.perk, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        listOfNotNull(
                                            if (t.minApproved > 0) "${t.minApproved} missions" else null,
                                            if (t.minSkr > 0) Format.skr(t.minSkr) else null,
                                        ).joinToString(" + ").ifBlank { "Everyone starts here" },
                                        style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Creator fee", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Stake at least ${Format.skr(state.config.creatorStakeThreshold)} and every mission you fund pays ${state.config.feeBps / 200}% instead of ${state.config.feeBps / 100}%.",
                            style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (SKR)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            done?.let { Text("Confirmed: ${Format.shortKey(it)}", style = MaterialTheme.typography.bodySmall, color = Legwork.Money) }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.background(Legwork.Surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Stake ${amount.ifBlank { "0" }} SKR", loading = state.walletBusy, enabled = micro > 0, color = Legwork.Seeker, onClick = {
                vm.stake(sender, micro, role, unstake = false) { sig -> done = sig }
            })
            SecondaryButton("Unstake", enabled = micro in 1..staked && !state.walletBusy, onClick = {
                vm.stake(sender, micro, role, unstake = true) { sig -> done = sig }
            })
        }
    }
}
