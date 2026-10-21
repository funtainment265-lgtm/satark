@file:OptIn(ExperimentalMaterial3Api::class)
package com.satark.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Red = Color(0xFFFF3B47); val Navy = Color(0xFF0B1020); val Card = Color(0xFF151B33)
val Soft = Color(0xFF9AA4C7); val Green = Color(0xFF4ADE80)
val Bg = Brush.verticalGradient(listOf(Color(0xFF1C2147), Navy))
private val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
fun tm(ms: Long) = if (ms == 0L) "—" else fmt.format(Date(ms))

data class Profile(val uid: String = "", val name: String = "", val email: String = "", val phone: String = "",
                   val role: String = "user", val createdAt: Long = 0, val lastLogin: Long = 0)
data class AlertItem(val id: Long, val type: String, val message: String, val by: String, val ts: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Supa.init(this)
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName))
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, surface = Card, background = Navy)) { Root() }
        }
    }
}

// ---------- small UI helpers ----------
@Composable fun T(s: String, size: Int = 14, color: Color = Color.White, bold: Boolean = false, mod: Modifier = Modifier) =
    Text(s, mod, color, size.sp, fontWeight = if (bold) FontWeight.Bold else null)

@Composable fun Glass(mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(mod.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card)
        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp)).padding(16.dp), content = content)

@Composable fun Logo(size: Dp = 96.dp) {
    val s by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(size * 1.6f).scale(s).background(Brush.radialGradient(listOf(Red.copy(alpha = .35f), Color.Transparent)), CircleShape), Alignment.Center) {
        Icon(Icons.Filled.Shield, null, Modifier.size(size), Red)
    }
}

@Composable fun Field(v: String, on: (String) -> Unit, label: String, icon: ImageVector, kb: KeyboardType = KeyboardType.Text, pw: Boolean = false) =
    OutlinedTextField(v, on, Modifier.fillMaxWidth(), label = { Text(label) }, leadingIcon = { Icon(icon, null) },
        singleLine = true, shape = RoundedCornerShape(16.dp),
        visualTransformation = if (pw) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, focusedLabelColor = Red, cursorColor = Red))

@Composable fun Primary(text: String, busy: Boolean = false, on: () -> Unit) =
    Button(on, Modifier.fillMaxWidth().height(54.dp), enabled = !busy, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Red)) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), Color.White, 2.dp) else T(text, 16, bold = true)
    }

fun logout() { Supa.app.stopService(Intent(Supa.app, AlertService::class.java)); Supa.logout() }

@Composable fun rememberAlerts(limit: Int): List<AlertItem> {
    var l by remember { mutableStateOf(listOf<AlertItem>()) }
    LaunchedEffect(Unit) {
        while (true) {
            try { l = withContext(Dispatchers.IO) { Supa.alerts(limit) } } catch (e: Exception) {}
            delay(4000)
        }
    }
    return l
}

// ---------- root / routing ----------
@Composable fun Root() {
    val ctx = LocalContext.current
    val authed = Supa.authed
    var splash by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { delay(1500); splash = false }
    LaunchedEffect(authed) {
        profile = null
        if (!authed) return@LaunchedEffect
        ContextCompat.startForegroundService(ctx, Intent(ctx, AlertService::class.java))
        var tries = 0
        while (profile == null && tries < 6) {
            try { profile = withContext(Dispatchers.IO) { Supa.myProfile() } } catch (e: Exception) { tries++; delay(1500) }
        }
        if (profile == null) { logout(); return@LaunchedEffect }
        try { withContext(Dispatchers.IO) { Supa.touch() } } catch (e: Exception) {}
    }
    val p = profile
    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            splash -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Logo(110.dp); T("SATARK", 36, bold = true); T("Alert. Aware. Safe.", 14, Soft)
            }
            !authed -> AuthScreen()
            p == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Red) }
            p.role == "admin" -> AdminHome(p)
            else -> UserHome(p)
        }
    }
}

// ---------- login / sign up ----------
@Composable fun AuthScreen() {
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    fun go() {
        err = ""
        if (email.isBlank() || (signup && name.isBlank())) { err = "Saari details bharo"; return }
        if (pass.length < 6) { err = "Password kam se kam 6 characters ka ho"; return }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (signup) Supa.signUp(name.trim(), phone.trim(), email.trim(), pass) else Supa.signIn(email.trim(), pass) }
            } catch (e: Exception) { err = e.message ?: "Error"; busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp)); Logo(80.dp)
        T("SATARK", 30, bold = true)
        T(if (signup) "Naya account banao" else "Wapas swagat hai", 15, Soft)
        Glass(verticalArrangementFix = true) {
            if (signup) { Field(name, { name = it }, "Poora naam", Icons.Filled.Person); Spacer(Modifier.height(12.dp))
                Field(phone, { phone = it }, "Phone", Icons.Filled.Phone, KeyboardType.Phone); Spacer(Modifier.height(12.dp)) }
            Field(email, { email = it }, "Email", Icons.Filled.Email, KeyboardType.Email); Spacer(Modifier.height(12.dp))
            Field(pass, { pass = it }, "Password", Icons.Filled.Lock, KeyboardType.Password, true)
            if (err.isNotEmpty()) { Spacer(Modifier.height(10.dp)); T(err, 13, Red) }
            Spacer(Modifier.height(18.dp))
            Primary(if (signup) "SIGN UP" else "LOGIN", busy) { go() }
        }
        TextButton({ signup = !signup; err = "" }) { T(if (signup) "Account hai? Login karo" else "Naye ho? Sign up karo", 14, Red, true) }
    }
}
@Composable fun Glass(verticalArrangementFix: Boolean, content: @Composable ColumnScope.() -> Unit) = Glass(Modifier, content)

