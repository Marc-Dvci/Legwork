package app.legwork.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.legwork.MainActivity
import app.legwork.core.Format
import app.legwork.core.Geo
import app.legwork.data.LegworkApi
import app.legwork.data.Session
import app.legwork.location.LocationTracker
import java.util.concurrent.TimeUnit

/**
 * Periodic scan for new paid missions near the last known location. Runs on the device on
 * WorkManager's schedule and posts a local notification, so no push token leaves the phone.
 */
class NearbyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val session = Session(ctx)
        val s = session.current()
        if (!s.notifyEnabled) return Result.success()
        val fix = LocationTracker(ctx).lastKnown() ?: return Result.success()
        val api = LegworkApi(s.verifierUrl) { s.jwt }
        val missions = runCatching {
            api.missions(fix.lat, fix.lon, s.notifyRadiusKm.toDouble(), s.wallet)
        }.getOrElse { return Result.retry() }
        val seen = session.notifiedMissions()
        val fresh = missions.filter {
            it.isOpen && !it.completedByMe && it.reward >= s.notifyMinReward && it.address !in seen
        }.sortedBy { it.distanceM ?: Double.MAX_VALUE }
        if (fresh.isEmpty()) return Result.success()

        val top = fresh.first()
        val title = if (fresh.size == 1) "${Format.usdcShort(top.reward)} mission ${Geo.formatDistance(top.distanceM)} away"
        else "${fresh.size} new missions nearby, up to ${Format.usdcShort(fresh.maxOf { it.reward })}"
        notify(ctx, title, top.title, top.address)
        session.markNotified(fresh.map { it.address }.toSet())
        return Result.success()
    }

    companion object {
        const val CHANNEL = "nearby"
        private const val WORK = "legwork-nearby"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NearbyWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Missions near you", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "New paid missions within your radius"
                    }
                )
            }
        }

        fun notify(context: Context, title: String, text: String, missionAddress: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (missionAddress != null) putExtra("mission", missionAddress)
            }
            val pi = PendingIntent.getActivity(
                context, missionAddress.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(missionAddress.hashCode(), n)
        }
    }
}
