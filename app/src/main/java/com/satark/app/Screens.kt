@file:OptIn(ExperimentalMaterial3Api::class)
package com.satark.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val TASKS = listOf("QRT", "Sentry", "Patrol", "Routine Task", "Duty JCO", "Duty NCO", "Duty Officer")

suspend fun <T> io(f: () -> T): T = withContext(Dispatchers.IO) { f() }

@Composable fun <T> rememberPoll(init: T, ms: Long, load: () -> T): Pair<T, () -> Unit> {
    var v by remember { mutableStateOf(init) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { while (true) { try { v = io { load() } } catch (e: Exception) {}; delay(ms) } }
    val refresh: () -> Unit = { tick += 1 }
    return v to refresh
}

@Composable fun Info(icon: ImageVector, label: String, value: String) =
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Soft); Spacer(Modifier.width(12.dp))
        Column { T(label, 12, Soft); T(value.ifBlank { "—" }, 15) }
    }

@Composable fun Dropdown(label: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            T(value.ifBlank { label }, 15, if (value.isBlank()) Soft else Color.White)
            Spacer(Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem({ T(o, 15, Color.White) }, { open = false; onPick(i) }) }
        }
    }
}

// ---------- approval pending / rejected ----------
@Composable fun PendingScreen(p: Profile) {
    val rej = p.status == "rejected"
    Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Logo(90.dp); Spacer(Modifier.height(16.dp))
        T(if (rej) "Registration reject ho gayi" else "Approval ka intezaar", 22, bold = true)
        Spacer(Modifier.height(8.dp))
        T(if (rej) "Admin se sampark karo." else "Aapki request admin ko bhej di gayi hai. Approve hote hi app khul jayegi.", 15, Soft)
        Spacer(Modifier.height(24.dp)); if (!rej) CircularProgressIndicator(color = Red)
        Spacer(Modifier.height(24.dp)); OutlinedButton({ logout() }) { T("Logout", 15) }
    }
}

// ---------- user ----------
@Composable fun UserHome(p: Profile) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val last = rememberAlerts(10).firstOrNull { it.type == "start" }
    var task by remember(p.task) { mutableStateOf(p.task) }
    var msg by remember { mutableStateOf("") }
    var locMs by remember(p.locMs) { mutableStateOf(p.locMs) }
    fun share() {
        msg = "Location dhoondh raha hoon…"
        fetchLocation(ctx) { loc ->
            if (loc == null) msg = "Location nahi mili. GPS on karo aur dobara try karo."
            else scope.launch {
                msg = try { io { Supa.setLocation(loc.latitude, loc.longitude) }; locMs = System.currentTimeMillis(); "Location admin ko bhej di ✓" }
                      catch (e: Exception) { "Fail: ${e.message}" }
            }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) share() else msg = "Location permission nahi mili"
    }
    val opts = listOf("Koi task nahi") + TASKS
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp)); Logo(80.dp)
        T("Namaste, ${p.name}", 24, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(8.dp))
            T("Aap surakshit hain • Alerts ON", 14, Green)
        }
        Glass {
            T("MERA TASK (admin ko dikhega)", 12, Soft, true); Spacer(Modifier.height(8.dp))
            Dropdown("Apna task chuno", task, opts) { i ->
                val t = if (i == 0) "" else TASKS[i - 1]; task = t
                scope.launch { try { io { Supa.setTask(t) } } catch (e: Exception) { msg = "Fail: ${e.message}" } }
            }
        }
        Glass {
            T("MERI LOCATION (optional)", 12, Soft, true); Spacer(Modifier.height(6.dp))
            T(if (locMs > 0) "Aakhri update: ${tm(locMs)}" else "Abhi share nahi ki", 14, Soft); Spacer(Modifier.height(10.dp))
            Primary("📍 Location share karo") {
                val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (ok) share() else perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            if (locMs > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton({ scope.launch { msg = try { io { Supa.clearLocation() }; locMs = 0; "Location hata di" } catch (e: Exception) { "Fail: ${e.message}" } } },
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { T("Location share band karo", 14) }
            }
        }
        if (msg.isNotEmpty()) T(msg, 13, Soft)
        Glass {
            T("LATEST ALERT", 12, Soft, true); Spacer(Modifier.height(6.dp))
            T(last?.message ?: "Abhi tak koi alert nahi aaya", 17, bold = true)
            if (last != null) T(tm(last.ts), 12, Soft)
        }
        Glass { Info(Icons.Filled.Email, "Email", p.email); Info(Icons.Filled.Phone, "Phone", p.phone) }
        OutlinedButton({ logout() }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); T("Logout", 15)
        }
    }
}

