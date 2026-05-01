package com.info85.whereami

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.info85.whereami.network.AvailableServer
import com.info85.whereami.network.GameClient
import com.info85.whereami.network.NetworkManager
import kotlin.concurrent.thread

class ServerListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnRefresh: Button
    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvNoServers: TextView

    private val servers = mutableListOf<AvailableServer>()
    private lateinit var adapter: ServerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_list)

        recyclerView = findViewById(R.id.recyclerViewServers)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvNoServers = findViewById(R.id.tvNoServers)

        // Configurar RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ServerAdapter(servers) { server ->
            connectToServer(server)
        }
        recyclerView.adapter = adapter

        btnRefresh.setOnClickListener {
            searchForServers()
        }

        btnBack.setOnClickListener {
            finish()
        }

        // Buscar servidores automaticamente ao abrir
        searchForServers()
    }

    private fun searchForServers() {
        progressBar.visibility = View.VISIBLE
        btnRefresh.isEnabled = false
        tvStatus.text = "🔍 Procurando servidores..."
        tvNoServers.visibility = View.GONE
        servers.clear()
        adapter.notifyDataSetChanged()

        thread {
            val client = GameClient()
            val foundServers = client.discoverAllServers(5000)

            runOnUiThread {
                servers.clear()
                servers.addAll(foundServers)
                adapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE
                btnRefresh.isEnabled = true

                if (servers.isEmpty()) {
                    tvStatus.text = "❌ Nenhum servidor encontrado"
                    tvNoServers.visibility = View.VISIBLE
                    Toast.makeText(this, "Nenhum servidor disponível", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "✅ ${servers.size} servidor(es) disponível(eis)"
                    tvNoServers.visibility = View.GONE
                }
            }
        }
    }

    private fun connectToServer(server: AvailableServer) {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "📡 Conectando a ${server.ipAddress}..."
        btnRefresh.isEnabled = false

        thread {
            try {
                val client = GameClient()
                val success = client.connect(server.ipAddress)

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnRefresh.isEnabled = true

                    if (success) {
                        NetworkManager.gameClient = client
                        NetworkManager.isServer = false

                        Toast.makeText(this, "✅ Conectado!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, WaitingActivity::class.java)
                        intent.putExtra("IS_SERVER", false)
                        intent.putExtra("SERVER_IP", server.ipAddress)
                        startActivity(intent)
                        finish()
                    } else {
                        tvStatus.text = "❌ Falha na conexão"
                        Toast.makeText(this, "Não foi possível conectar ao servidor", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnRefresh.isEnabled = true
                    tvStatus.text = "❌ Erro: ${e.message}"
                    Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}