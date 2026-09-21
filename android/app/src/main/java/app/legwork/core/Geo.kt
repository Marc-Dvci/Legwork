package app.legwork.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6_371_000.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(m: Double?): String = when {
        m == null -> "—"
        m < 1000 -> "${m.toInt()} m"
        else -> String.format("%.1f km", m / 1000)
    }

    /** Walking time at 80 m/min. */
    fun walkMinutes(m: Double?): Int = if (m == null) 0 else (m / 80).toInt().coerceAtLeast(1)
}
