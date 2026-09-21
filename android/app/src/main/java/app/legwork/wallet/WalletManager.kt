package app.legwork.wallet

import android.net.Uri
import android.util.Base64
import app.legwork.core.Config
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana

sealed class WalletOutcome<out T> {
    data class Ok<T>(val value: T) : WalletOutcome<T>()
    data object NoWallet : WalletOutcome<Nothing>()
    data class Failed(val message: String) : WalletOutcome<Nothing>()
}

data class SignInProof(
    val wallet: String,
    val label: String?,
    val mwaToken: String?,
    /** The exact SIWS message bytes the wallet signed, base64. */
    val message: String,
    /** Detached ed25519 signature, base58. */
    val signature: String,
)

/**
 * Thin wrapper over the Mobile Wallet Adapter client. The app never sees a private key:
 * authorization, Sign-in-with-Solana and every transaction signature go through the wallet app.
 */
class WalletManager {
    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse(Config.IDENTITY_URI),
            iconUri = Uri.parse(Config.IDENTITY_ICON),
            identityName = Config.IDENTITY_NAME,
        )
    )

    fun configure(cluster: String, authToken: String?) {
        adapter.blockchain = when (cluster) {
            "mainnet-beta", "mainnet" -> Solana.Mainnet
            "testnet" -> Solana.Testnet
            else -> Solana.Devnet
        }
        adapter.authToken = authToken
    }

    val authToken: String? get() = adapter.authToken

    /** Authorize and sign a SIWS message in one wallet round-trip. */
    suspend fun signIn(sender: ActivityResultSender): WalletOutcome<SignInProof> {
        val payload = SignInWithSolana.Payload(
            Uri.parse(Config.IDENTITY_URI).host,
            null as ByteArray?,
            Config.SIWS_STATEMENT,
            Uri.parse(Config.IDENTITY_URI),
            "1",
            null,
            nonce(),
            java.time.Instant.now().toString(),
            null, null, null, null,
        )
        return when (val r = adapter.signIn(sender, payload)) {
            is TransactionResult.Success -> {
                val siws = r.payload
                val account = r.authResult.accounts.firstOrNull()
                WalletOutcome.Ok(
                    SignInProof(
                        wallet = Base58.encodeToString(siws.publicKey),
                        label = account?.accountLabel,
                        mwaToken = r.authResult.authToken,
                        message = Base64.encodeToString(siws.signedMessage, Base64.NO_WRAP),
                        signature = Base58.encodeToString(siws.signature),
                    )
                )
            }
            is TransactionResult.NoWalletFound -> WalletOutcome.NoWallet
            is TransactionResult.Failure -> WalletOutcome.Failed(r.message)
        }
    }

    /** Sign one serialized transaction in the wallet; the app submits it to the cluster itself. */
    suspend fun sign(sender: ActivityResultSender, transaction: ByteArray): WalletOutcome<ByteArray> {
        return when (val r = adapter.transact(sender) {
            signTransactions(arrayOf(transaction))
        }) {
            is TransactionResult.Success -> {
                val signed = r.payload.signedPayloads.firstOrNull()
                if (signed == null) WalletOutcome.Failed("Wallet returned no signed transaction")
                else WalletOutcome.Ok(signed)
            }
            is TransactionResult.NoWalletFound -> WalletOutcome.NoWallet
            is TransactionResult.Failure -> WalletOutcome.Failed(r.message)
        }
    }

    suspend fun disconnect(sender: ActivityResultSender) {
        runCatching { adapter.disconnect(sender) }
        adapter.authToken = null
    }

    private fun nonce(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        return (1..12).map { chars.random() }.joinToString("")
    }
}
