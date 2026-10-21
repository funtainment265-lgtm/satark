package com.satark.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Supa.init(c)
        if (Supa.loggedIn) ContextCompat.startForegroundService(c, Intent(c, AlertService::class.java))
    }
}