// ---------- user ----------
@Composable fun Info(icon: ImageVector, label: String, value: String) =
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Soft); Spacer(Modifier.width(12.dp))
        Column { T(label, 12, Soft); T(value.ifBlank { "—" }, 15) }
    }

@Composable fun UserHome(p: Profile) {
    val last = rememberAlerts(10).firstOrNull { it.type == "start" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp)); Logo(100.dp)
        T("Namaste, ${p.name}", 24, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(8.dp))
            T("Aap surakshit hain • Alerts ON", 14, Green)
        }
        Glass { T("LATEST ALERT", 12, Soft, true); Spacer(Modifier.height(6.dp))
            T(last?.message ?: "Abhi tak koi alert nahi aaya", 17, bold = true)
            if (last != null) T(tm(last.ts), 12, Soft) }
        Glass { Info(Icons.Filled.Email, "Email", p.email); Info(Icons.Filled.Phone, "Phone", p.phone) }
        OutlinedButton({ logout() }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); T("Logout", 15)
        }
    }
}

// ---------- admin ----------
@Composable fun AdminHome(p: Profile) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(containerColor = Color.Transparent, bottomBar = {
        NavigationBar(containerColor = Card) {
            listOf(Icons.Filled.Campaign to "Alert", Icons.Filled.Groups to "Users", Icons.Filled.History to "History")
                .forEachIndexed { i, (ic, lb) ->
                    NavigationBarItem(tab == i, { tab = i }, { Icon(ic, null) }, label = { Text(lb) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Red,
                            selectedTextColor = Red, unselectedIconColor = Soft, unselectedTextColor = Soft))
                }
        }
    }) { pad -> Box(Modifier.padding(pad)) { when (tab) { 0 -> AlertTab(p); 1 -> UsersTab(); else -> HistoryTab() } } }
}

@Composable fun AlertTab(p: Profile) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("Emergency! Please follow instructions.") }
    var confirm by remember { mutableStateOf(false) }; var sent by remember { mutableStateOf("") }
    val glow by rememberInfiniteTransition(label = "b").animateFloat(0.93f, 1.05f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "g")
    fun send(type: String, m: String) {
        scope.launch {
            sent = try {
                withContext(Dispatchers.IO) { Supa.sendAlert(type, m, p.name) }
                if (type == "start") "Alert bhej diya gaya ✓" else "Siren band kiya gaya ✓"
            } catch (e: Exception) { "Fail: ${e.message}" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        Arrangement.spacedBy(18.dp), Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("Control Room", 26, bold = true); T("${p.name} • Admin", 13, Soft) }
            IconButton({ logout() }) { Icon(Icons.Filled.Logout, null, tint = Soft) }
        }
        Glass {
            T("ALERT MESSAGE (edit karo)", 12, Soft, true); Spacer(Modifier.height(8.dp))
            OutlinedTextField(msg, { msg = it }, Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Emergency! Evacuate now.", "Fire alert! Move to exit.", "Test alert - please ignore.").forEach {
                    AssistChip(onClick = { msg = it }, label = { Text(it, maxLines = 1) })
                }
            }
        }
        Box(Modifier.size(190.dp).scale(glow).shadow(24.dp, CircleShape, ambientColor = Red, spotColor = Red).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFF7A7A), Red, Color(0xFFB0121F))))
            .clickable(enabled = msg.isNotBlank()) { confirm = true }, Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(56.dp), Color.White)
                T("SEND ALERT", 18, bold = true)
            }
        }
        OutlinedButton({ send("stop", "") }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            T("Siren band karo (sabke liye)", 15)
        }
        if (sent.isNotEmpty()) T(sent, 14, Green)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Card,
        title = { T("Sabko alert bhejein?", 20, bold = true) },
        text = { T("Sabhi users ke phone par siren bajega:\n\n\"$msg\"", 15, Soft) },
        confirmButton = { TextButton({ confirm = false; send("start", msg.trim()) }) { T("HAAN, BHEJO", 14, Red, true) } },
        dismissButton = { TextButton({ confirm = false }) { T("Cancel", 14, Soft) } })
}

@Composable fun UsersTab() {
    var users by remember { mutableStateOf(listOf<Profile>()) }; var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            try { users = withContext(Dispatchers.IO) { Supa.users() } } catch (e: Exception) {}
            delay(5000)
        }
    }
    val list = users.filter { q.isBlank() || it.name.contains(q, true) || it.email.contains(q, true) || it.phone.contains(q) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp)); T("Users (${users.size})", 26, bold = true); Spacer(Modifier.height(10.dp))
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Naam, email ya phone search karo") },
            leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red))
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list) { u ->
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Red.copy(alpha = .2f)), Alignment.Center) {
                            T(u.name.take(1).uppercase(), 18, Red, true)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(u.name, 16, bold = true); T(u.email, 13, Soft)
                            if (u.phone.isNotBlank()) T(u.phone, 13, Soft)
                            T("Joined ${tm(u.createdAt)} • Last login ${tm(u.lastLogin)}", 11, Soft)
                        }
                        if (u.role == "admin") T("ADMIN", 11, Red, true)
                    }
                }
            }
        }
    }
}

@Composable fun HistoryTab() {
    val all = rememberAlerts(50)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp)); T("Alert History", 26, bold = true); Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(all) { a ->
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (a.type == "start") Icons.Filled.Warning else Icons.Filled.CheckCircle, null,
                            tint = if (a.type == "start") Red else Green)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            T(if (a.type == "start") a.message else "Siren band kiya gaya", 15, bold = true)
                            T("${a.by} • ${tm(a.ts)}", 12, Soft)
                        }
                    }
                }
            }
        }
    }
}
