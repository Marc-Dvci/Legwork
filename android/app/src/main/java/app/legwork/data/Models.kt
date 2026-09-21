package app.legwork.data

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val cluster: String = "devnet",
    val rpcUrl: String = "https://api.devnet.solana.com",
    val programId: String = "",
    val usdcMint: String = "",
    val skrMint: String = "",
    val explorerBase: String = "https://explorer.solana.com",
    val feeBps: Int = 1000,
    val creatorStakeThreshold: Long = 0,
    val verifierWallet: String = "",
    val mapStyleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
) {
    fun explorerTx(sig: String) = "$explorerBase/tx/$sig?cluster=$cluster"
    fun explorerAddress(addr: String) = "$explorerBase/address/$addr?cluster=$cluster"
}

@Serializable
data class Mission(
    val address: String,
    val id: Long,
    val creator: String,
    val title: String,
    val instructions: String,
    val question: String = "",
    val category: Int = 0,
    val proofKind: Int = 0,
    /** Reward per completion in USDC base units (6 decimals). */
    val reward: Long,
    val slots: Int,
    val filled: Int,
    val lat: Double,
    val lon: Double,
    val radiusM: Int,
    val deadline: Long,
    val requiresSeeker: Boolean = false,
    val minScore: Int = 0,
    val status: Int = 0,
    val createdAt: Long = 0,
    val distanceM: Double? = null,
    val creatorName: String? = null,
    val creatorVerified: Boolean = false,
    /** Set when the signed-in worker already completed it. */
    val completedByMe: Boolean = false,
) {
    val remaining get() = (slots - filled).coerceAtLeast(0)
    val isOpen get() = status == 0 && remaining > 0 && deadline > System.currentTimeMillis() / 1000
}

@Serializable
data class WorkerProfile(
    val wallet: String,
    val approved: Int = 0,
    val rejected: Int = 0,
    val streak: Int = 0,
    val lastDay: Int = 0,
    val skrStaked: Long = 0,
    val seekerVerified: Boolean = false,
    val totalEarned: Long = 0,
    val score: Int = 0,
    val categories: Int = 0,
    val exists: Boolean = true,
) {
    val approvalRate: Int get() = if (approved + rejected == 0) 100 else (approved * 100) / (approved + rejected)
    /** Streak is alive if the last approval was today or yesterday. */
    val streakAlive: Boolean get() {
        val today = (System.currentTimeMillis() / 1000 / 86_400).toInt()
        return streak > 0 && (today - lastDay) <= 1
    }
}

@Serializable
data class CreatorProfile(
    val wallet: String,
    val skrStaked: Long = 0,
    val missions: Int = 0,
    val totalFunded: Long = 0,
    val totalPaid: Long = 0,
    val exists: Boolean = true,
)

@Serializable
data class Completion(
    val address: String,
    val mission: String,
    val worker: String,
    val proofHash: String,
    val confidence: Int,
    val answer: Int,
    val amount: Long,
    val approvedAt: Long,
    val missionTitle: String? = null,
    val missionCategory: Int? = null,
)

@Serializable
data class Check(val name: String, val pass: Boolean, val detail: String = "")

@Serializable
data class VerifyResult(
    /** approved | rejected | error */
    val status: String,
    val signature: String? = null,
    val amount: Long? = null,
    val confidence: Int? = null,
    val checks: List<Check> = emptyList(),
    val reason: String? = null,
    val answer: String? = null,
    val proofHash: String? = null,
)

@Serializable
data class ReserveResult(val ok: Boolean, val expiresAt: Long, val signature: String? = null)

@Serializable
data class AuthResponse(val token: String, val wallet: String)

@Serializable
data class SeekerResult(val verified: Boolean, val sgtMint: String? = null, val signature: String? = null, val reason: String? = null)

@Serializable
data class BuiltTx(val transaction: String, val missionAddress: String? = null, val summary: String = "")

@Serializable
data class LeaderboardEntry(val wallet: String, val approved: Int, val score: Int, val streak: Int, val seekerVerified: Boolean)

@Serializable
data class MissionStats(
    val mission: Mission,
    val completions: List<Completion>,
    val yes: Int = 0,
    val no: Int = 0,
    val unknown: Int = 0,
)

@Serializable
data class CreateMissionRequest(
    val creator: String,
    val title: String,
    val instructions: String,
    val question: String,
    val category: Int,
    val proofKind: Int,
    val reward: Long,
    val slots: Int,
    val lat: Double,
    val lon: Double,
    val radiusM: Int,
    val deadline: Long,
    val requiresSeeker: Boolean,
    val minScore: Int,
)

@Serializable
data class StakeRequest(val wallet: String, val amount: Long, val role: String, val unstake: Boolean = false)

@Serializable
data class SiwsRequest(
    val wallet: String,
    val message: String,
    val signature: String,
)

@Serializable
data class ApiError(val detail: String)
