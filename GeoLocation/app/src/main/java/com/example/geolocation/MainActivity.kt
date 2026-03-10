package com.example.geolocation

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geolocation.ui.theme.GeoLocationTheme
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    
    // Samsung R&D Institute, Noida Sector 126 coordinates
    private val centerLat = 28.5445
    private val centerLng = 77.3340
    private val radiusMeters = 200.0
    private var wsServer: SimpleWebSocketServer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoLocationTheme {
                GeoFencingServerApp()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wsServer?.stop()
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GeoFencingServerApp() {
        var serverPort by remember { mutableStateOf("8080") }
        var isServerRunning by remember { mutableStateOf(false) }
        var clientAddress by remember { mutableStateOf("") }
        var logMessages by remember { mutableStateOf(listOf<String>()) }
        val localIp = SimpleWebSocketServer.getLocalIpAddress() ?: "Unknown"

        fun addLog(message: String) {
            logMessages = (listOf(message) + logMessages).take(10)
        }

        fun startServer() {
            if (isServerRunning) return
            val port = serverPort.toIntOrNull() ?: 8080
            wsServer = SimpleWebSocketServer(port) { msg ->
                addLog("Received: $msg")
            }
            wsServer?.start()
            isServerRunning = true
            addLog("Server started on $localIp:$port")
        }

        fun stopServer() {
            wsServer?.stop()
            wsServer = null
            isServerRunning = false
            addLog("Server stopped")
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF1a1a2e)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GeoFencing Android Server",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3498db),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server Info", color = Color(0xFF3498db), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("IP Address: $localIp", color = Color.White)
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3498db),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFF3498db),
                                unfocusedLabelColor = Color.Gray
                            ),
                            enabled = !isServerRunning
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { if (isServerRunning) stopServer() else startServer() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServerRunning) Color(0xFFe74c3c) else Color(0xFF27ae60)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isServerRunning) "Stop Server" else "Start Server")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (wsServer?.clientAddress != null) {
                            Text("Client Connected: ${wsServer?.clientAddress}", color = Color(0xFF27ae60))
                        }
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Log", color = Color(0xFF3498db), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        logMessages.forEach { msg ->
                            Text(
                                text = msg,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}