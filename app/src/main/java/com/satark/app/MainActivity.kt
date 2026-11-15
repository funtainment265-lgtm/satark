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

data class Profile(
    val uid: String = "", val name: String = "", val email: String = "", val phone: String = "",
    val role: String = "user", val status: String = "pending", val task: String = "",
    val createdAt: Long = 0, val lastLogin: Long = 0, val lat: Double? = null, val lng: Double? = null, val locMs: Long = 0,
    val pAlert: Boolean = false, val pGroups: Boolean = false, val pApprove: Boolean = false,
    val pUsers: Boolean = false, val pMap: Boolean = false
) {
    val isSuper: Boolean get() = role == "superadmin"
    val isStaff: Boolean get() = role == "admin" || role == "superadmin"
    fun can(f: Boolean): Boolean = isSuper || (role == "admin" && f)
}
data class Group(val id: Long, val name: String)
data class AlertItem(val id: Long, val type: String, val message: String, val by: String, val ts: Long,
                     val groupId: Long? = null, val groupName: String = "")

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
        var tries = 0; var started = false
        while (true) {
            try {
                val first = profile == null
                val np = withContext(Dispatchers.IO) {
                    val x = Supa.myProfile()
                    if (first) { try { Supa.touch() } catch (e: Exception) {} }
                    x
                }
                profile = np; tries = 0
                Supa.sp.edit().putBoolean("canApprove", np.can(np.pApprove)).apply()
                if (np.status == "approved" && !started) {
                    started = true
                    ContextCompat.startForegroundService(ctx, Intent(ctx, AlertService::class.java))
                }
            } catch (e: Exception) {
                tries++
                if (profile == null && tries >= 6) { logout(); return@LaunchedEffect }
            }
            delay(10000)
        }
    }
    val p = profile
    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            splash -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Logo(110.dp); T("SATARK", 36, bold = true); T("Alert. Aware. Safe.", 14, Soft)
            }
            !authed -> AuthScreen()
            p == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Red) }
            p.status != "approved" -> PendingScreen(p)
            p.isStaff -> StaffHome(p)
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

