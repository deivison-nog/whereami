package com.info85.whereami

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.info85.whereami.network.GameServer
import com.info85.whereami.network.NetworkManager

class MainActivity : AppCompatActivity() {
    private lateinit var btnCreateServer: Button
    private lateinit var btnJoinServer: Button
    private lateinit var btnAbout: Button

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCreateServer = findViewById(R.id.btnCreateServer)
        btnJoinServer = findViewById(R.id.btnJoinServer)
        btnAbout = findViewById(R.id.btnAbout)

        btnCreateServer.setOnClickListener {
            createServer()
        }

        btnJoinServer.setOnClickListener {
            val intent = Intent(this, ServerListActivity::class.java)
            startActivity(intent)
        }

        btnAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }

    private fun createServer() {
        Log.d(TAG, "🖥️ Creating server...")

        // ✅ IMPORTANTE: Limpar conexões antigas ANTES de criar novo servidor
        NetworkManager.reset()

        // ✅ Pequeno delay para garantir que as portas foram liberadas
        Thread {
            Thread.sleep(500)

            runOnUiThread {
                val server = GameServer()
                server.start()

                NetworkManager.gameServer = server
                NetworkManager.isServer = true

                Log.d(TAG, "✅ Server created, opening waiting screen...")

                val intent = Intent(this, WaitingActivity::class.java)
                intent.putExtra("IS_SERVER", true)
                startActivity(intent)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 MainActivity resumed")

        // ✅ Limpar conexões quando voltar ao menu
        NetworkManager.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔚 MainActivity destroyed")
    }
}