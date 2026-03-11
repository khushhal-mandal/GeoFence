package com.example.geolocation

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Proper WebSocket server for Android using Java-WebSocket library.
 * Accepts connections from the Tizen TV web app on the same WiFi.
 */
class SimpleWebSocketServer(
    port: Int,
    private val onMessage: (String) -> Unit,
    private val onClientConnected: (String) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {}
) : WebSocketServer(InetSocketAddress(port)) {

    var clientAddress: String? = null
        private set

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val addr = conn.remoteSocketAddress?.address?.hostAddress ?: "unknown"
        clientAddress = addr
        Log.d("WebSocketServer", "Client connected: $addr")
        onClientConnected(addr)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        val addr = conn.remoteSocketAddress?.address?.hostAddress ?: "unknown"
        Log.d("WebSocketServer", "Client disconnected: $addr")
        onClientDisconnected(addr)
        if (connections.isEmpty()) {
            clientAddress = null
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("WebSocketServer", "Received: $message")
        onMessage(message)

        // Handle register messages from TV and send confirmation
        try {
            val json = org.json.JSONObject(message)
            if (json.optString("type") == "register") {
                val response = org.json.JSONObject().apply {
                    put("type", "registered")
                    put("clientId", json.optString("appName", "tv-client"))
                    put("serverTime", System.currentTimeMillis())
                }
                conn.send(response.toString())
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

    /**
     * Broadcast a message to all connected clients.
     */
    fun broadcast(message: String) {
        connections.forEach { conn ->
            if (conn.isOpen) {
                conn.send(message)
            }
        }
    }

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
