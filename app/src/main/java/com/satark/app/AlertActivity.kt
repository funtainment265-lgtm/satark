package com.satark.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlertActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val msg = intent.getStringExtra("message") ?: "Alert!"
        setContent {
            val k by rememberInfiniteTransition(label = "f")
                .animateFloat(0.5f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "k")
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFD7121F).copy(alpha = k), Color(0xFF2A040A))))
                    .padding(28.dp),
                Arrangement.Center, Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Warning, null, Modifier.size(100.dp), Color.White)
                Spacer(Modifier.height(16.dp))
                Text("SATARK ALERT", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Text(msg, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(44.dp))
                Button(
                    onClick = { startService(Intent(this@AlertActivity, AlertService::class.java).setAction("STOP_SIREN")); finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("SIREN BAND KARO", color = Color(0xFFB0121F), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
