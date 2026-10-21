package com.satark.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AlertService : Service() {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var ws: WebSocket? = null
    private var player: MediaPlayer? = null
    private var worker: Thread? = null
    @Volatile private var running = true

    override fun onCreate() {
        super.onCreate(); Supa.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("listen", "Satark protection", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("siren", "Siren", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i?.action == "STOP_SIREN") { stopSiren(); return START_STICKY }
        val n = NotificationCompat.Builder(this, "listen").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Satark chalu hai").setContentText("Alerts sun raha hai").setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (worker == null) { running = true; worker = thread { loop() } }
        return START_STICKY
    }

    private fun loop() {
        while (running) {
            try {
                if (Supa.loggedIn) {
                    Supa.ensureFresh(); catchUp()
                    val closed = CountDownLatch(1)
                    val req = Request.Builder().url(Supa.URL.replaceFirst("https", "wss") + "/realtime/v1/websocket?apikey=${Supa.KEY}&vsn=1.0.0").build()
                    ws = http.newWebSocket(req, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val join = JSONObject().put("topic", "realtime:satark").put("event", "phx_join").put("ref", "1").put("join_ref", "1")
                                .put("payload", JSONObject().put("access_token", Supa.token).put("config", JSONObject()
                                    .put("postgres_changes", JSONArray().put(JSONObject().put("event", "INSERT").put("schema", "public").put("table", "alerts")))))
                            webSocket.send(join.toString())
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) { try { handle(text) } catch (e: Exception) {} }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.countDown() }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.countDown() }
                    })
                    val end = System.currentTimeMillis() + 50 * 60_000L   // token refresh ke liye 50 min me reconnect
                    var n = 2
                    while (running && closed.count > 0 && System.currentTimeMillis() < end) {
                        closed.await(25, TimeUnit.SECONDS)
                        if (closed.count > 0) ws?.send("""{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"${n++}"}""")
                    }
                    ws?.close(1000, null)
                }
            } catch (e: Exception) { }
            try { Thread.sleep(3000) } catch (e: InterruptedException) { return }
        }
    }

    private fun handle(t: String) {
        val j = JSONObject(t)
        if (j.optString("event") != "postgres_changes") return
        process(j.getJSONObject("payload").getJSONObject("data").getJSONObject("record"))
    }

    // offline/reconnect ke dauran miss hua taza alert (5 min tak purana) bhi bajao
    private fun catchUp() {
        val a = JSONArray(Supa.api("GET", "/rest/v1/alerts?select=*&order=id.desc&limit=1"))
        if (a.length() == 0) return
        val r = a.getJSONObject(0)
        if (!Supa.sp.contains("last")) { Supa.sp.edit().putLong("last", r.getLong("id")).apply(); return }
        if (System.currentTimeMillis() - r.optLong("ts_ms") < 5 * 60_000L) process(r)
    }

    private fun process(r: JSONObject) {
        val id = r.getLong("id")
        if (id <= Supa.sp.getLong("last", 0)) return
        Supa.sp.edit().putLong("last", id).apply()
        if (r.optString("by_id") == Supa.uid) return          // bhejne wale admin ke phone par siren nahi
        if (r.getString("type") == "stop") stopSiren() else ring(r.optString("message", "Alert!"))
    }

    private fun ring(msg: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val full = PendingIntent.getActivity(this, 1, Intent(this, AlertActivity::class.java).putExtra("message", msg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, AlertService::class.java).setAction("STOP_SIREN"), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(2, NotificationCompat.Builder(this, "siren").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 SATARK ALERT").setContentText(msg).setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setFullScreenIntent(full, true).setOngoing(true)
            .addAction(0, "Stop", stop).build())
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        player?.release()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        player = MediaPlayer.create(this, R.raw.siren, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)?.apply { isLooping = true; start() }
    }

    private fun stopSiren() {
        player?.release(); player = null
        getSystemService(NotificationManager::class.java).cancel(2)
    }

    override fun onDestroy() { running = false; ws?.cancel(); stopSiren(); worker?.interrupt(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
