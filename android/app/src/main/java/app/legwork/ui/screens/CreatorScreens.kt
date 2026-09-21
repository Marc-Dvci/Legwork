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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Categories
import app.legwork.core.Format
import app.legwork.data.CreateMissionRequest
import app.legwork.ui.components.Card
import app.legwork.ui.components.CheckRow
import app.legwork.ui.components.MissionMap
import app.legwork.ui.components.Pill
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.StatTile
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Template(val title: String, val instructions: String, val question: String, val category: Int)

private val templates = listOf(
    Template("Verify USDC acceptance", "Photograph the payment options sign or terminal at the checkout so the accepted methods are readable.", "Does this shop accept USDC or crypto payments?", 5),
    Template("Check charging station", "Photograph the EV charger screen and connector so the status is visible.", "Is the charger operational?", 2),
    Template("Confirm event poster", "Photograph the poster where it is displayed, including its surroundings.", "Is the poster still up and intact?", 1),
    Template("Shelf availability check", "Photograph the shelf section for this product with the price labels visible.", "Is the product in stock?", 0),
    Template("Verify opening hours", "Photograph the opening hours sign on the door or window.", "Is the business open right now?", 5),
)

@Composable
fun CreateMissionScreen(vm: AppViewModel, sender: ActivityResultSender, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var category by remember { mutableIntStateOf(5) }
    var reward by remember { mutableStateOf("4") }
    var slots by remember { mutableStateOf("5") }
    var radius by remember { mutableStateOf("75") }
    var days by remember { mutableStateOf("7") }
    var requiresSeeker by remember { mutableStateOf(false) }
    var minScore by remember { mutableStateOf("0") }
    var lat by remember { mutableStateOf(state.fix?.lat) }
    var lon by remember { mutableStateOf(state.fix?.lon) }
    var published by remember { mutableStateOf<String?>(null) }

    val rewardMicro = ((reward.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
    val n = slots.toIntOrNull() ?: 0
    val escrow = rewardMicro * n
    val staked = state.creatorProfile?.skrStaked ?: 0
    val feeBps = if (state.config.creatorStakeThreshold > 0 && staked >= state.config.creatorStakeThreshold) state.config.feeBps / 2 else state.config.feeBps
    val fee = escrow * feeBps / 10_000
    val valid = title.isNotBlank() && instructions.isNotBlank() && rewardMicro > 0 && n > 0 && lat != null && lon != null &&
        (radius.toIntOrNull() ?: 0) in 10..2000 && title.length <= 48 && instructions.length <= 200 && question.length <= 80
    val pinMission = if (lat != null && lon != null) app.legwork.data.Mission(
        address = "draft", id = -1, creator = "", title = title.ifBlank { "New mission" }, instructions = "", reward = rewardMicro.coerceAtLeast(1),
        slots = n, filled = 0, lat = lat!!, lon = lon!!, radiusM = radius.toIntOrNull() ?: 75, deadline = Long.MAX_VALUE,
    ) else null

    if (published != null) {
        Column(Modifier.fillMaxSize().background(Legwork.Bg).statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🚀", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text("Mission funded", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("${Format.usdc(escrow)} is locked in escrow on Solana. Nearby Seekers can see it now.", style = MaterialTheme.typography.bodyLarge, color = Legwork.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Transaction ${Format.shortKey(published)}", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Done", onClick = onClose)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("New mission", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Start from a template", style = MaterialTheme.typography.labelLarge, color = Legwork.Muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates) { t ->
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (title == t.title) Legwork.AccentSoft else Legwork.Surface)
                            .clickable { title = t.title; instructions = t.instructions; question = t.question; category = t.category }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(t.title, style = MaterialTheme.typography.labelMedium) }
                }
            }
            OutlinedTextField(title, { title = it.take(48) }, label = { Text("Title (${title.length}/48)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instructions, { instructions = it.take(200) }, label = { Text("What the worker must photograph (${instructions.length}/200)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(question, { question = it.take(80) }, label = { Text("Yes/no question to answer from the photo (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Category", style = MaterialTheme.typography.labelLarge, color = Legwork.Muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Categories.all) { c ->
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(if (category == c.id) Legwork.Ink else Legwork.Surface)
                            .clickable { category = c.id }.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text("${c.emoji} ${c.name}", style = MaterialTheme.typography.labelMedium, color = if (category == c.id) Legwork.Surface else Legwork.Ink) }
                }
            }
            Text("Location: tap the map to move the pin", style = MaterialTheme.typography.labelLarge, color = Legwork.Muted)
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))) {
                MissionMap(
                    state.config.mapStyleUrl, listOfNotNull(pinMission), state.fix, target = pinMission, modifier = Modifier.fillMaxSize(),
                    onMapTap = { la, lo -> lat = la; lon = lo },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(reward, { reward = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Reward (USDC)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(slots, { slots = it.filter { c -> c.isDigit() } }, label = { Text("Completions") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(radius, { radius = it.filter { c -> c.isDigit() } }, label = { Text("Radius (m)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(days, { days = it.filter { c -> c.isDigit() } }, label = { Text("Open for (days)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(minScore, { minScore = it.filter { c -> c.isDigit() } }, label = { Text("Min. score") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text("Seeker only", style = MaterialTheme.typography.labelLarge)
                    Switch(requiresSeeker, { requiresSeeker = it })
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Funding", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Line("$n × ${Format.usdc(rewardMicro)} rewards", Format.usdc(escrow))
                    Line("Platform fee (${feeBps / 100.0}%)", Format.usdc(fee))
                    Line("Total from your wallet", Format.usdc(escrow + fee), bold = true)
                    Spacer(Modifier.height(4.dp))
                    Text("Rewards sit in the mission's own vault; whatever is not claimed comes back to you when you close it.", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                    if (state.usdcBalance != null && state.usdcBalance!! < escrow + fee)
                        Text("Wallet holds ${Format.usdc(state.usdcBalance!!)} USDC", style = MaterialTheme.typography.bodySmall, color = Legwork.Danger)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.background(Legwork.Surface).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton("Fund and publish", enabled = valid, loading = state.walletBusy, onClick = {
                vm.createMission(
                    sender,
                    CreateMissionRequest(
                        creator = state.wallet ?: return@PrimaryButton, title = title.trim(), instructions = instructions.trim(), question = question.trim(),
                        category = category, proofKind = 0, reward = rewardMicro, slots = n, lat = lat!!, lon = lon!!,
                        radiusM = radius.toInt(), deadline = System.currentTimeMillis() / 1000 + (days.toLongOrNull() ?: 7) * 86_400,
                        requiresSeeker = requiresSeeker, minScore = minScore.toIntOrNull() ?: 0,
                    )
                ) { sig -> if (sig != null) published = sig }
            })
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = if (bold) Legwork.Ink else Legwork.Muted, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CreatorMissionScreen(vm: AppViewModel, address: String, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = state.creatorMissions.firstOrNull { it.mission.address == address }
    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text(s?.mission?.title ?: "Mission", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        if (s == null) return
        val m = s.mission
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Completed", "${m.filled}/${m.slots}", Modifier.weight(1f))
                    StatTile("Paid out", Format.usdc(m.reward * m.filled), Modifier.weight(1f), Legwork.Money)
                    StatTile("In escrow", Format.usdc(m.reward * m.remaining), Modifier.weight(1f))
                }
            }
            if (m.question.isNotBlank()) item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(m.question, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        if (s.yes + s.no + s.unknown == 0) Text("No answers yet", color = Legwork.Muted, style = MaterialTheme.typography.bodySmall)
                        else AnswerBar(s.yes, s.no, s.unknown)
                    }
                }
            }
            item {
                Text(
                    "View escrow on explorer", style = MaterialTheme.typography.labelLarge, color = Legwork.Accent,
                    modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.config.explorerAddress(m.address)))) },
                )
            }
            item { Text("Verified completions", style = MaterialTheme.typography.titleLarge) }
            items(s.completions, key = { it.address }) { c ->
                Card(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.config.explorerAddress(c.address)))) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.shortKey(c.worker), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Pill(when (c.answer) { 1 -> "Yes"; 2 -> "No"; else -> "Unclear" }, when (c.answer) { 1 -> Legwork.MoneySoft; 2 -> Legwork.DangerSoft; else -> Legwork.SurfaceAlt }, when (c.answer) { 1 -> Legwork.Money; 2 -> Legwork.Danger; else -> Legwork.Muted })
                        }
                        Text(
                            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(c.approvedAt * 1000)) + " · ${c.confidence}% confidence · proof ${c.proofHash.take(10)}…",
                            style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                        )
                    }
                }
            }
        }
    }
}