// ---------- admin / super admin ----------
@Composable fun StaffHome(p: Profile) {
    val pending = rememberPoll(0, 8000) { if (p.can(p.pApprove)) Supa.users().count { it.status == "pending" } else 0 }.first
    val tabs = buildList<String> {
        if (p.can(p.pAlert)) add("Alert")
        if (p.can(p.pUsers)) add("Users")
        if (p.can(p.pApprove)) add("Approvals")
        if (p.can(p.pGroups)) add("Groups")
        if (p.can(p.pMap)) add("Map")
        if (p.can(p.pAlert)) add("History")
        if (p.isSuper) add("Admins")
    }
    var tab by remember { mutableStateOf(0) }
    val idx = if (tabs.isEmpty()) 0 else tab.coerceIn(0, tabs.size - 1)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T(if (p.isSuper) "Super Admin" else "Admin", 22, bold = true); T(p.name, 13, Soft) }
            IconButton({ logout() }) { Icon(Icons.Filled.Logout, null, tint = Soft) }
        }
        if (tabs.isEmpty()) {
            T("Abhi aapko koi access nahi mila. Super admin se sampark karo.", 15, Soft, mod = Modifier.padding(24.dp))
        } else {
            ScrollableTabRow(selectedTabIndex = idx, containerColor = Color.Transparent, contentColor = Red, edgePadding = 12.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = i == idx, onClick = { tab = i },
                        text = { T(if (t == "Approvals" && pending > 0) "Approvals ($pending)" else t, 14, if (i == idx) Red else Soft, true) })
                }
            }
            Box(Modifier.weight(1f)) {
                when (tabs[idx]) {
                    "Alert" -> AlertTab(p); "Users" -> UsersTab(); "Approvals" -> ApprovalsTab()
                    "Groups" -> GroupsTab(); "Map" -> MapTab(); "History" -> HistoryTab(); else -> AdminsTab()
                }
            }
        }
    }
}

@Composable fun AlertTab(p: Profile) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("Emergency! Please follow instructions.") }
    var confirm by remember { mutableStateOf(false) }; var sent by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(-1) }
    val (groups, _) = rememberPoll(listOf<Group>(), 10000) { Supa.groups() }
    val target = groups.getOrNull(sel)
    val who = target?.name ?: "Sabhi users"
    val glow by rememberInfiniteTransition(label = "b").animateFloat(0.93f, 1.05f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "g")
    fun send(type: String, m: String) {
        scope.launch {
            sent = try {
                io { Supa.sendAlert(type, m, p.name, target?.id, target?.name ?: "") }
                if (type == "start") "Alert bhej diya gaya ✓ ($who)" else "Siren band kiya gaya ✓ ($who)"
            } catch (e: Exception) { "Fail: ${e.message}" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
        Glass {
            T("KISKO BHEJNA HAI", 12, Soft, true); Spacer(Modifier.height(8.dp))
            Dropdown("Sabhi users", who, listOf("Sabhi users") + groups.map { it.name }) { i -> sel = i - 1 }
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
        Box(Modifier.size(170.dp).scale(glow).shadow(24.dp, CircleShape, ambientColor = Red, spotColor = Red).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFF7A7A), Red, Color(0xFFB0121F))))
            .clickable(enabled = msg.isNotBlank()) { confirm = true }, Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(52.dp), Color.White); T("SEND ALERT", 17, bold = true)
            }
        }
        OutlinedButton({ send("stop", "") }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            T("Siren band karo ($who)", 15)
        }
        if (sent.isNotEmpty()) T(sent, 14, Green)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Card,
        title = { T("$who ko alert bhejein?", 20, bold = true) },
        text = { T("In sabke phone par siren bajega:\n\n\"$msg\"", 15, Soft) },
        confirmButton = { TextButton({ confirm = false; send("start", msg.trim()) }) { T("HAAN, BHEJO", 14, Red, true) } },
        dismissButton = { TextButton({ confirm = false }) { T("Cancel", 14, Soft) } })
}

