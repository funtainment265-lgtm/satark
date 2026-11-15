package com.satark.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

// Phone GPS se ek baar location leta hai (Google services nahi chahiye)
@SuppressLint("MissingPermission")
fun fetchLocation(ctx: Context, cb: (Location?) -> Unit) {
    val lm = ctx.getSystemService(LocationManager::class.java)
    val provs = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { try { lm.isProviderEnabled(it) } catch (e: Exception) { false } }
    if (provs.isEmpty()) { cb(null); return }
    var done = false
    val l = object : LocationListener {
        override fun onLocationChanged(loc: Location) { if (!done) { done = true; lm.removeUpdates(this); cb(loc) } }
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }
    provs.forEach { lm.requestLocationUpdates(it, 0L, 0f, l, Looper.getMainLooper()) }
    Handler(Looper.getMainLooper()).postDelayed({
        if (!done) { done = true; lm.removeUpdates(l); cb(provs.firstNotNullOfOrNull { lm.getLastKnownLocation(it) }) }
    }, 20000)
}
