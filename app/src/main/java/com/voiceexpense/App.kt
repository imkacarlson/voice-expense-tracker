package com.voiceexpense

import android.app.Application
import android.os.Build
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

@HiltAndroidApp
class App : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Enable StrictMode in debug to surface disk/network on main thread
        if (BuildConfig.DEBUG) {
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
}
