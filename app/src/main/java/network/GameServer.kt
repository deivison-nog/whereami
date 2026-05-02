package com.info85.whereami.network

import android.util.Log
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class GameServer(private val port: Int = SERVER_PORT) : Thread() {
    private lateinit var serverSocket: ServerSocket
    private lateinit var discoverySocket: DatagramSocket
    private var isRunning = false
    private var messageListener: ((GameMessage) -> Unit)? = null
    private var disconnectListener: (() -> Unit)? = null
    private var discoveryThread: Thread? = null

    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private val gson = Gson()

    private var isOccupied = false
    private var currentClient: Socket? = null

    companion object {
        const val SERVER_PORT = 8888
        const val DISCOVERY_PORT = 8889
        private const val TAG = "GameServer"
    }

    override fun run() {
        try {
            // ✅ CRIAR SERVIDOR COM REUTILIZAÇÃO DE ENDEREÇO
            serverSocket = ServerSocket()
            serverSocket.reuseAddress = true // ✅ PERMITIR REUTILIZAR PORTA
            serverSocket.bind(java.net.InetSocketAddress(port))

            discoverySocket = DatagramSocket(null) // ✅ Criar sem bind
            discoverySocket.reuseAddress = true // ✅ PERMITIR REUTILIZAR PORTA
            discoverySocket.bind(java.net.InetSocketAddress(DISCOVERY_PORT))

            isRunning = true

            Log.d(TAG, "✅ Server started on port $port")
            Log.d(TAG, "✅ Discovery listening on port $DISCOVERY_PORT")

            discoveryThread = thread {
                while (isRunning) {
                    try {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        discoverySocket.receive(packet)

                        val message = String(packet.data, 0, packet.length)
                        Log.d(TAG, "📡 Received discovery request: $message")

                        if (message == "DISCOVER_SERVER") {
                            if (!isOccupied) {
                                val response = "SERVER_FOUND".toByteArray()
                                val responsePacket = DatagramPacket(
                                    response,
                                    response.size,
                                    packet.address,
                                    packet.port
                                )
                                discoverySocket.send(responsePacket)
                                Log.d(TAG, "✅ Responded to discovery (server available)")
                            } else {
                                Log.d(TAG, "⚠️ Ignored discovery (server occupied)")
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Discovery error: ${e.message}")
                        }
                    }
                }
            }

            while (isRunning) {
                try {
                    Log.d(TAG, "⏳ Waiting for client connection...")
                    val clientSocket = serverSocket.accept()

                    if (isOccupied) {
                        Log.w(TAG, "❌ Server already occupied, rejecting connection from ${clientSocket.inetAddress.hostAddress}")
                        clientSocket.close()
                        continue
                    }

                    Log.d(TAG, "✅ Client connected: ${clientSocket.inetAddress.hostAddress}")
                    isOccupied = true
                    currentClient = clientSocket
                    handleClient(clientSocket)

                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting client: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Server error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            writer = PrintWriter(clientSocket.getOutputStream(), true)

            Log.d(TAG, "📖 Reading messages from client...")

            var line: String? = null
            while (clientSocket.isConnected && reader?.readLine().also { line = it } != null) {
                line?.let { json ->
                    Log.d(TAG, "📩 Received: $json")
                    try {
                        val baseMessage = gson.fromJson(json, GameMessage.BaseMessage::class.java)

                        val message = when (baseMessage.type) {
                            "CLIENT_READY" -> gson.fromJson(json, GameMessage.ClientReady::class.java)
                            "SQUARE_SELECTED" -> gson.fromJson(json, GameMessage.SquareSelected::class.java)
                            "GUESS_RESULT" -> gson.fromJson(json, GameMessage.GuessResult::class.java)
                            "EMOJI_SYNC" -> gson.fromJson(json, GameMessage.EmojiSync::class.java)
                            "GAME_OVER" -> gson.fromJson(json, GameMessage.GameOver::class.java)
                            "PLAYER_DISCONNECTED" -> gson.fromJson(json, GameMessage.PlayerDisconnected::class.java)
                            else -> {
                                Log.w(TAG, "Unknown message type: ${baseMessage.type}")
                                null
                            }
                        }

                        message?.let { messageListener?.invoke(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}")
        } finally {
            Log.d(TAG, "🔌 Client disconnected")

            isOccupied = false
            currentClient = null

            disconnectListener?.invoke()

            try {
                reader?.close()
                writer?.close()
                clientSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client connection: ${e.message}")
            }
        }
    }

    fun sendMessage(message: GameMessage) {
        try {
            val json = gson.toJson(message)
            Log.d(TAG, "📤 Sending: $json")
            writer?.println(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
        }
    }

    fun setMessageListener(listener: (GameMessage) -> Unit) {
        this.messageListener = listener
    }

    fun setDisconnectListener(listener: () -> Unit) {
        this.disconnectListener = listener
    }

    fun shutdown() {
        Log.d(TAG, "🛑 Shutting down server...")
        isRunning = false

        try {
            // Closing currentClient unblocks readLine() in handleClient() via
            // SocketException, letting that thread's finally block close reader/writer.
            // Do NOT close reader/writer here directly: same lock-based deadlock
            // risk as in GameClient.disconnect().
            currentClient?.close()

            // ✅ Interromper thread de discovery
            discoveryThread?.interrupt()

            // ✅ Fechar sockets
            try {
                discoverySocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing discovery socket: ${e.message}")
            }

            try {
                serverSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing server socket: ${e.message}")
            }

            Log.d(TAG, "✅ Server shutdown complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down: ${e.message}")
            e.printStackTrace()
        }
    }

    fun isOccupied(): Boolean = isOccupied
}