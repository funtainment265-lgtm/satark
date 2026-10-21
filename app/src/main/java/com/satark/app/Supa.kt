package com.satark.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object Supa {
    const val URL = "https://gonrjldecsobmytypxsn.supabase.co/rest/v1/"          // jaise 
    const val KEY = "sb_publishable_EX9oAZtR9xEL0orMiflqTA_wA1AIsRo"     // anon public key
    private val http = OkHttpClient()
    private val JT = "application/json".toMediaType()
    lateinit var app: Context
    lateinit var sp: SharedPreferences
    var authed by mutableStateOf(false)

    fun init(c: Context) {
        if (this::sp.isInitialized) return
        app = c.applicationContext; sp = app.getSharedPreferences("satark", 0); authed = loggedIn
    }
    val loggedIn: Boolean get() = sp.getString("at", null) != null
    val token: String? get() = sp.getString("at", null)
    val uid: String get() = sp.getString("uid", "") ?: ""

    private fun errMsg(s: String, code: Int): String = try {
        val o = JSONObject(s)
        listOf("msg", "error_description", "message").map { o.optString(it) }.firstOrNull { it.isNotBlank() } ?: "Error $code"
    } catch (e: Exception) { "Error $code" }

    fun api(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, extra: Map<String, String> = emptyMap()): String {
        if (auth) ensureFresh()
        val b = Request.Builder().url(URL + path).header("apikey", KEY)
            .header("Authorization", "Bearer " + (if (auth) (token ?: KEY) else KEY))
        extra.forEach { (k, v) -> b.header(k, v) }
        b.method(method, if (method == "GET") null else (body?.toString() ?: "{}").toRequestBody(JT))
        http.newCall(b.build()).execute().use { r ->
            val s = r.body?.string() ?: ""
            if (!r.isSuccessful) throw Exception(errMsg(s, r.code))
            return s
        }
    }

    private fun save(j: JSONObject) {
        sp.edit().putString("at", j.getString("access_token")).putString("rt", j.getString("refresh_token"))
            .putString("uid", j.getJSONObject("user").getString("id"))
            .putLong("exp", System.currentTimeMillis() + j.getLong("expires_in") * 1000).apply()
        authed = true
    }
    fun signUp(name: String, phone: String, email: String, pw: String) {
        val j = JSONObject(api("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", pw)
            .put("data", JSONObject().put("name", name).put("phone", phone)), false))
        if (!j.has("access_token")) throw Exception("Supabase me 'Confirm email' OFF karo, phir dobara try karo")
        save(j)
    }
    fun signIn(email: String, pw: String) =
        save(JSONObject(api("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", pw), false)))
    fun ensureFresh() {
        if (!loggedIn || System.currentTimeMillis() < sp.getLong("exp", 0) - 60_000) return
        save(JSONObject(api("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", sp.getString("rt", "")), false)))
    }
    fun logout() { sp.edit().clear().apply(); authed = false }

    private fun prof(o: JSONObject) = Profile(o.getString("id"), o.optString("name"), o.optString("email"),
        o.optString("phone"), o.optString("role", "user"), o.optLong("created_ms"), o.optLong("last_login_ms"))
    fun myProfile(): Profile = prof(JSONArray(api("GET", "/rest/v1/profiles?id=eq.$uid&select=*")).getJSONObject(0))
    fun users(): List<Profile> = JSONArray(api("GET", "/rest/v1/profiles?select=*&order=created_ms.desc")).let { a -> (0 until a.length()).map { prof(a.getJSONObject(it)) } }
    fun touch() { api("PATCH", "/rest/v1/profiles?id=eq.$uid", JSONObject().put("last_login_ms", System.currentTimeMillis()), true, mapOf("Prefer" to "return=minimal")) }
    fun alerts(limit: Int): List<AlertItem> = JSONArray(api("GET", "/rest/v1/alerts?select=*&order=id.desc&limit=$limit")).let { a ->
        (0 until a.length()).map { val o = a.getJSONObject(it); AlertItem(o.getLong("id"), o.getString("type"), o.optString("message"), o.optString("by_name"), o.optLong("ts_ms")) } }
    fun sendAlert(type: String, msg: String, by: String) {
        api("POST", "/rest/v1/alerts", JSONObject().put("type", type).put("message", msg).put("by_name", by), true, mapOf("Prefer" to "return=minimal"))
    }
}
