package com.info85.whereami.network

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ServerDiscovery {
    private val discoveryPort = 8889
    private val broadcastMessage = "WHERE_AM_I_SERVER"
    private var broadcastJob: Job? = null
    private var discoveryJob: Job? = null
    private var broadcastSocket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null

    companion object {
        private const val TAG = "ServerDiscovery"
    }

    // Servidor: Anunciar presença na rede
    fun startBroadcasting(serverIp: String) {
        stopBroadcasting() // Garantir que não há broadcast anterior

        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                broadcastSocket = DatagramSocket()
                broadcastSocket?.broadcast = true

                val responseMessage = "WHERE_AM_I_SERVER:$serverIp"
                val sendData = responseMessage.toByteArray()

                Log.d(TAG, "Started broadcasting server at: $serverIp")

                while (isActive) {
                    try {
                        val broadcastAddress = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(
                            sendData,
                            sendData.size,
                            broadcastAddress,
                            discoveryPort
                        )
                        broadcastSocket?.send(packet)
                        Log.d(TAG, "Broadcasting server at: $serverIp")
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error broadcasting: ${e.message}")
                        }
                    }
                    delay(2000) // Broadcast a cada 2 segundos
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcasting: ${e.message}")
            }
        }
    }

    // Cliente: Escutar broadcasts e descobrir servidores
    fun startDiscovery(onServerFound: (String) -> Unit) {
        stopDiscovery() // Garantir que não há discovery anterior

        discoveryJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                discoverySocket = DatagramSocket(discoveryPort)
                discoverySocket?.broadcast = true
                discoverySocket?.soTimeout = 5000 // Timeout de 5 segundos

                Log.d(TAG, "Listening for servers on port $discoveryPort...")

                val receiveData = ByteArray(1024)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(receiveData, receiveData.size)
                        discoverySocket?.receive(packet)

                        val message = String(packet.data, 0, packet.length)
                        Log.d(TAG, "Received broadcast: $message")

                        if (message.startsWith("WHERE_AM_I_SERVER:")) {
                            val serverIp = message.substringAfter("WHERE_AM_I_SERVER:")
                            Log.d(TAG, "Server found at: $serverIp")
                            withContext(Dispatchers.Main) {
                                onServerFound(serverIp)
                            }
                            break // Encontrou servidor, para de procurar
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error receiving broadcast: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in discovery: ${e.message}")
            } finally {
                try {
                    discoverySocket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing discovery socket: ${e.message}")
                }
            }
        }
    }

    fun stopBroadcasting() {
        Log.d(TAG, "Stopping broadcasting...")
        broadcastJob?.cancel()
        broadcastJob = null

        try {
            broadcastSocket?.close()
            broadcastSocket = null
            Log.d(TAG, "Broadcast socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing broadcast socket: ${e.message}")
        }
    }

    fun stopDiscovery() {
        Log.d(TAG, "Stopping discovery...")
        discoveryJob?.cancel()
        discoveryJob = null

        try {
            discoverySocket?.close()
            discoverySocket = null
            Log.d(TAG, "Discovery socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing discovery socket: ${e.message}")
        }
    }
}