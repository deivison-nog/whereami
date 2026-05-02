package com.info85.whereami.network

import android.util.Log

object NetworkManager {
    var gameServer: GameServer? = null
    var gameClient: GameClient? = null
    var isServer: Boolean = false

    private const val TAG = "NetworkManager"

    fun reset() {
        Log.d(TAG, "🔄 ========================================")
        Log.d(TAG, "🔄 Resetting NetworkManager...")
        Log.d(TAG, "🔄 ========================================")

        // ✅ Fechar servidor se existir
        gameServer?.let {
            Log.d(TAG, "🛑 Shutting down GameServer...")
            try {
                it.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down server: ${e.message}")
            }
        }
        gameServer = null

        // ✅ Desconectar cliente se existir
        gameClient?.let {
            Log.d(TAG, "🔌 Disconnecting GameClient...")
            try {
                it.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting client: ${e.message}")
            }
        }
        gameClient = null

        isServer = false

        Log.d(TAG, "✅ NetworkManager reset complete")
        Log.d(TAG, "========================================")
    }
}