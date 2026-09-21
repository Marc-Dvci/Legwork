package app.legwork.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Format
import app.legwork.core.Geo
import app.legwork.ui.Routes
import app.legwork.ui.components.Card
import app.legwork.ui.components.EmptyState
import app.legwork.ui.components.MissionCard
import app.legwork.ui.components.MissionMap
import app.legwork.ui.components.Pill
import app.legwork.ui.components.RewardTag
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

private enum class Tab(val label: String, val icon: ImageVector) {
    Missions("Missions", Icons.Filled.Explore),
    Activity("Activity", Icons.Filled.Timeline),
    Create("Create", Icons.Filled.AddCircle),
    Profile("Profile", Icons.Filled.Person),
}

@Composable
fun HomeShell(vm: AppViewModel, sender: ActivityResultSender, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state by vm.state.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.startLocation()
    }
    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        if (vm.tracker.hasPermission()) vm.startLocation() else permission.launch(perms.toTypedArray())
        if (state.missions.isEmpty()) vm.refreshMissions()
    }

    Column(Modifier.fillMaxSize().background(Legwork.Bg)) {
        Box(Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.Missions -> MissionsTab(vm, nav)
                Tab.Activity -> ActivityTab(vm, sender, nav)
                Tab.Create -> CreateTab(vm, sender, nav)
                Tab.Profile -> ProfileTab(vm, sender, nav)
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Legwork.Surface).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Tab.entries.forEachIndexed { i, t ->
            val on = i == selected
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (on) Legwork.Accent else Legwork.Muted, modifier = Modifier.size(24.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (on) Legwork.Accent else Legwork.Muted)
            }
        }
    }
}

@Composable
private fun MissionsTab(vm: AppViewModel, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showMap by rememberSaveable { mutableStateOf(true) }
    val open = remember(state.missions) { state.missions.filter { it.isOpen || it.completedByMe } }
    val active = state.activeMission

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Near you", style = MaterialTheme.typography.headlineLarge)
                val n = open.count { !it.completedByMe }
                val total = open.filter { !it.completedByMe }.sumOf { it.reward }
                Text(
                    if (n == 0) "No open missions in range yet" else "$n missions · ${Format.usdc(total)} available",
                    style = MaterialTheme.typography.bodyMedium, color = Legwork.Muted,
                )
            }
            IconButton(onClick = { vm.refreshMissions() }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Legwork.Muted) }
            Segmented(showMap) { showMap = it }
        }
        if (state.missionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Legwork.Accent, trackColor = Legwork.Line)

        if (active != null) ActiveBanner(vm, active) { nav.navigate(Routes.ACTIVE) }

        if (showMap) {
            Box(Modifier.weight(1f)) {
                MissionMap(
                    styleUrl = state.config.mapStyleUrl, missions = open.filter { !it.completedByMe },
                    fix = state.fix, modifier = Modifier.fillMaxSize(),
                    onMissionTap = { nav.navigate(Routes.mission(it)) },
                )
                val nearest = open.firstOrNull { !it.completedByMe }
                if (nearest != null) Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    MissionCard(nearest, vm.distanceTo(nearest)) { nav.navigate(Routes.mission(nearest.address)) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (open.isEmpty() && !state.missionsLoading) item {
                    EmptyState(
                        "No missions within 50 km",
                        state.missionsError ?: "New missions appear as creators fund them. Turn on alerts and we'll tell you when one lands nearby.",
                        "Refresh",
                    ) { vm.refreshMissions() }
                }
                items(open, key = { it.address }) { m ->
                    MissionCard(m, vm.distanceTo(m)) { nav.navigate(Routes.mission(m.address)) }
                }
            }
        }
    }
}

@Composable
private fun Segmented(map: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Legwork.SurfaceAlt).padding(3.dp)) {
        listOf("Map" to true, "List" to false).forEach { (label, isMap) ->
            val on = map == isMap
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Legwork.Surface else Legwork.SurfaceAlt)
                    .clickable { onChange(isMap) }.padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Legwork.Ink else Legwork.Muted) }
        }
    }
}

@Composable
private fun ActiveBanner(vm: AppViewModel, m: app.legwork.data.Mission, onClick: () -> Unit) {
    val d = vm.distanceTo(m)
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Card(onClick = onClick) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("In progress", Legwork.AccentSoft, Legwork.Accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("${Geo.formatDistance(d)} away · tap to continue", style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                }
                RewardTag(m.reward)
            }
        }
    }
}
