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
    const val URL = "https://gonrjldecsobmytypxsn.supabase.co"
    const val KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdvbnJqbGRlY3NvYm15dHlweHNuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI1OTEwMDcsImV4cCI6MjEwODE2NzAwN30.nS576EOwmFOmUQRVjPunTv4vB5g_AlDSi_NpBENw9vM"     // <-- yahan apni anon key (eyJ...) daalo
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

    private fun rows(s: String): List<JSONObject> = JSONArray(s).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun dbl(o: JSONObject, k: String): Double? = if (o.isNull(k)) null else o.getDouble(k)
    private fun prof(o: JSONObject) = Profile(o.getString("id"), o.optString("name"), o.optString("email"), o.optString("phone"),
        o.optString("role", "user"), o.optString("status", "pending"), o.optString("task"), o.optLong("created_ms"), o.optLong("last_login_ms"),
        dbl(o, "lat"), dbl(o, "lng"), o.optLong("loc_ms"), o.optBoolean("p_alert"), o.optBoolean("p_groups"),
        o.optBoolean("p_approve"), o.optBoolean("p_users"), o.optBoolean("p_map"))

    fun myProfile(): Profile = prof(rows(api("GET", "/rest/v1/profiles?id=eq.$uid&select=*"))[0])
    fun users(): List<Profile> = rows(api("GET", "/rest/v1/profiles?select=*&order=created_ms.desc")).map { prof(it) }
    fun touch() { api("PATCH", "/rest/v1/profiles?id=eq.$uid", JSONObject().put("last_login_ms", System.currentTimeMillis()), true, mapOf("Prefer" to "return=minimal")) }

    private fun rpc(fn: String, o: JSONObject) { api("POST", "/rest/v1/rpc/$fn", o) }
    fun setStatus(t: String, s: String) = rpc("set_status", JSONObject().put("target", t).put("new_status", s))
    fun setAccess(t: String, role: String, a: Boolean, g: Boolean, ap: Boolean, u: Boolean, m: Boolean) =
        rpc("set_access", JSONObject().put("target", t).put("new_role", role).put("a", a).put("g", g).put("ap", ap).put("u", u).put("m", m))
    fun setTask(t: String) = rpc("set_my_task", JSONObject().put("t", t))
    fun setLocation(la: Double, ln: Double) = rpc("set_my_location", JSONObject().put("la", la).put("ln", ln))
    fun clearLocation() = rpc("clear_my_location", JSONObject())

    fun groups(): List<Group> = rows(api("GET", "/rest/v1/user_groups?select=*&order=name")).map { Group(it.getLong("id"), it.optString("name")) }
    fun createGroup(n: String) { api("POST", "/rest/v1/user_groups", JSONObject().put("name", n), true, mapOf("Prefer" to "return=minimal")) }
    fun deleteGroup(id: Long) { api("DELETE", "/rest/v1/user_groups?id=eq.$id") }
    fun members(gid: Long): Set<String> = rows(api("GET", "/rest/v1/group_members?group_id=eq.$gid&select=user_id")).map { it.getString("user_id") }.toSet()
    fun addMember(gid: Long, u: String) { api("POST", "/rest/v1/group_members", JSONObject().put("group_id", gid).put("user_id", u), true, mapOf("Prefer" to "return=minimal")) }
    fun removeMember(gid: Long, u: String) { api("DELETE", "/rest/v1/group_members?group_id=eq.$gid&user_id=eq.$u") }
    fun isMember(gid: Long): Boolean = rows(api("GET", "/rest/v1/group_members?group_id=eq.$gid&user_id=eq.$uid&select=user_id")).isNotEmpty()

    fun alerts(limit: Int): List<AlertItem> = rows(api("GET", "/rest/v1/alerts?select=*&order=id.desc&limit=$limit")).map {
        AlertItem(it.getLong("id"), it.getString("type"), it.optString("message"), it.optString("by_name"), it.optLong("ts_ms"),
            if (it.isNull("group_id")) null else it.getLong("group_id"), it.optString("group_name")) }
    fun sendAlert(type: String, msg: String, by: String, groupId: Long?, groupName: String) {
        val o = JSONObject().put("type", type).put("message", msg).put("by_name", by).put("group_name", groupName)
        if (groupId != null) o.put("group_id", groupId)
        api("POST", "/rest/v1/alerts", o, true, mapOf("Prefer" to "return=minimal"))
    }
}
