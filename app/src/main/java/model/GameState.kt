package com.info85.whereami.models

data class GameState(
    var grid: Array<BooleanArray> = Array(4) { BooleanArray(4) { true } },
    var currentPicker: Int = 1, // 1 ou 2
    var currentGuesser: Int = 2,
    var selectedSquare: Pair<Int, Int>? = null,
    var player1Score: Int = 0,
    var player2Score: Int = 0,
    var isPlayer1: Boolean = true
) {
    companion object {
        const val WINNING_SCORE = 50 // Pontuação necessária para vencer (alterado de 30 para 50)
    }

    fun resetGrid() {
        grid = Array(4) { BooleanArray(4) { true } }
    }

    fun removeSquare(row: Int, col: Int) {
        grid[row][col] = false
    }

    fun switchRoles() {
        val temp = currentPicker
        currentPicker = currentGuesser
        currentGuesser = temp
    }

    fun addPoint(player: Int) {
        if (player == 1) player1Score++ else player2Score++
    }

    fun isGameOver(): Boolean {
        return player1Score >= WINNING_SCORE || player2Score >= WINNING_SCORE
    }

    fun getWinner(): Int? {
        return when {
            player1Score >= WINNING_SCORE -> 1
            player2Score >= WINNING_SCORE -> 2
            else -> null
        }
    }
}