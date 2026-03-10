package com.example.geolocation

import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Simple WebSocket server for Android (for demo/testing only).
 * Accepts one client (TV web app) at a time.
 * Only handles basic text frames (not production-ready).
 */
class SimpleWebSocketServer(private val port: Int, private val onMessage: (String) -> Unit) {
    private var serverThread: Thread? = null
    private var running = false
    var clientAddress: String? = null

    fun start() {
        running = true
        serverThread = thread {
            try {
                val serverSocket = ServerSocket(port)
                Log.d("WebSocketServer", "Listening on port $port")
                while (running) {
                    val socket = serverSocket.accept()
                    clientAddress = socket.inetAddress.hostAddress
                    Log.d("WebSocketServer", "Client connected: $clientAddress")
                    handleClient(socket)
                }
                serverSocket.close()
            } catch (e: Exception) {
                Log.e("WebSocketServer", "Error: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        serverThread?.interrupt()
        serverThread = null
    }

    private fun handleClient(socket: Socket) {
        thread {
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                // Minimal handshake (not full RFC)
                val reader = input.bufferedReader()
                val request = reader.readLine()
                if (request.contains("Upgrade: websocket")) {
                    // Accept handshake (simplified)
                    output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n".toByteArray())
                }
                // Read text frames (very basic)
                while (running) {
                    val line = reader.readLine() ?: break
                    onMessage(line)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("WebSocketServer", "Client error: ${e.message}")
            }
        }
    }

    companion object {
        fun getLocalIpAddress(): String? {
            return try {
                val inetAddress = InetAddress.getLocalHost()
                inetAddress.hostAddress
            } catch (e: Exception) {
                null
            }
        }
    }
}
