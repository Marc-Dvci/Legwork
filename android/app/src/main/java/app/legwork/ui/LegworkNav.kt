package app.legwork.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.legwork.ui.screens.ActiveMissionScreen
import app.legwork.ui.screens.CaptureScreen
import app.legwork.ui.screens.CreatorMissionScreen
import app.legwork.ui.screens.HomeShell
import app.legwork.ui.screens.LeaderboardScreen
import app.legwork.ui.screens.MissionDetailScreen
import app.legwork.ui.screens.OnboardingScreen
import app.legwork.ui.screens.ResultScreen
import app.legwork.ui.screens.SettingsScreen
import app.legwork.ui.screens.StakeScreen
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MISSION = "mission/{address}"
    const val ACTIVE = "active"
    const val CAPTURE = "capture"
    const val RESULT = "result"
    const val STAKE = "stake"
    const val SETTINGS = "settings"
    const val LEADERBOARD = "leaderboard"
    const val CREATOR_MISSION = "creator/{address}"
    fun mission(address: String) = "mission/$address"
    fun creatorMission(address: String) = "creator/$address"
}

@Composable
fun LegworkNav(vm: AppViewModel, sender: ActivityResultSender, pendingMission: MutableState<String?>) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav: NavHostController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }
    LaunchedEffect(pendingMission.value, state.ready) {
        val addr = pendingMission.value
        if (addr != null && state.ready) { pendingMission.value = null; nav.navigate(Routes.mission(addr)) }
    }

    if (!state.ready) {
        Box(Modifier.fillMaxSize().background(Legwork.Bg))
        return
    }

    Box(Modifier.fillMaxSize().background(Legwork.Bg)) {
        NavHost(nav, startDestination = if (state.session.onboardingDone) Routes.HOME else Routes.ONBOARDING) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(vm, sender, onDone = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) { HomeShell(vm, sender, nav) }
            composable(Routes.MISSION) { back ->
                val address = back.arguments?.getString("address") ?: return@composable
                MissionDetailScreen(vm, sender, address, nav)
            }
            composable(Routes.ACTIVE) { ActiveMissionScreen(vm, nav) }
            composable(Routes.CAPTURE) { CaptureScreen(vm, nav) }
            composable(Routes.RESULT) { ResultScreen(vm, nav) }
            composable(Routes.STAKE) { StakeScreen(vm, sender, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, sender, nav) }
            composable(Routes.LEADERBOARD) { LeaderboardScreen(vm, nav) }
            composable(Routes.CREATOR_MISSION) { back ->
                val address = back.arguments?.getString("address") ?: return@composable
                CreatorMissionScreen(vm, address, nav)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
