package app.legwork.core

import java.text.NumberFormat
import java.util.Locale

object Format {
    private val usd: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /** USDC base units (6 decimals) to "$4.00". */
    fun usdc(micro: Long): String = "$" + usd.format(micro / 1_000_000.0)

    /** Short "$4" for map pins and cards when the amount is whole. */
    fun usdcShort(micro: Long): String {
        val v = micro / 1_000_000.0
        return if (v == v.toLong().toDouble()) "$${v.toLong()}" else "$" + usd.format(v)
    }

    fun skr(base: Long): String {
        val v = base / 1_000_000.0
        return if (v == v.toLong().toDouble()) "${v.toLong()} SKR" else String.format(Locale.US, "%.2f SKR", v)
    }

    fun shortKey(key: String?): String =
        if (key == null || key.length < 12) key ?: "" else key.take(4) + "…" + key.takeLast(4)

    fun relativeDeadline(deadline: Long): String {
        val s = deadline - System.currentTimeMillis() / 1000
        return when {
            s <= 0 -> "Expired"
            s < 3600 -> "${s / 60} min left"
            s < 86_400 -> "${s / 3600} h left"
            else -> "${s / 86_400} d left"
        }
    }
}
