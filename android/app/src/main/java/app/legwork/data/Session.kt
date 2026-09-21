package app.legwork.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.legwork.core.Config
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore("legwork")

data class SessionState(
    val wallet: String? = null,
    val walletLabel: String? = null,
    val mwaToken: String? = null,
    val jwt: String? = null,
    val onboardingDone: Boolean = false,
    val verifierUrl: String = Config.VERIFIER_URL,
    val notifyRadiusKm: Int = 3,
    val notifyMinReward: Long = 1_000_000,
    val notifyEnabled: Boolean = true,
    val activeMission: String? = null,
    val activeMissionExpires: Long = 0,
) {
    val signedIn get() = wallet != null && jwt != null
}

/** Persistent per-device state. Nothing here is a secret: the wallet keys stay in the wallet app. */
class Session(private val context: Context) {
    private object K {
        val wallet = stringPreferencesKey("wallet")
        val walletLabel = stringPreferencesKey("wallet_label")
        val mwaToken = stringPreferencesKey("mwa_token")
        val jwt = stringPreferencesKey("jwt")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val verifier = stringPreferencesKey("verifier_url")
        val notifyRadius = intPreferencesKey("notify_radius_km")
        val notifyMinReward = longPreferencesKey("notify_min_reward")
        val notifyEnabled = booleanPreferencesKey("notify_enabled")
        val activeMission = stringPreferencesKey("active_mission")
        val activeExpires = longPreferencesKey("active_expires")
        val notified = stringPreferencesKey("notified_missions")
    }

    val state: Flow<SessionState> = context.store.data.map { p ->
        SessionState(
            wallet = p[K.wallet],
            walletLabel = p[K.walletLabel],
            mwaToken = p[K.mwaToken],
            jwt = p[K.jwt],
            onboardingDone = p[K.onboarding] ?: false,
            verifierUrl = p[K.verifier] ?: Config.VERIFIER_URL,
            notifyRadiusKm = p[K.notifyRadius] ?: 3,
            notifyMinReward = p[K.notifyMinReward] ?: 1_000_000,
            notifyEnabled = p[K.notifyEnabled] ?: true,
            activeMission = p[K.activeMission],
            activeMissionExpires = p[K.activeExpires] ?: 0,
        )
    }

    suspend fun current(): SessionState = state.first()

    suspend fun setWallet(wallet: String?, label: String?, mwaToken: String?, jwt: String?) {
        context.store.edit { p ->
            if (wallet == null) {
                p.remove(K.wallet); p.remove(K.walletLabel); p.remove(K.mwaToken); p.remove(K.jwt)
            } else {
                p[K.wallet] = wallet
                if (label != null) p[K.walletLabel] = label else p.remove(K.walletLabel)
                if (mwaToken != null) p[K.mwaToken] = mwaToken else p.remove(K.mwaToken)
                if (jwt != null) p[K.jwt] = jwt else p.remove(K.jwt)
            }
        }
    }

    suspend fun setMwaToken(token: String?) = context.store.edit { p ->
        if (token == null) p.remove(K.mwaToken) else p[K.mwaToken] = token
    }

    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }

    suspend fun setVerifierUrl(url: String) = context.store.edit { it[K.verifier] = url }

    suspend fun setNotify(enabled: Boolean, radiusKm: Int, minReward: Long) = context.store.edit {
        it[K.notifyEnabled] = enabled; it[K.notifyRadius] = radiusKm; it[K.notifyMinReward] = minReward
    }

    suspend fun setActiveMission(address: String?, expiresAt: Long) = context.store.edit { p ->
        if (address == null) { p.remove(K.activeMission); p.remove(K.activeExpires) } else {
            p[K.activeMission] = address; p[K.activeExpires] = expiresAt
        }
    }

    suspend fun notifiedMissions(): Set<String> =
        (context.store.data.first()[K.notified] ?: "").split(',').filter { it.isNotBlank() }.toSet()

    suspend fun markNotified(addresses: Set<String>) = context.store.edit { p ->
        val all = (notifiedMissions() + addresses).toList().takeLast(200)
        p[K.notified] = all.joinToString(",")
    }
}