@Composable fun UsersTab() {
    val (users, _) = rememberPoll(listOf<Profile>(), 5000) { Supa.users() }
    var q by remember { mutableStateOf("") }
    val list = users.filter { it.status == "approved" && (q.isBlank() || it.name.contains(q, true) || it.email.contains(q, true) || it.phone.contains(q) || it.task.contains(q, true)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp)); T("Users (${list.size})", 26, bold = true); Spacer(Modifier.height(10.dp))
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Naam, email, phone ya task search karo") },
            leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red))
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list) { u ->
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Red.copy(alpha = .2f)), Alignment.Center) { T(u.name.take(1).uppercase(), 18, Red, true) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(u.name, 16, bold = true); T(u.email, 13, Soft)
                            if (u.phone.isNotBlank()) T(u.phone, 13, Soft)
                            T("Task: ${u.task.ifBlank { "—" }}", 14, if (u.task.isBlank()) Soft else Green, true)
                            if (u.lat != null) T("📍 Location: ${tm(u.locMs)}", 12, Soft)
                            T("Last login ${tm(u.lastLogin)}", 11, Soft)
                        }
                        if (u.isStaff) T(if (u.isSuper) "SUPER" else "ADMIN", 11, Red, true)
                    }
                }
            }
        }
    }
}

@Composable fun ApprovalsTab() {
    val scope = rememberCoroutineScope(); var note by remember { mutableStateOf("") }
    val (users, reload) = rememberPoll(listOf<Profile>(), 5000) { Supa.users() }
    val list = users.filter { it.status != "approved" }.sortedBy { it.status != "pending" }
    fun act(u: Profile, s: String) {
        scope.launch { note = try { io { Supa.setStatus(u.uid, s) }; reload(); "" } catch (e: Exception) { e.message ?: "Error" } }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp)); T("Approval requests (${list.size})", 24, bold = true)
        if (note.isNotEmpty()) T(note, 13, Red)
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) T("Koi pending request nahi.", 15, Soft)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list) { u ->
                Glass {
                    T(u.name, 17, bold = true); T(u.email, 13, Soft); if (u.phone.isNotBlank()) T(u.phone, 13, Soft)
                    T(if (u.status == "pending") "Status: pending" else "Status: rejected", 12, if (u.status == "pending") Soft else Red)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ act(u, "approved") }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E9E5A))) { T("Approve", 14, bold = true) }
                        if (u.status == "pending") OutlinedButton({ act(u, "rejected") }, Modifier.weight(1f)) { T("Reject", 14, Red) }
                    }
                }
            }
        }
    }
}

@Composable fun GroupsTab() {
    val scope = rememberCoroutineScope()
    val (groups, reload) = rememberPoll(listOf<Group>(), 8000) { Supa.groups() }
    var name by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var manage by remember { mutableStateOf<Group?>(null) }; var del by remember { mutableStateOf<Group?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp)); T("Groups (${groups.size})", 26, bold = true); Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, Modifier.weight(1f), placeholder = { Text("Naya group (jaise Alpha Team)") },
                singleLine = true, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, cursorColor = Red))
            Spacer(Modifier.width(8.dp))
            Button({ if (name.isNotBlank()) scope.launch { note = try { io { Supa.createGroup(name.trim()) }; name = ""; reload(); "" } catch (e: Exception) { e.message ?: "Error" } } },
                colors = ButtonDefaults.buttonColors(containerColor = Red)) { T("Add", 14, bold = true) }
        }
        if (note.isNotEmpty()) T(note, 13, Red)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(groups) { g ->
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, null, tint = Red); Spacer(Modifier.width(10.dp))
                        T(g.name, 16, bold = true, mod = Modifier.weight(1f))
                        TextButton({ manage = g }) { T("Members", 14, Red, true) }
                        IconButton({ del = g }) { Icon(Icons.Filled.Delete, null, tint = Soft) }
                    }
                }
            }
        }
    }
    manage?.let { g -> MembersDialog(g) { manage = null } }
    del?.let { g ->
        AlertDialog(onDismissRequest = { del = null }, containerColor = Card,
            title = { T("\"${g.name}\" delete karein?", 18, bold = true) },
            confirmButton = { TextButton({ del = null; scope.launch { try { io { Supa.deleteGroup(g.id) }; reload() } catch (e: Exception) { note = e.message ?: "Error" } } }) { T("Delete", 14, Red, true) } },
            dismissButton = { TextButton({ del = null }) { T("Cancel", 14, Soft) } })
    }
}

