package com.info85.whereami

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.info85.whereami.network.GameClient
import com.info85.whereami.network.GameMessage
import com.info85.whereami.network.GameServer
import com.info85.whereami.network.NetworkManager

class WaitingActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var ipPanel: ConstraintLayout
    private lateinit var tvIpAddress: TextView
    private lateinit var btnCopyIp: Button
    private lateinit var btnShareIp: Button
    private var isServer = false
    private var localIp: String = ""

    companion object {
        private const val TAG = "WaitingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waiting)

        tvStatus = findViewById(R.id.tvStatus)
        ipPanel = findViewById(R.id.ipPanel)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        btnCopyIp = findViewById(R.id.btnCopyIp)
        btnShareIp = findViewById(R.id.btnShareIp)

        isServer = intent.getBooleanExtra("IS_SERVER", false)
        NetworkManager.isServer = isServer

        if (isServer) {
            tvStatus.text = "⏳ Aguardando oponente conectar..."
            showLocalIp()
            startServer()
        } else {
            val serverIp = intent.getStringExtra("SERVER_IP")

            if (serverIp.isNullOrEmpty()) {
                Toast.makeText(this, "❌ Erro: IP do servidor não fornecido", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            tvStatus.text = "📡 Conectando ao servidor..."
            ipPanel.visibility = View.GONE
            connectToServer(serverIp)
        }

        btnCopyIp.setOnClickListener {
            copyIpToClipboard()
        }

        btnShareIp.setOnClickListener {
            shareIp()
        }
    }

    private fun showLocalIp() {
        val ipAddress = getLocalIpAddress()
        if (ipAddress != null) {
            localIp = ipAddress
            tvIpAddress.text = ipAddress
            ipPanel.visibility = View.VISIBLE
            Log.d(TAG, "📍 Local IP: $ipAddress")
        } else {
            tvIpAddress.text = "IP não disponível"
            ipPanel.visibility = View.VISIBLE
            Log.e(TAG, "❌ Failed to get local IP")
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress

            if (ipAddress != 0) {
                @Suppress("DEPRECATION")
                Formatter.formatIpAddress(ipAddress)
            } else {
                getIpFromNetworkInterface()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP: ${e.message}")
            null
        }
    }

    private fun getIpFromNetworkInterface(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in getIpFromNetworkInterface: ${e.message}")
        }
        return null
    }

    private fun copyIpToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IP Address", localIp)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "📋 IP copiado: $localIp", Toast.LENGTH_SHORT).show()
    }

    private fun shareIp() {
        val shareText = "🎮 Vamos jogar Where Am I!\n\n" +
                "Entre no jogo e digite este IP:\n" +
                "📍 $localIp\n\n" +
                "Nos vemos no jogo! 🎯"

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        startActivity(Intent.createChooser(shareIntent, "Compartilhar IP com oponente"))
    }

    private fun startServer() {
        Log.d(TAG, "🖥️ ========================================")
        Log.d(TAG, "🖥️ STARTING SERVER")
        Log.d(TAG, "🖥️ ========================================")

        val server = GameServer()
        NetworkManager.gameServer = server

        // ✅ Configurar listener ANTES de iniciar
        server.setMessageListener { message ->
            Log.d(TAG, "📩 ========================================")
            Log.d(TAG, "📩 SERVER RECEIVED MESSAGE!")
            Log.d(TAG, "📩 Type: ${message::class.simpleName}")
            Log.d(TAG, "📩 Message: $message")
            Log.d(TAG, "📩 ========================================")

            // Quando receber ClientReady, iniciar o jogo
            if (message is GameMessage.ClientReady) {
                Log.d(TAG, "🎮 ✅ CLIENT_READY CONFIRMED! Starting game...")
                runOnUiThread {
                    tvStatus.text = "✅ Oponente conectado! Iniciando jogo..."
                    Toast.makeText(this, "🎮 Iniciando jogo!", Toast.LENGTH_SHORT).show()

                    tvStatus.postDelayed({
                        startGame(true) // Server é sempre Player 1
                    }, 500)
                }
            } else {
                Log.w(TAG, "⚠️ Received non-ClientReady message: ${message::class.simpleName}")
            }
        }

        // Iniciar servidor
        server.start()

        Log.d(TAG, "✅ Server thread started, waiting for connections...")
    }

    private fun connectToServer(serverIp: String) {
        Log.d(TAG, "📡 ========================================")
        Log.d(TAG, "📡 CONNECTING TO SERVER: $serverIp")
        Log.d(TAG, "📡 ========================================")

        Thread {
            try {
                val client = GameClient()
                NetworkManager.gameClient = client

                Log.d(TAG, "📡 Attempting connection...")
                val success = client.connect(serverIp)

                if (!success) {
                    Log.e(TAG, "❌ Connection failed!")
                    runOnUiThread {
                        tvStatus.text = "❌ Falha ao conectar ao servidor"
                        Toast.makeText(this, "❌ Não foi possível conectar", Toast.LENGTH_SHORT).show()

                        tvStatus.postDelayed({
                            finish()
                        }, 2000)
                    }
                    return@Thread
                }

                Log.d(TAG, "✅ TCP connection established!")

                runOnUiThread {
                    tvStatus.text = "✅ Conectado! Enviando sinal..."
                    Toast.makeText(this, "✅ Conectado!", Toast.LENGTH_SHORT).show()
                }

                // ✅ Aguardar estabilização
                Log.d(TAG, "⏳ Waiting 1 second for connection to stabilize...")
                Thread.sleep(1000)

                // ✅ Enviar ClientReady
                Log.d(TAG, "📤 ========================================")
                Log.d(TAG, "📤 SENDING CLIENT_READY MESSAGE")

                val readyMessage = GameMessage.ClientReady(true)
                Log.d(TAG, "📤 Message object: $readyMessage")
                Log.d(TAG, "📤 Message type field: ${readyMessage.type}")

                client.sendMessage(readyMessage)

                Log.d(TAG, "📤 CLIENT_READY sent!")
                Log.d(TAG, "📤 ========================================")

                runOnUiThread {
                    tvStatus.text = "⏳ Aguardando servidor..."
                }

                // ✅ Aguardar e iniciar o jogo
                Log.d(TAG, "⏳ Waiting 2 seconds before starting game...")
                Thread.sleep(2000)

                runOnUiThread {
                    Log.d(TAG, "🎮 Starting game as Player 2...")
                    startGame(false) // Client é sempre Player 2
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ ========================================")
                Log.e(TAG, "❌ CONNECTION ERROR!")
                Log.e(TAG, "❌ Error: ${e.message}")
                e.printStackTrace()
                Log.e(TAG, "❌ ========================================")

                runOnUiThread {
                    tvStatus.text = "❌ Erro ao conectar:\n${e.message}"
                    Toast.makeText(this, "❌ Erro: ${e.message}", Toast.LENGTH_SHORT).show()

                    tvStatus.postDelayed({
                        finish()
                    }, 3000)
                }
            }
        }.start()
    }

    private fun startGame(isPlayer1: Boolean) {
        Log.d(TAG, "🎮 ========================================")
        Log.d(TAG, "🎮 STARTING GAME ACTIVITY")
        Log.d(TAG, "🎮 isPlayer1: $isPlayer1")
        Log.d(TAG, "🎮 ========================================")

        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("IS_PLAYER1", isPlayer1)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔚 WaitingActivity destroyed")
    }

    override fun onBackPressed() {
        super.onBackPressed()

        Log.d(TAG, "⬅️ Back pressed - cleaning up...")
        NetworkManager.reset()
        finish()
    }
}