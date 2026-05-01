package com.info85.whereami.network

import com.google.gson.annotations.SerializedName

sealed class GameMessage {
    @get:SerializedName("type")
    abstract val type: String

    // ✅ Classe auxiliar para identificar o tipo antes de deserializar
    data class BaseMessage(
        @SerializedName("type")
        val type: String
    )

    data class PlayerJoined(
        @SerializedName("playerName")
        val playerName: String
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "PLAYER_JOINED"
    }

    data class PlayerDisconnected(
        @SerializedName("reason")
        val reason: String = "quit"
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "PLAYER_DISCONNECTED"
    }

    data class ClientReady(
        @SerializedName("ready")
        val ready: Boolean
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "CLIENT_READY"
    }

    data class EmojiSync(
        @SerializedName("emojis")
        val emojis: List<String>
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "EMOJI_SYNC"
    }

    data class SquareSelected(
        @SerializedName("row")
        val row: Int,
        @SerializedName("col")
        val col: Int
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "SQUARE_SELECTED"
    }

    data class GuessAttempt(
        @SerializedName("row")
        val row: Int,
        @SerializedName("col")
        val col: Int
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "GUESS_ATTEMPT"
    }

    data class GuessResult(
        @SerializedName("correct")
        val correct: Boolean,
        @SerializedName("row")
        val row: Int,
        @SerializedName("col")
        val col: Int,
        @SerializedName("emoji")
        val emoji: String
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "GUESS_RESULT"
    }

    data class GameOver(
        @SerializedName("winnerPlayer")
        val winnerPlayer: Int,
        @SerializedName("player1Score")
        val player1Score: Int,
        @SerializedName("player2Score")
        val player2Score: Int
    ) : GameMessage() {
        @SerializedName("type")
        override val type = "GAME_OVER"
    }
}