@Composable fun MembersDialog(g: Group, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf(listOf<Profile>()) }
    var mem by remember { mutableStateOf(setOf<String>()) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(g.id) {
        try { users = io { Supa.users() }.filter { it.status == "approved" }; mem = io { Supa.members(g.id) } }
        catch (e: Exception) { err = e.message ?: "Error" }
    }
    AlertDialog(onDismissRequest = onClose, containerColor = Card,
        title = { T("${g.name} • members (${mem.size})", 18, bold = true) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (err.isNotEmpty()) T(err, 13, Red)
                users.forEach { u ->
                    val on = u.uid in mem
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            try { if (on) io { Supa.removeMember(g.id, u.uid) } else io { Supa.addMember(g.id, u.uid) }; mem = if (on) mem - u.uid else mem + u.uid }
                            catch (e: Exception) { err = e.message ?: "Error" }
                        }
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = Red)); Spacer(Modifier.width(8.dp))
                        Column { T(u.name, 15); T(u.task.ifBlank { u.email }, 12, Soft) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { T("Done", 14, Red, true) } })
}

@Composable fun PermRow(label: String, on: Boolean, change: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, 14, mod = Modifier.weight(1f)); Switch(on, change, colors = SwitchDefaults.colors(checkedTrackColor = Red))
    }

@Composable fun AdminsTab() {
    val scope = rememberCoroutineScope(); var note by remember { mutableStateOf("") }
    val (users, reload) = rememberPoll(listOf<Profile>(), 5000) { Supa.users() }
    val list = users.filter { it.status == "approved" && !it.isSuper }.sortedBy { it.role != "admin" }
    fun save(u: Profile, role: String, a: Boolean, g: Boolean, ap: Boolean, us: Boolean, m: Boolean) {
        scope.launch { note = try { io { Supa.setAccess(u.uid, role, a, g, ap, us, m) }; reload(); "" } catch (e: Exception) { e.message ?: "Error" } }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp)); T("Admins & access rights", 24, bold = true)
        if (note.isNotEmpty()) T(note, 13, Red)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list) { u ->
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { T(u.name, 16, bold = true); T(u.email, 12, Soft) }
                        if (u.role == "admin") OutlinedButton({ save(u, "user", false, false, false, false, false) }) { T("Admin hatao", 13, Red) }
                        else Button({ save(u, "admin", true, true, true, true, true) }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { T("Admin banao", 13, bold = true) }
                    }
                    if (u.role == "admin") {
                        Spacer(Modifier.height(8.dp))
                        PermRow("Alert bhejna", u.pAlert) { save(u, "admin", it, u.pGroups, u.pApprove, u.pUsers, u.pMap) }
                        PermRow("Groups manage karna", u.pGroups) { save(u, "admin", u.pAlert, it, u.pApprove, u.pUsers, u.pMap) }
                        PermRow("Approvals dena", u.pApprove) { save(u, "admin", u.pAlert, u.pGroups, it, u.pUsers, u.pMap) }
                        PermRow("Users + task dekhna", u.pUsers) { save(u, "admin", u.pAlert, u.pGroups, u.pApprove, it, u.pMap) }
                        PermRow("Map dekhna", u.pMap) { save(u, "admin", u.pAlert, u.pGroups, u.pApprove, u.pUsers, it) }
                    }
                }
            }
        }
    }
}

@Composable fun HistoryTab() {
    val all = rememberAlerts(50)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp)); T("Alert History", 26, bold = true); Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(all) { a ->
                Glass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (a.type == "start") Icons.Filled.Warning else Icons.Filled.CheckCircle, null, tint = if (a.type == "start") Red else Green)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            T(if (a.type == "start") a.message else "Siren band kiya gaya", 15, bold = true)
                            T("${a.by} • ${if (a.groupId == null) "Sabhi" else a.groupName.ifBlank { "Group" }} • ${tm(a.ts)}", 12, Soft)
                        }
                    }
                }
            }
        }
    }
}
