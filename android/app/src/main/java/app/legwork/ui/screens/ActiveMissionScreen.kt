package app.legwork.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Config
import app.legwork.core.Format
import app.legwork.core.Geo
import app.legwork.ui.Routes
import app.legwork.ui.components.MissionMap
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.RewardTag
import app.legwork.ui.components.SecondaryButton
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel

@Composable
fun ActiveMissionScreen(vm: AppViewModel, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val m = state.activeMission
    LaunchedEffect(Unit) { vm.startLocation() }
    if (m == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val fix = state.fix
    val d = vm.distanceTo(m)
    val arrived = fix != null && d != null && d <= m.radiusM + Config.ARRIVAL_SLACK_M + fix.accuracyM.coerceAtMost(30f)
    val gpsOk = fix != null && fix.accuracyM <= 60f
    val expiresIn = ((state.session.activeMissionExpires - System.currentTimeMillis() / 1000) / 60).coerceAtLeast(0)
    val statusColor by animateColorAsState(if (arrived) Legwork.Money else Legwork.Accent, label = "status")

    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Box(Modifier.weight(1f)) {
            MissionMap(state.config.mapStyleUrl, listOf(m), fix, target = m, modifier = Modifier.fillMaxSize(), followUser = true)
            Row(Modifier.statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.clip(CircleShape).background(Legwork.Surface)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Legwork.Ink)
                }
            }
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(statusColor).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (arrived) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(
                        when {
                            fix == null -> "Waiting for GPS…"
                            arrived -> "You're at the mission location"
                            else -> "${Geo.formatDistance(d)} to go"
                        },
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Column(Modifier.background(Legwork.Surface).navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.headlineSmall)
                    Text("Reserved for you · $expiresIn min left", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                }
                RewardTag(m.reward)
            }
            Spacer(Modifier.height(14.dp))
            Text(m.instructions, style = MaterialTheme.typography.bodyMedium, color = Legwork.Ink)
            Spacer(Modifier.height(16.dp))
            Checklist(
                listOf(
                    "At location" to arrived,
                    "GPS accuracy ${fix?.accuracyM?.toInt()?.let { "$it m" } ?: "—"}" to gpsOk,
                    "Take the photo" to false,
                )
            )
            Spacer(Modifier.height(16.dp))
            if (arrived) {
                PrimaryButton("Capture proof", color = Legwork.Money, onClick = { nav.navigate(Routes.CAPTURE) })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Navigate", modifier = Modifier.weight(1f), onClick = {
                        val uri = Uri.parse("geo:${m.lat},${m.lon}?q=${m.lat},${m.lon}(${Uri.encode(m.title)})")
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { vm.toast("No maps app installed") }
                    })
                    PrimaryButton("Capture proof", modifier = Modifier.weight(1f), enabled = false, onClick = {})
                }
            }
            TextButton(onClick = { vm.cancelMission(); nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Release this mission", color = Legwork.Muted)
            }
        }
    }
}

@Composable
fun Checklist(items: List<Pair<String, Boolean>>) {
    Column(Modifier.fillMaxWidth()) {
        items.forEach { (label, done) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (done) Legwork.Money else Legwork.SurfaceAlt),
                    contentAlignment = Alignment.Center,
                ) { if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal, color = if (done) Legwork.Ink else Legwork.Muted)
            }
        }
    }
}
