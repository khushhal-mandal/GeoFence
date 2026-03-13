package com.example.geoclient

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geoclient.ui.theme.GeoClientTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val centerLat = 28.5445
    private val centerLng = 77.3340
    private val radiusMeters = 200.0

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val isConnected = mutableStateOf(false)
    private val logMessages = mutableStateOf(listOf<String>())
    private val lastSent = mutableStateOf("")

    private val deviceId: String by lazy {
        "phone-${Build.MANUFACTURER}-${Build.MODEL}".replace(" ", "_")
    }

    private val deviceName: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoClientTheme {
                GeoClientApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectWebSocket()
    }

    private fun addLog(message: String) {
        Log.d("GeoClient", message)
        logMessages.value = (listOf(message) + logMessages.value).take(20)
    }

    private fun generateInsideLocation(): Pair<Double, Double> {
        val distance = radiusMeters * 0.8 * Random.nextDouble()
        val angle = Random.nextDouble() * 2 * Math.PI
        val dLat = (distance * cos(angle)) / 111320.0
        val dLng = (distance * sin(angle)) / (111320.0 * cos(Math.toRadians(centerLat)))
        return Pair(centerLat + dLat, centerLng + dLng)
    }

    private fun generateOutsideLocation(): Pair<Double, Double> {
        val distance = radiusMeters + 50 + Random.nextDouble() * 300
        val angle = Random.nextDouble() * 2 * Math.PI
        val dLat = (distance * cos(angle)) / 111320.0
        val dLng = (distance * sin(angle)) / (111320.0 * cos(Math.toRadians(centerLat)))
        return Pair(centerLat + dLat, centerLng + dLng)
    }

    private fun connectWebSocket(serverIp: String, serverPort: String) {
        if (isConnected.value) return

        val url = "ws://$serverIp:$serverPort"
        addLog("Connecting to $url...")

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.value = true
                addLog("Connected to server")

                val registerMsg = JSONObject().apply {
                    put("type", "register")
                    put("clientType", "phone")
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                }
                webSocket.send(registerMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                addLog("Server: $text")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.value = false
                addLog("Disconnected from server")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.value = false
                addLog("Connection failed: ${t.message}")
            }
        })
    }

    private fun disconnectWebSocket() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        isConnected.value = false
    }

    private fun sendLocation(inside: Boolean) {
        val (lat, lng) = if (inside) {
            generateInsideLocation()
        } else {
            generateOutsideLocation()
        }

        val msg = JSONObject().apply {
            put("type", "location")
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("lat", lat)
            put("lng", lng)
        }

        webSocket?.send(msg.toString())
        val label = if (inside) "INSIDE" else "OUTSIDE"
        lastSent.value = "$label: (${String.format("%.6f", lat)}, ${String.format("%.6f", lng)})"
        addLog("Sent $label: ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GeoClientApp() {
        var serverIp by remember { mutableStateOf("") }
        var serverPort by remember { mutableStateOf("8080") }
        val connected by isConnected
        val logs by logMessages
        val last by lastSent

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF1A1A2E)
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
                    text = "GeoFence Client",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ECDC4),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = deviceName,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server Connection", color = Color(0xFF4ECDC4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = serverIp,
                            onValueChange = { serverIp = it },
                            label = { Text("Server IP Address") },
                            placeholder = { Text("e.g. 192.168.1.5") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFF4ECDC4),
                                unfocusedLabelColor = Color.Gray
                            ),
                            enabled = !connected
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFF4ECDC4),
                                unfocusedLabelColor = Color.Gray
                            ),
                            enabled = !connected
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (connected) disconnectWebSocket() else connectWebSocket(serverIp, serverPort)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (connected) Color(0xFFE74C3C) else Color(0xFF27AE60)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = connected || serverIp.isNotBlank()
                        ) {
                            Text(if (connected) "Disconnect" else "Connect to Server")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (connected) "Connected" else "Disconnected",
                            color = if (connected) Color(0xFF27AE60) else Color(0xFFE74C3C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Send Location", color = Color(0xFF4ECDC4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Center: $centerLat, $centerLng\nRadius: ${radiusMeters.toInt()}m",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { sendLocation(inside = true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = connected
                            ) {
                                Text("Send Inside", fontSize = 14.sp)
                            }
                            Button(
                                onClick = { sendLocation(inside = false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = connected
                            ) {
                                Text("Send Outside", fontSize = 14.sp)
                            }
                        }
                        if (last.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Last: $last", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 150.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Log", color = Color(0xFF4ECDC4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        logs.forEach { msg ->
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

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMinUpdateIntervalMillis(3000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                _currentLat.value = location.latitude
                _currentLng.value = location.longitude
                _isLocationEnabled.value = true
                // Auto-send location if connected
                if (_isConnected.value) {
                    sendCurrentLocation()
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        _isLocationEnabled.value = true
        addLog("Location tracking started")
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        _isLocationEnabled.value = false
    }

    // ========== WebSocket ==========

    private fun connectWebSocket(serverIp: String, serverPort: String) {
        if (_isConnected.value) return
        val url = "ws://$serverIp:$serverPort"
        addLog("Connecting to $url...")

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                addLog("Connected to server!")

                // Register as a phone client
                val registerMsg = JSONObject().apply {
                    put("type", "register")
                    put("clientType", "phone")
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                }
                webSocket.send(registerMsg.toString())

                // Send current location if available
                if (_currentLat.value != null) {
                    sendCurrentLocation()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                addLog("Server: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                addLog("Disconnected from server")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                addLog("Connection failed: ${t.message}")
            }
        })
    }

    private fun disconnectWebSocket() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _isConnected.value = false
    }

    private fun sendCurrentLocation() {
        val lat = _currentLat.value ?: return
        val lng = _currentLng.value ?: return
        val msg = JSONObject().apply {
            put("type", "location")
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("lat", lat)
            put("lng", lng)
        }
        webSocket?.send(msg.toString())
        addLog("Sent: (${String.format("%.6f", lat)}, ${String.format("%.6f", lng)})")
    }

    // ========== UI ==========

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GeoClientApp() {
        var serverIp by remember { mutableStateOf("") }
        var serverPort by remember { mutableStateOf("8080") }
        val isConnected by _isConnected
        val isLocationEnabled by _isLocationEnabled
        val currentLat by _currentLat
        val currentLng by _currentLng
        val logMessages by _logMessages

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
                    text = "GeoFence Client",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ECDC4),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = deviceName,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Connection Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server Connection", color = Color(0xFF4ECDC4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = serverIp,
                            onValueChange = { serverIp = it },
                            label = { Text("Server IP Address") },
                            placeholder = { Text("e.g. 192.168.1.5") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFF4ECDC4),
                                unfocusedLabelColor = Color.Gray
                            ),
                            enabled = !isConnected
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFF4ECDC4),
                                unfocusedLabelColor = Color.Gray
                            ),
                            enabled = !isConnected
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (isConnected) disconnectWebSocket()
                                else connectWebSocket(serverIp, serverPort)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) Color(0xFFe74c3c) else Color(0xFF27ae60)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = isConnected || serverIp.isNotBlank()
                        ) {
                            Text(if (isConnected) "Disconnect" else "Connect to Server")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isConnected) "Connected" else "Disconnected",
                            color = if (isConnected) Color(0xFF27ae60) else Color(0xFFe74c3c),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Location Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("GPS Location", color = Color(0xFF4ECDC4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (currentLat != null && currentLng != null) {
                            Text(
                                "Lat: ${String.format("%.6f", currentLat)}",
                                color = Color.White
                            )
                            Text(
                                "Lng: ${String.format("%.6f", currentLng)}",
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (isConnected) "Auto-sending every 5s"
                                else "Connect to server to send",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        } else {
                            Text("Waiting for GPS fix...", color = Color.Yellow)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { requestLocationPermission() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLocationEnabled) Color(0xFF45B7D1) else Color(0xFF3498db)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (isLocationEnabled) "Location Tracking Active"
                                else "Enable Location"
                            )
                        }
                        if (isConnected && currentLat != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { sendCurrentLocation() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9b59b6)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Send Location Now")
                            }
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
                        Text("Log", color = Color(0xFF4ECDC4), fontWeight = FontWeight.Bold)
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
