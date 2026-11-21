package app.screenbalance

import android.app.Application
import android.content.Context
import app.screenbalance.data.AppUsage
import app.screenbalance.data.ScreenTime
import app.screenbalance.data.SettingsRepository
import app.screenbalance.data.StatsRepository
import app.screenbalance.data.UsageHistory
import app.screenbalance.engine.PolicyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Manual dependency container: the app is small enough that a DI framework adds only weight. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val policy = PolicyEngine()
    val settings = SettingsRepository(context, appScope, policy)
    val stats = StatsRepository(context, Dispatchers.IO)
    val usage = AppUsage(context)
    val screenTime = ScreenTime(context, usage)
    val history = UsageHistory(context, usage, screenTime, stats)
}

class GlanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.stats.pruneOlderThan(LocalDate.now().minusDays(RETENTION_DAYS))
        }
    }

    companion object {
        const val RETENTION_DAYS = 90L
    }
}

val Context.container: AppContainer get() = (applicationContext as GlanceApp).container
