package com.example.geolocation

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket server that relays location messages between client phones and the TV app.
 * Tracks which connections are "tv" or "phone" clients.
 */
class SimpleWebSocketServer(
    port: Int,
    private val onMessage: (String) -> Unit,
    private val onClientConnected: (String) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {}
) : WebSocketServer(InetSocketAddress(port)) {

    var clientAddress: String? = null
        private set

    // Track connection types: conn -> "tv" | "phone"
    private val clientTypes = ConcurrentHashMap<WebSocket, String>()
    // Track phone client info: conn -> JSONObject with deviceId, deviceName, last lat/lng
    private val phoneClients = ConcurrentHashMap<WebSocket, JSONObject>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val addr = conn.remoteSocketAddress?.address?.hostAddress ?: "unknown"
        clientAddress = addr
        Log.d("WebSocketServer", "Client connected: $addr")
        onClientConnected(addr)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        val addr = conn.remoteSocketAddress?.address?.hostAddress ?: "unknown"
        Log.d("WebSocketServer", "Client disconnected: $addr")

        // If a phone client disconnects, notify TV with last known location
        val clientType = clientTypes[conn]
        if (clientType == "phone") {
            val info = phoneClients[conn]
            if (info != null) {
                val disconnectMsg = JSONObject().apply {
                    put("type", "deviceDisconnected")
                    put("deviceId", info.optString("deviceId"))
                    put("deviceName", info.optString("deviceName"))
                    put("lastLat", info.optDouble("lat", 0.0))
                    put("lastLng", info.optDouble("lng", 0.0))
                }
                sendToTV(disconnectMsg.toString())
            }
            phoneClients.remove(conn)
        }
        clientTypes.remove(conn)

        onClientDisconnected(addr)
        if (connections.isEmpty()) {
            clientAddress = null
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("WebSocketServer", "Received: $message")
        onMessage(message)

        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "register" -> {
                    val cType = json.optString("clientType", "tv")
                    clientTypes[conn] = cType
                    val response = JSONObject().apply {
                        put("type", "registered")
                        put("clientId", json.optString("appName", json.optString("deviceId", "client")))
                        put("serverTime", System.currentTimeMillis())
                    }
                    conn.send(response.toString())

                    // If a TV just connected, send it all known phone locations
                    if (cType == "tv") {
                        phoneClients.values.forEach { info ->
                            val locMsg = JSONObject().apply {
                                put("type", "location")
                                put("deviceId", info.optString("deviceId"))
                                put("deviceName", info.optString("deviceName"))
                                put("lat", info.optDouble("lat", 0.0))
                                put("lng", info.optDouble("lng", 0.0))
                            }
                            conn.send(locMsg.toString())
                        }
                    }
                }
                "location" -> {
                    // Could come from a phone client OR from the server's own buttons
                    // Track this phone client's info
                    if (clientTypes[conn] == "phone") {
                        phoneClients[conn] = json
                    }
                    // Forward location to all TV clients
                    sendToTV(message)
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocketServer", "Error handling message: ${e.message}")
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("WebSocketServer", "Error: ${ex.message}")
    }

    override fun onStart() {
        Log.d("WebSocketServer", "Server started on port $port")
    }

    /** Send a message only to TV connections. */
    private fun sendToTV(message: String) {
        connections.forEach { conn ->
            if (conn.isOpen && clientTypes[conn] == "tv") {
                conn.send(message)
            }
        }
    }

    /** Broadcast a message to all TV connections (used by server's own Send buttons). */
    fun broadcastToTV(message: String) {
        sendToTV(message)
    }

    /** Broadcast a message to ALL connected clients. */
    fun broadcast(message: String) {
        connections.forEach { conn ->
            if (conn.isOpen) {
                conn.send(message)
            }
        }
    }

    /** Get count of connected phone clients. */
    fun getPhoneClientCount(): Int = phoneClients.size

    /** Get count of connected TV clients. */
    fun getTVClientCount(): Int = clientTypes.values.count { it == "tv" }

    companion object {
        fun getLocalIpAddress(): String? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}
