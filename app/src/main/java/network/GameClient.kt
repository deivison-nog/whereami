package com.info85.whereami.network

import android.util.Log
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

data class AvailableServer(
    val ipAddress: String,
    val hostName: String,
    val responseTime: Long
)

class GameClient {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var messageListener: ((GameMessage) -> Unit)? = null
    private var disconnectListener: (() -> Unit)? = null
    private var isConnected = false
    private val gson = Gson()

    companion object {
        const val SERVER_PORT = 8888
        const val DISCOVERY_PORT = 8889
        private const val TAG = "GameClient"
    }

    // ✅ NOVO: Descobrir TODOS os servidores disponíveis
    fun discoverAllServers(timeoutMs: Long = 5000): List<AvailableServer> {
        val servers = mutableListOf<AvailableServer>()
        val discoverySocket = DatagramSocket()
        discoverySocket.broadcast = true
        discoverySocket.soTimeout = 1000 // Timeout curto para cada recebimento

        try {
            // Enviar broadcast
            val request = "DISCOVER_SERVER".toByteArray()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(request, request.size, broadcastAddress, DISCOVERY_PORT)

            Log.d(TAG, "📡 Sending broadcast to discover servers...")
            discoverySocket.send(packet)

            val startTime = System.currentTimeMillis()
            val foundIps = mutableSetOf<String>() // Evitar duplicatas

            // Receber TODAS as respostas durante o período de timeout
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val buffer = ByteArray(1024)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    discoverySocket.receive(responsePacket)

                    val message = String(responsePacket.data, 0, responsePacket.length)
                    if (message == "SERVER_FOUND") {
                        val serverIp = responsePacket.address.hostAddress ?: continue

                        // Evitar adicionar o mesmo servidor duas vezes
                        if (serverIp in foundIps) {
                            continue
                        }

                        foundIps.add(serverIp)
                        val responseTime = System.currentTimeMillis() - startTime

                        val server = AvailableServer(
                            ipAddress = serverIp,
                            hostName = responsePacket.address.hostName,
                            responseTime = responseTime
                        )

                        servers.add(server)
                        Log.d(TAG, "✅ Found server: $serverIp (${responseTime}ms)")
                    }

                } catch (e: SocketTimeoutException) {
                    // Timeout esperado - continuar tentando receber mais respostas
                    continue
                }
            }

            Log.d(TAG, "🔍 Discovery complete. Found ${servers.size} server(s)")

        } catch (e: Exception) {
            Log.e(TAG, "Discovery error: ${e.message}")
        } finally {
            discoverySocket.close()
        }

        return servers.sortedBy { it.responseTime } // Ordenar por tempo de resposta
    }

    // Método original mantido para compatibilidade
    fun discoverServer(timeoutMs: Long = 5000): String? {
        val servers = discoverAllServers(timeoutMs)
        return servers.firstOrNull()?.ipAddress
    }

    fun connect(serverIp: String): Boolean {
        return try {
            Log.d(TAG, "🔌 Connecting to server: $serverIp:$SERVER_PORT")
            socket = Socket(serverIp, SERVER_PORT)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            writer = PrintWriter(socket!!.getOutputStream(), true)
            isConnected = true

            // Iniciar thread para receber mensagens
            thread {
                try {
                    // ✅ CORRIGIDO: Inicializar a variável line
                    var line: String? = null
                    while (isConnected && reader?.readLine().also { line = it } != null) {
                        line?.let { json ->
                            Log.d(TAG, "📩 Received: $json")
                            try {
                                val baseMessage = gson.fromJson(json, GameMessage.BaseMessage::class.java)
                                val message = when (baseMessage.type) {
                                    "EmojiSync" -> gson.fromJson(json, GameMessage.EmojiSync::class.java)
                                    "SquareSelected" -> gson.fromJson(json, GameMessage.SquareSelected::class.java)
                                    "GuessResult" -> gson.fromJson(json, GameMessage.GuessResult::class.java)
                                    "GameOver" -> gson.fromJson(json, GameMessage.GameOver::class.java)
                                    "PlayerDisconnected" -> gson.fromJson(json, GameMessage.PlayerDisconnected::class.java)
                                    else -> null
                                }

                                message?.let { messageListener?.invoke(it) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing message: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection lost: ${e.message}")
                } finally {
                    Log.d(TAG, "🔌 Disconnected from server")
                    isConnected = false
                    disconnectListener?.invoke()
                }
            }

            Log.d(TAG, "✅ Connected successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            false
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

    fun disconnect() {
        Log.d(TAG, "🛑 Disconnecting...")
        isConnected = false
        try {
            reader?.close()
            writer?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }
}