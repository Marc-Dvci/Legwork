package app.legwork

import android.app.Application
import androidx.work.Configuration
import app.legwork.notify.NearbyWorker
import org.maplibre.android.MapLibre

class LegworkApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        NearbyWorker.ensureChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
