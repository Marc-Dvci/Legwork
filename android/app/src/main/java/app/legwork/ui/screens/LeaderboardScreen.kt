package app.legwork.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Format
import app.legwork.ui.components.Card
import app.legwork.ui.components.EmptyState
import app.legwork.ui.components.SeekerBadge
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel

@Composable
fun LeaderboardScreen(vm: AppViewModel, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshLeaderboard() }
    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") }
            Column {
                Text("Top scouts", style = MaterialTheme.typography.headlineMedium)
                Text("Ranked by verified missions, read from chain", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.leaderboard.isEmpty()) item { EmptyState("No scouts yet", "Complete a mission and you'll be the first name here.") }
            itemsIndexed(state.leaderboard, key = { _, e -> e.wallet }) { i, e ->
                val me = e.wallet == state.wallet
                Card {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (i < 3) Legwork.AccentSoft else Legwork.SurfaceAlt),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = if (i < 3) Legwork.Accent else Legwork.Muted) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (me) "You" else Format.shortKey(e.wallet), style = MaterialTheme.typography.titleMedium)
                            Text("${e.approved} missions · score ${e.score} · ${e.streak}-day streak", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                        }
                        if (e.seekerVerified) SeekerBadge(small = true)
                    }
                }
            }
        }
    }
}
