package com.ethran.notable.i18n

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Runs in its own process (`:restart`, see the manifest) so it survives the main process being killed: it ends the
 * main process, then starts the app again in a fresh one. Used after a language change.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch: Intent? = intent.getParcelableExtra(EXTRA_LAUNCH)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0) Process.killProcess(mainPid)
        // Wait until the old process is gone, or the new activity would be started inside it and die with it.
        Handler(Looper.getMainLooper()).postDelayed({
            launch?.let { startActivity(it) }
            finish()
            Process.killProcess(Process.myPid())
        }, 500)
    }

    companion object {
        const val EXTRA_LAUNCH = "launch"
        const val EXTRA_MAIN_PID = "main_pid"
    }
}
