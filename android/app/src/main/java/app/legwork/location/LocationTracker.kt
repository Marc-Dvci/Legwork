package app.legwork.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val timeMs: Long,
    val mock: Boolean,
)

fun Location.toFix(): Fix = Fix(
    latitude, longitude, accuracy, time,
    if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider,
)

/** Precise location only while a mission is on screen; nothing runs in the background. */
class LocationTracker(private val context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): Fix? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        runCatching { Tasks.await(fused.lastLocation)?.toFix() }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): Fix? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        runCatching {
            Tasks.await(fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null))?.toFix()
        }.getOrNull() ?: lastKnown()
    }

    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 2_000): Flow<Fix> = callbackFlow {
        if (!hasPermission()) { close(); return@callbackFlow }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1_000)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }
        fused.requestLocationUpdates(request, cb, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(cb) }
    }
}
