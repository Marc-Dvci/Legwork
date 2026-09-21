package app.legwork.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Client for the Legwork verifier service. The verifier reads the mission board from the
 * Solana program, verifies proofs, and signs the payout and reservation transactions.
 */
class LegworkApi(
    @Volatile var baseUrl: String,
    private val tokenProvider: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url(path))
        tokenProvider()?.let { b.header("Authorization", "Bearer $it") }
        execute(b.build())
    }

    private suspend inline fun <reified T, reified B> post(path: String, body: B): T = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url(path)).post(json.encodeToString(body).toRequestBody(jsonType))
        tokenProvider()?.let { b.header("Authorization", "Bearer $it") }
        execute(b.build())
    }

    private inline fun <reified T> execute(req: Request): T {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val detail = runCatching { json.decodeFromString<ApiError>(text).detail }.getOrNull()
                throw ApiException(resp.code, detail ?: "Verifier returned ${resp.code}")
            }
            return json.decodeFromString(text)
        }
    }

    suspend fun config(): AppConfig = get("/config")

    suspend fun signIn(req: SiwsRequest): AuthResponse = post("/auth/siws", req)

    suspend fun missions(lat: Double?, lon: Double?, radiusKm: Double = 25.0, wallet: String? = null): List<Mission> {
        val q = buildString {
            append("/missions?radius_km=$radiusKm")
            if (lat != null && lon != null) append("&lat=$lat&lon=$lon")
            if (wallet != null) append("&wallet=$wallet")
        }
        return get(q)
    }

    suspend fun mission(address: String, lat: Double?, lon: Double?): Mission {
        val q = if (lat != null && lon != null) "?lat=$lat&lon=$lon" else ""
        return get("/missions/$address$q")
    }

    suspend fun reserve(address: String): ReserveResult = post("/missions/$address/reserve", mapOf<String, String>())

    suspend fun submitProof(
        address: String,
        photo: ByteArray,
        lat: Double,
        lon: Double,
        accuracy: Float,
        timestamp: Long,
        mockLocation: Boolean,
        deviceModel: String,
    ): VerifyResult = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("lat", lat.toString())
            .addFormDataPart("lon", lon.toString())
            .addFormDataPart("accuracy", accuracy.toString())
            .addFormDataPart("timestamp", timestamp.toString())
            .addFormDataPart("mock_location", mockLocation.toString())
            .addFormDataPart("device", deviceModel)
            .addFormDataPart("photo", "proof.jpg", photo.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val b = Request.Builder().url(url("/missions/$address/submit")).post(body)
        tokenProvider()?.let { b.header("Authorization", "Bearer $it") }
        execute(b.build())
    }

    suspend fun profile(wallet: String): WorkerProfile = get("/workers/$wallet")

    suspend fun completions(wallet: String): List<Completion> = get("/workers/$wallet/completions")

    suspend fun verifySeeker(): SeekerResult = post("/workers/verify-seeker", mapOf<String, String>())

    suspend fun leaderboard(): List<LeaderboardEntry> = get("/leaderboard")

    suspend fun buildCreateMission(req: CreateMissionRequest): BuiltTx = post("/tx/create-mission", req)

    suspend fun buildStake(req: StakeRequest): BuiltTx = post("/tx/stake", req)

    suspend fun creatorProfile(wallet: String): CreatorProfile = get("/creators/$wallet")

    suspend fun creatorMissions(wallet: String): List<MissionStats> = get("/creators/$wallet/missions")

    suspend fun confirm(signature: String): Map<String, Boolean> = get("/tx/$signature/confirm")
}
