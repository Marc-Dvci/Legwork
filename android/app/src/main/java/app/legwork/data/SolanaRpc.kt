package app.legwork.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Reads the app needs straight from the cluster: token balances and the Seeker Genesis Token.
 * Kept deliberately small; everything that needs a signature goes through the wallet.
 */
class SolanaRpc(@Volatile var rpcUrl: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val TOKEN_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
        const val SGT_MINT_AUTHORITY = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"
        const val SGT_GROUP = "GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te"
    }

    private suspend fun call(method: String, params: JsonArray): JsonElement = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", 1); put("method", method); put("params", params)
        }.toString()
        val req = Request.Builder().url(rpcUrl)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw IOException("Empty RPC response")
            val obj = json.parseToJsonElement(text).jsonObject
            obj["error"]?.let { throw IOException("RPC error: $it") }
            obj["result"] ?: throw IOException("RPC returned no result")
        }
    }

    suspend fun solBalance(wallet: String): Long =
        call("getBalance", buildJsonArray { add(json.parseToJsonElement("\"$wallet\"")) })
            .jsonObject["value"]!!.jsonPrimitive.content.toLong()

    /** Sum of all token accounts for [mint] owned by [wallet], in base units. */
    suspend fun tokenBalance(wallet: String, mint: String): Long {
        if (mint.isBlank()) return 0
        val params = buildJsonArray {
            add(json.parseToJsonElement("\"$wallet\""))
            add(buildJsonObject { put("mint", mint) })
            add(buildJsonObject { put("encoding", "jsonParsed") })
        }
        val value = call("getTokenAccountsByOwner", params).jsonObject["value"]?.jsonArray ?: return 0
        return value.sumOf { acc ->
            acc.jsonObject["account"]!!.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject["info"]!!
                .jsonObject["tokenAmount"]!!.jsonObject["amount"]!!.jsonPrimitive.content.toLong()
        }
    }

    /**
     * Returns the Seeker Genesis Token mint held by [wallet], or null. Follows the Solana Mobile
     * verification rule: a Token-2022 mint with the SGT mint authority, the SGT metadata pointer
     * and membership of the SGT token group, held with a non-zero balance.
     */
    suspend fun findSeekerGenesisToken(wallet: String): String? {
        val params = buildJsonArray {
            add(json.parseToJsonElement("\"$wallet\""))
            add(buildJsonObject { put("programId", TOKEN_2022) })
            add(buildJsonObject { put("encoding", "jsonParsed") })
        }
        val accounts = call("getTokenAccountsByOwner", params).jsonObject["value"]?.jsonArray ?: return null
        val candidateMints = accounts.mapNotNull { acc ->
            val info = acc.jsonObject["account"]!!.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject["info"]!!.jsonObject
            val amount = info["tokenAmount"]!!.jsonObject["amount"]!!.jsonPrimitive.content.toLong()
            if (amount > 0) info["mint"]!!.jsonPrimitive.content else null
        }
        for (mint in candidateMints) {
            if (isSeekerGenesisMint(mint)) return mint
        }
        return null
    }

    private suspend fun isSeekerGenesisMint(mint: String): Boolean {
        val params = buildJsonArray {
            add(json.parseToJsonElement("\"$mint\""))
            add(buildJsonObject { put("encoding", "jsonParsed") })
        }
        val value = call("getAccountInfo", params).jsonObject["value"] ?: return false
        if (value !is JsonObject) return false
        val info = value["data"]?.jsonObject?.get("parsed")?.jsonObject?.get("info")?.jsonObject ?: return false
        val mintAuthority = info["mintAuthority"]?.jsonPrimitive?.content
        if (mintAuthority != SGT_MINT_AUTHORITY) return false
        val extensions = info["extensions"]?.jsonArray ?: return false
        var metadataOk = false
        var groupOk = false
        for (ext in extensions) {
            val e = ext.jsonObject
            val state = e["state"]?.jsonObject ?: continue
            when (e["extension"]?.jsonPrimitive?.content) {
                "metadataPointer" -> metadataOk =
                    state["authority"]?.jsonPrimitive?.content == SGT_GROUP &&
                        state["metadataAddress"]?.jsonPrimitive?.content == SGT_GROUP
                "tokenGroupMember" -> groupOk = state["group"]?.jsonPrimitive?.content == SGT_GROUP
            }
        }
        return metadataOk && groupOk
    }

    /** Submits a signed transaction; returns the base58 signature. */
    suspend fun sendTransaction(signed: ByteArray): String {
        val b64 = android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP)
        val params = buildJsonArray {
            add(json.parseToJsonElement("\"$b64\""))
            add(buildJsonObject { put("encoding", "base64"); put("preflightCommitment", "confirmed"); put("maxRetries", 5) })
        }
        return call("sendTransaction", params).jsonPrimitive.content
    }

    suspend fun signatureStatus(signature: String): String? {
        val params = buildJsonArray {
            add(buildJsonArray { add(json.parseToJsonElement("\"$signature\"")) })
            add(buildJsonObject { put("searchTransactionHistory", true) })
        }
        val value = call("getSignatureStatuses", params).jsonObject["value"]?.jsonArray ?: return null
        val first = value.firstOrNull() ?: return null
        if (first !is JsonObject) return null
        return first["confirmationStatus"]?.jsonPrimitive?.content
    }
}
