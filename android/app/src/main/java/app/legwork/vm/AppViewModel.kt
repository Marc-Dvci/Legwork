package app.legwork.vm

import android.app.Application
import android.os.Build
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.legwork.core.Config
import app.legwork.core.Geo
import app.legwork.data.AppConfig
import app.legwork.data.Completion
import app.legwork.data.CreateMissionRequest
import app.legwork.data.CreatorProfile
import app.legwork.data.LeaderboardEntry
import app.legwork.data.LegworkApi
import app.legwork.data.Mission
import app.legwork.data.MissionStats
import app.legwork.data.Session
import app.legwork.data.SessionState
import app.legwork.data.SiwsRequest
import app.legwork.data.SolanaRpc
import app.legwork.data.StakeRequest
import app.legwork.data.VerifyResult
import app.legwork.data.WorkerProfile
import app.legwork.location.Fix
import app.legwork.location.LocationTracker
import app.legwork.notify.NearbyWorker
import app.legwork.wallet.WalletManager
import app.legwork.wallet.WalletOutcome
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Idle, Reserving, Verifying }

data class UiState(
    val ready: Boolean = false,
    val session: SessionState = SessionState(),
    val config: AppConfig = AppConfig(),
    val fix: Fix? = null,
    val missions: List<Mission> = emptyList(),
    val missionsLoading: Boolean = false,
    val missionsError: String? = null,
    val profile: WorkerProfile? = null,
    val completions: List<Completion> = emptyList(),
    val usdcBalance: Long? = null,
    val skrBalance: Long? = null,
    val activeMission: Mission? = null,
    val phase: Phase = Phase.Idle,
    val lastResult: VerifyResult? = null,
    val lastResultMission: Mission? = null,
    val creatorProfile: CreatorProfile? = null,
    val creatorMissions: List<MissionStats> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val walletBusy: Boolean = false,
    val toast: String? = null,
) {
    val wallet get() = session.wallet
    val todayEarned: Long get() {
        val start = System.currentTimeMillis() / 1000 / 86_400 * 86_400
        return completions.filter { it.approvedAt >= start }.sumOf { it.amount }
    }
    val weekEarned: Long get() {
        val start = System.currentTimeMillis() / 1000 - 7 * 86_400
        return completions.filter { it.approvedAt >= start }.sumOf { it.amount }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val wallet = WalletManager()
    val tracker = LocationTracker(app)
    private var api = LegworkApi(Config.VERIFIER_URL) { _state.value.session.jwt }
    private val rpc = SolanaRpc(AppConfig().rpcUrl)
    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            val s = session.current()
            api.baseUrl = s.verifierUrl
            _state.update { it.copy(session = s) }
            wallet.configure("devnet", s.mwaToken)
            loadConfig()
            _state.update { it.copy(ready = true) }
            session.state.collect { st ->
                _state.update { it.copy(session = st) }
                api.baseUrl = st.verifierUrl
            }
        }
        viewModelScope.launch {
            // Whenever the session settles with a wallet, refresh the worker's view.
            var lastWallet: String? = null
            session.state.collect { st ->
                if (st.wallet != lastWallet) {
                    lastWallet = st.wallet
                    if (st.wallet != null) refreshWorker() else _state.update {
                        it.copy(profile = null, completions = emptyList(), usdcBalance = null, skrBalance = null)
                    }
                }
            }
        }
    }

    // ----- Config and location -------------------------------------------------------------

    fun loadConfig() = viewModelScope.launch {
        runCatching { api.config() }.onSuccess { cfg ->
            rpc.rpcUrl = cfg.rpcUrl
            wallet.configure(cfg.cluster, _state.value.session.mwaToken)
            _state.update { it.copy(config = cfg) }
        }.onFailure { e -> toast("Verifier unreachable: ${e.message}") }
    }

    fun startLocation() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            tracker.lastKnown()?.let { f -> _state.update { it.copy(fix = f) } }
            tracker.updates().collect { f ->
                _state.update { it.copy(fix = f) }
            }
        }
        viewModelScope.launch {
            // One refresh once we have a position.
            val first = state.first { it.fix != null }
            refreshMissions(first.fix)
        }
    }

    fun stopLocation() { locationJob?.cancel(); locationJob = null }

    fun distanceTo(m: Mission): Double? {
        val f = _state.value.fix ?: return m.distanceM
        return Geo.distanceM(f.lat, f.lon, m.lat, m.lon)
    }

    // ----- Missions --------------------------------------------------------------------------

    fun refreshMissions(fix: Fix? = _state.value.fix) = viewModelScope.launch {
        _state.update { it.copy(missionsLoading = true, missionsError = null) }
        runCatching { api.missions(fix?.lat, fix?.lon, 50.0, _state.value.session.wallet) }
            .onSuccess { list ->
                _state.update { it.copy(missions = list.sortedBy { m -> m.distanceM ?: Double.MAX_VALUE }, missionsLoading = false) }
                restoreActive(list)
            }
            .onFailure { e -> _state.update { it.copy(missionsLoading = false, missionsError = e.message ?: "Could not load missions") } }
    }

    private suspend fun restoreActive(list: List<Mission>) {
        val s = _state.value.session
        val addr = s.activeMission ?: return
        if (s.activeMissionExpires < System.currentTimeMillis() / 1000) {
            session.setActiveMission(null, 0); return
        }
        if (_state.value.activeMission == null) {
            list.firstOrNull { it.address == addr }?.let { m -> _state.update { it.copy(activeMission = m) } }
        }
    }

    fun mission(address: String): Mission? =
        _state.value.missions.firstOrNull { it.address == address }
            ?: _state.value.activeMission?.takeIf { it.address == address }
            ?: _state.value.creatorMissions.firstOrNull { it.mission.address == address }?.mission

    /** Reserve the mission through the verifier; the worker signs nothing here. */
    fun startMission(m: Mission, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        _state.update { it.copy(phase = Phase.Reserving) }
        val r = runCatching { api.reserve(m.address) }
        _state.update { it.copy(phase = Phase.Idle) }
        r.onSuccess { res ->
            session.setActiveMission(m.address, res.expiresAt)
            _state.update { it.copy(activeMission = m) }
            onDone(true)
        }.onFailure { e ->
            toast(e.message ?: "Could not reserve this mission"); onDone(false)
        }
    }

    fun cancelMission() = viewModelScope.launch {
        session.setActiveMission(null, 0)
        _state.update { it.copy(activeMission = null) }
    }

    fun submitProof(m: Mission, photo: ByteArray, fix: Fix, onDone: (VerifyResult) -> Unit) = viewModelScope.launch {
        _state.update { it.copy(phase = Phase.Verifying) }
        val r = runCatching {
            api.submitProof(
                m.address, photo, fix.lat, fix.lon, fix.accuracyM, fix.timeMs / 1000,
                fix.mock, "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        }
        val result = r.getOrElse { e -> VerifyResult(status = "error", reason = e.message ?: "Verification failed") }
        _state.update { it.copy(phase = Phase.Idle, lastResult = result, lastResultMission = m) }
        if (result.status == "approved") {
            session.setActiveMission(null, 0)
            _state.update { it.copy(activeMission = null) }
            refreshWorker()
            refreshMissions()
        }
        onDone(result)
    }

    // ----- Worker ----------------------------------------------------------------------------

    fun refreshWorker() = viewModelScope.launch {
        val w = _state.value.session.wallet ?: return@launch
        runCatching { api.profile(w) }.onSuccess { p -> _state.update { it.copy(profile = p) } }
        runCatching { api.completions(w) }.onSuccess { c -> _state.update { it.copy(completions = c.sortedByDescending { x -> x.approvedAt }) } }
        val cfg = _state.value.config
        runCatching { rpc.tokenBalance(w, cfg.usdcMint) }.onSuccess { b -> _state.update { it.copy(usdcBalance = b) } }
        runCatching { rpc.tokenBalance(w, cfg.skrMint) }.onSuccess { b -> _state.update { it.copy(skrBalance = b) } }
    }

    fun refreshLeaderboard() = viewModelScope.launch {
        runCatching { api.leaderboard() }.onSuccess { l -> _state.update { it.copy(leaderboard = l) } }
    }

    fun verifySeeker(onDone: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val w = _state.value.session.wallet ?: return@launch
        // Check on the phone first, then let the verifier attest it on chain.
        val mint = runCatching { rpc.findSeekerGenesisToken(w) }.getOrNull()
        if (mint == null) { onDone(false, "No Seeker Genesis Token in this wallet"); return@launch }
        runCatching { api.verifySeeker() }
            .onSuccess { r -> refreshWorker(); onDone(r.verified, r.reason) }
            .onFailure { e -> onDone(false, e.message) }
    }

    // ----- Wallet ----------------------------------------------------------------------------

    fun connectWallet(sender: ActivityResultSender, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        _state.update { it.copy(walletBusy = true) }
        when (val r = wallet.signIn(sender)) {
            is WalletOutcome.Ok -> {
                val p = r.value
                val auth = runCatching { api.signIn(SiwsRequest(p.wallet, p.message, p.signature)) }
                auth.onSuccess { a ->
                    session.setWallet(p.wallet, p.label, p.mwaToken, a.token)
                    session.setOnboardingDone()
                    NearbyWorker.schedule(getApplication())
                    _state.update { it.copy(walletBusy = false) }
                    onDone(true)
                }.onFailure { e ->
                    _state.update { it.copy(walletBusy = false) }
                    toast("Sign-in rejected by verifier: ${e.message}"); onDone(false)
                }
            }
            WalletOutcome.NoWallet -> {
                _state.update { it.copy(walletBusy = false) }
                toast("No Solana wallet app found. Install Phantom, Solflare or use the Seeker wallet."); onDone(false)
            }
            is WalletOutcome.Failed -> {
                _state.update { it.copy(walletBusy = false) }
                toast(r.message); onDone(false)
            }
        }
    }

    fun disconnect(sender: ActivityResultSender) = viewModelScope.launch {
        wallet.disconnect(sender)
        session.setWallet(null, null, null, null)
        session.setActiveMission(null, 0)
        _state.update { it.copy(activeMission = null) }
    }

    fun skipOnboarding() = viewModelScope.launch { session.setOnboardingDone() }

    /** Stake or unstake SKR: the verifier builds the transaction, the wallet signs and sends it. */
    fun stake(sender: ActivityResultSender, amount: Long, role: String, unstake: Boolean, onDone: (String?) -> Unit) =
        viewModelScope.launch {
            val w = _state.value.session.wallet ?: return@launch
            _state.update { it.copy(walletBusy = true) }
            val built = runCatching { api.buildStake(StakeRequest(w, amount, role, unstake)) }
            built.onFailure { e -> _state.update { it.copy(walletBusy = false) }; toast(e.message ?: "Could not build transaction"); onDone(null) }
            val tx = built.getOrNull() ?: return@launch
            val sig = signAndSend(sender, tx.transaction)
            _state.update { it.copy(walletBusy = false) }
            if (sig != null) { awaitConfirmation(sig); refreshWorker(); refreshCreator() }
            onDone(sig)
        }

    fun createMission(sender: ActivityResultSender, req: CreateMissionRequest, onDone: (String?) -> Unit) =
        viewModelScope.launch {
            _state.update { it.copy(walletBusy = true) }
            val built = runCatching { api.buildCreateMission(req) }
            built.onFailure { e -> _state.update { it.copy(walletBusy = false) }; toast(e.message ?: "Could not build transaction"); onDone(null) }
            val tx = built.getOrNull() ?: return@launch
            val sig = signAndSend(sender, tx.transaction)
            _state.update { it.copy(walletBusy = false) }
            if (sig != null) { awaitConfirmation(sig); refreshCreator(); refreshMissions() }
            onDone(sig)
        }

    /** The wallet signs; the app submits to the cluster from /config so any wallet works on any network. */
    private suspend fun signAndSend(sender: ActivityResultSender, txB64: String): String? {
        val bytes = Base64.decode(txB64, Base64.DEFAULT)
        val signed = when (val r = wallet.sign(sender, bytes)) {
            is WalletOutcome.Ok -> { session.setMwaToken(wallet.authToken); r.value }
            WalletOutcome.NoWallet -> { toast("No Solana wallet app found"); return null }
            is WalletOutcome.Failed -> { toast(r.message); return null }
        }
        return runCatching { rpc.sendTransaction(signed) }
            .onFailure { e -> toast("Transaction rejected: ${e.message?.take(160)}") }
            .getOrNull()
    }

    private suspend fun awaitConfirmation(sig: String) {
        repeat(20) {
            val st = runCatching { rpc.signatureStatus(sig) }.getOrNull()
            if (st == "confirmed" || st == "finalized") return
            delay(1_000)
        }
    }

    // ----- Creator ---------------------------------------------------------------------------

    fun refreshCreator() = viewModelScope.launch {
        val w = _state.value.session.wallet ?: return@launch
        runCatching { api.creatorProfile(w) }.onSuccess { p -> _state.update { it.copy(creatorProfile = p) } }
        runCatching { api.creatorMissions(w) }.onSuccess { l -> _state.update { it.copy(creatorMissions = l) } }
    }

    // ----- Settings --------------------------------------------------------------------------

    fun setVerifierUrl(url: String) = viewModelScope.launch {
        session.setVerifierUrl(url.trim()); api.baseUrl = url.trim(); loadConfig()
    }

    fun setNotify(enabled: Boolean, radiusKm: Int, minReward: Long) = viewModelScope.launch {
        session.setNotify(enabled, radiusKm, minReward)
    }

    fun toast(msg: String) { _state.update { it.copy(toast = msg) } }
    fun clearToast() { _state.update { it.copy(toast = null) } }
}
