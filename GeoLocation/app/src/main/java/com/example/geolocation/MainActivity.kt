package com.example.geolocation

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geolocation.ui.theme.GeoLocationTheme
import org.json.JSONObject
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
        try { wsServer?.stop() } catch (_: Exception) {}
    }

    /**
     * Generate a random coordinate inside the geofence (within radiusMeters of center).
     */
    private fun generateInsideLocation(): Pair<Double, Double> {
        val r = radiusMeters * 0.8 * Random.nextDouble() // 0-80% of radius to stay well inside
        val angle = Random.nextDouble() * 2 * Math.PI
        val dLat = (r * cos(angle)) / 111320.0
        val dLng = (r * sin(angle)) / (111320.0 * cos(Math.toRadians(centerLat)))
        return Pair(centerLat + dLat, centerLng + dLng)
    }

    /**
     * Generate a random coordinate outside the geofence (beyond radiusMeters from center).
     */
    private fun generateOutsideLocation(): Pair<Double, Double> {
        val r = radiusMeters + 50 + Random.nextDouble() * 300 // 250m-550m from center
        val angle = Random.nextDouble() * 2 * Math.PI
        val dLat = (r * cos(angle)) / 111320.0
        val dLng = (r * sin(angle)) / (111320.0 * cos(Math.toRadians(centerLat)))
        return Pair(centerLat + dLat, centerLng + dLng)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GeoFencingServerApp() {
        var serverPort by remember { mutableStateOf("8080") }
        var isServerRunning by remember { mutableStateOf(false) }
        var connectedClient by remember { mutableStateOf<String?>(null) }
        var logMessages by remember { mutableStateOf(listOf<String>()) }
        var lastSentLocation by remember { mutableStateOf("") }
        val localIp = remember {
            SimpleWebSocketServer.getLocalIpAddress() ?: "Unknown"
        }
        val deviceName = remember { "${Build.MANUFACTURER} ${Build.MODEL}" }

        fun addLog(message: String) {
            logMessages = (listOf(message) + logMessages).take(20)
        }

        fun startServer() {
            if (isServerRunning) return
            val port = serverPort.toIntOrNull() ?: 8080
            wsServer = SimpleWebSocketServer(
                port = port,
                onMessage = { msg -> addLog("Recv: $msg") },
                onClientConnected = { addr ->
                    connectedClient = addr
                    addLog("TV connected: $addr")
                },
                onClientDisconnected = { addr ->
                    connectedClient = null
                    addLog("TV disconnected: $addr")
                }
            )
            wsServer?.isReuseAddr = true
            wsServer?.start()
            isServerRunning = true
            addLog("Server started on $localIp:$port")
        }

        fun stopServer() {
            try { wsServer?.stop() } catch (_: Exception) {}
            wsServer = null
            isServerRunning = false
            connectedClient = null
            addLog("Server stopped")
        }

        fun sendLocation(inside: Boolean) {
            val (lat, lng) = if (inside) generateInsideLocation() else generateOutsideLocation()
            val msg = JSONObject().apply {
                put("type", "location")
                put("deviceId", "android-${Build.SERIAL.takeLast(6)}")
                put("deviceName", deviceName)
                put("lat", lat)
                put("lng", lng)
            }
            wsServer?.broadcastToTV(msg.toString())
            val label = if (inside) "INSIDE" else "OUTSIDE"
            lastSentLocation = "$label: (${String.format("%.6f", lat)}, ${String.format("%.6f", lng)})"
            addLog("Sent $label location: ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}")
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF1a1a2e)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GeoFencing Android Server",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3498db),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Server Info Card
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
                        Text("Device: $deviceName", color = Color.Gray, fontSize = 12.sp)
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
                        if (connectedClient != null) {
                            Text("TV Connected: $connectedClient", color = Color(0xFF27ae60))
                        } else if (isServerRunning) {
                            Text("Waiting for TV to connect...", color = Color.Yellow)
                            Text(
                                "Enter $localIp and $serverPort in the TV app",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Send Location Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Send Location", color = Color(0xFF3498db), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Center: $centerLat, $centerLng\nRadius: ${radiusMeters.toInt()}m",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { sendLocation(inside = true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27ae60)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = isServerRunning
                            ) {
                                Text("Send Inside", fontSize = 14.sp)
                            }
                            Button(
                                onClick = { sendLocation(inside = false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = isServerRunning
                            ) {
                                Text("Send Outside", fontSize = 14.sp)
                            }
                        }
                        if (lastSentLocation.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Last: $lastSentLocation", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }

                // Log Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 150.dp),
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