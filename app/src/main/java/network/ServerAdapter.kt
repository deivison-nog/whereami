package com.info85.whereami

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.info85.whereami.network.AvailableServer

class ServerAdapter(
    private val servers: List<AvailableServer>,
    private val onServerClick: (AvailableServer) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvServerIp: TextView = view.findViewById(R.id.tvServerIp)
        val tvServerHost: TextView = view.findViewById(R.id.tvServerHost)
        val tvResponseTime: TextView = view.findViewById(R.id.tvResponseTime)
        val btnConnect: Button = view.findViewById(R.id.btnConnect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = servers[position]

        holder.tvServerIp.text = "📡 ${server.ipAddress}"
        holder.tvServerHost.text = server.hostName
        holder.tvResponseTime.text = "⚡ ${server.responseTime}ms"

        holder.btnConnect.setOnClickListener {
            onServerClick(server)
        }

        // Animação de entrada
        holder.itemView.alpha = 0f
        holder.itemView.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay((position * 50).toLong())
            .start()
    }

    override fun getItemCount(): Int = servers.size
}