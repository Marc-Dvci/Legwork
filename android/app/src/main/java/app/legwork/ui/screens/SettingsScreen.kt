package app.legwork.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Format
import app.legwork.ui.components.Card
import app.legwork.ui.components.SecondaryButton
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun SettingsScreen(vm: AppViewModel, sender: ActivityResultSender, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(state.session.verifierUrl) }
    var enabled by remember { mutableStateOf(state.session.notifyEnabled) }
    var radius by remember { mutableFloatStateOf(state.session.notifyRadiusKm.toFloat()) }
    var minReward by remember { mutableFloatStateOf(state.session.notifyMinReward / 1_000_000f) }

    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mission alerts", style = MaterialTheme.typography.titleMedium)
                            Text("Checked on this phone every 15 minutes from your last position", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it; vm.setNotify(it, radius.toInt(), (minReward * 1_000_000).toLong()) })
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Radius: ${radius.toInt()} km", style = MaterialTheme.typography.labelLarge)
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 1f..25f, steps = 23,
                        onValueChangeFinished = { vm.setNotify(enabled, radius.toInt(), (minReward * 1_000_000).toLong()) })
                    Text("Minimum reward: ${Format.usdc((minReward * 1_000_000).toLong())}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = minReward, onValueChange = { minReward = it }, valueRange = 0f..20f, steps = 39,
                        onValueChangeFinished = { vm.setNotify(enabled, radius.toInt(), (minReward * 1_000_000).toLong()) })
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Location and photos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Legwork reads your precise location only while a mission is open on screen, to confirm you are at the pin. " +
                            "Proof photos are sent once to the verifier, judged, hashed and discarded; only the hash is written to Solana. " +
                            "Nothing runs in the background except the 15-minute nearby check, which uses your last known position.",
                        style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                    )
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Network", style = MaterialTheme.typography.titleMedium)
                    Text("Cluster ${state.config.cluster} · program ${Format.shortKey(state.config.programId)}", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, label = { Text("Verifier URL") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Apply", onClick = { vm.setVerifierUrl(url) })
                }
            }
            if (state.wallet != null) SecondaryButton("Disconnect wallet", onClick = { vm.disconnect(sender); nav.popBackStack() })
            Text("Legwork 1.0.0 · Built for Solana Seeker", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
            Spacer(Modifier.height(24.dp))
        }
    }
}
