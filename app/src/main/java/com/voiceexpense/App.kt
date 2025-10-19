package com.voiceexpense

import android.app.Application
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.voiceexpense.ai.mediapipe.MediaPipeGenAiClient
import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.performance.AiPerformanceOptimizer
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltAndroidApp
class App : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var mediaPipeClient: MediaPipeGenAiClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val warmInFlight = AtomicBoolean(false)
    @Volatile private var lastWarmElapsedMs: Long = 0L

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            warmGenAi(reason = "process-start")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize parsing module logger
        com.voiceexpense.ai.parsing.logging.Log.setLogger(
            com.voiceexpense.logging.AndroidLogger()
        )

        // Enable StrictMode in debug to surface disk/network on main thread
        if (isAppDebuggable()) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // Lightweight ANR watchdog: logs main thread stack if UI thread is blocked > 5s
        startAnrWatchdog()

        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)

        // Warm AI model availability on app start so parser can use GenAI path
        warmGenAi(reason = "app-start", force = true)
    }

    private fun startAnrWatchdog(timeoutMs: Long = 5000L) {
        val tag = "ANRWatchdog"
        val main = Looper.getMainLooper()
        val handler = android.os.Handler(main)
        var tick = 0
        val ticker = object : Runnable {
            override fun run() {
                tick++
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(ticker)

        Thread({
            var last = -1
            while (true) {
                last = tick
                try { Thread.sleep(timeoutMs) } catch (_: InterruptedException) { }
                if (last == tick) {
                    // Main thread did not advance — dump its stack
                    val st = main.thread.stackTrace.joinToString("\n    ")
                    Log.e(tag, "Main thread appears blocked > ${timeoutMs}ms\n    $st")
                }
            }
        }, "anr-watchdog").apply { isDaemon = true }.start()
    }

    private fun warmGenAi(reason: String, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastWarmElapsedMs < WARM_THROTTLE_MS) {
            Log.d(AI_WARM_TAG, "Skipping warm (throttled) reason=$reason elapsed=${now - lastWarmElapsedMs}ms")
            return
        }
        if (!warmInFlight.compareAndSet(false, true)) {
            Log.d(AI_WARM_TAG, "Warm already running; reason=$reason")
            return
        }
        appScope.launch {
            try {
                val metrics = AiPerformanceOptimizer.warmGenAi(
                    context = applicationContext,
                    mm = modelManager,
                    client = mediaPipeClient
                )
                lastWarmElapsedMs = SystemClock.elapsedRealtime()
                Log.i(
                    AI_WARM_TAG,
                    "reason=$reason status=${metrics.modelStatus.javaClass.simpleName} ensureMs=${metrics.ensureModelMs} clientMs=${metrics.clientInitMs} totalMs=${metrics.totalMs} ready=${metrics.clientReady}"
                )
            } catch (t: Throwable) {
                Log.w(AI_WARM_TAG, "Warm attempt failed for reason=$reason: ${t.message}", t)
            } finally {
                warmInFlight.set(false)
            }
        }
    }

    private fun isAppDebuggable(): Boolean {
        return (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun onTerminate() {
        super.onTerminate()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        appScope.cancel()
    }

    companion object {
        private const val AI_WARM_TAG = "AI.Warm"
        private const val WARM_THROTTLE_MS = 5 * 60 * 1000L
    }
}
