package com.info85.whereami

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.info85.whereami.models.GameState
import com.info85.whereami.network.GameMessage
import com.info85.whereami.network.NetworkManager
import android.view.WindowManager
import java.util.concurrent.Executors

class GameActivity : AppCompatActivity() {
    private lateinit var gridLayout: GridLayout
    private lateinit var tvScore: TextView
    private lateinit var tvStatus: TextView
    private lateinit var messageContainer: FrameLayout
    private val gameState = GameState()
    private val buttons = Array(4) { arrayOfNulls<Button>(4) }
    private var gameOverProcessed = false
    private var disconnectDialog: AlertDialog? = null
    private var exitDialog: AlertDialog? = null

    // Flag para bloquear cliques durante animação
    private var isAnimatingMessage = false

    // Executor dedicado para envio de mensagens de rede (evita I/O na thread principal)
    private val messageSender = Executors.newSingleThreadExecutor()

    // Lista de emojis de lugares do dia-a-dia
    private val emojis = listOf(
        "🏠", "🚗", "🏪", "🏦",
        "🏖️", "🏫", "⛪", "🏢",
        "🍕", "☕", "🏋️", "🎬",
        "🚌", "🏊", "🛒", "⛽"
    )

    // Mapeamento emoji → nome do local
    private val emojiNames = mapOf(
        "🏠" to "CASA",
        "🚗" to "CARRO",
        "🏪" to "SUPERMERCADO",
        "🏦" to "BANCO",
        "🏖️" to "PRAIA",
        "🏫" to "ESCOLA",
        "⛪" to "IGREJA",
        "🏢" to "TRABALHO",
        "🍕" to "PIZZARIA",
        "☕" to "CAFETERIA",
        "🏋️" to "ACADEMIA",
        "🎬" to "CINEMA",
        "🚌" to "ÔNIBUS",
        "🏊" to "PISCINA",
        "🛒" to "SHOPPING",
        "⛽" to "POSTO"
    )

    // Matriz para armazenar os emojis de cada quadrado
    private val gridEmojis = Array(4) { Array(4) { "" } }
    private var emojisInitialized = false

    companion object {
        private const val TAG = "GameActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gridLayout = findViewById(R.id.gridLayout)
        tvScore = findViewById(R.id.tvScore)
        tvStatus = findViewById(R.id.tvStatus)
        messageContainer = findViewById(R.id.messageContainer)

        val isPlayer1 = intent.getBooleanExtra("IS_PLAYER1", true)
        gameState.isPlayer1 = isPlayer1

        Log.d(TAG, "🎮 ========================================")
        Log.d(TAG, "🎮 GameActivity started - isPlayer1: $isPlayer1")
        Log.d(TAG, "🎮 NetworkManager.isServer: ${NetworkManager.isServer}")
        Log.d(TAG, "🎮 NetworkManager.gameServer: ${NetworkManager.gameServer}")
        Log.d(TAG, "🎮 NetworkManager.gameClient: ${NetworkManager.gameClient}")
        Log.d(TAG, "🎮 ========================================")

        setupNetworkListener()

        if (gameState.isPlayer1) {
            Log.d(TAG, "✅ Player 1 (Server): Initializing game...")
            initializeEmojis()
            setupGrid()
            updateUI()

            // EmojiSync is sent only when CLIENT_READY is received from the client's GameActivity
            Log.d(TAG, "⏳ Server: Waiting for CLIENT_READY from client GameActivity to send EmojiSync...")

        } else {
            Log.d(TAG, "⏳ Player 2 (Client): Waiting for EmojiSync from server...")
            tvStatus.text = "⏳ Sincronizando com servidor..."

            // Notify server that client GameActivity is ready and request EmojiSync
            Log.d(TAG, "📤 Client: Sending CLIENT_READY to request EmojiSync...")
            sendMessage(GameMessage.ClientReady(true))
        }
    }

    private fun initializeEmojis() {
        val shuffledEmojis = emojis.shuffled()
        var index = 0
        for (row in 0..3) {
            for (col in 0..3) {
                gridEmojis[row][col] = shuffledEmojis[index]
                Log.d(TAG, "Emoji[$row][$col] = ${gridEmojis[row][col]}")
                index++
            }
        }
        emojisInitialized = true
        Log.d(TAG, "✓ Emojis initialized successfully")
    }

    private fun setEmojisFromList(emojiList: List<String>) {
        Log.d(TAG, "📥 Setting emojis from received list: ${emojiList.size} emojis")
        var index = 0
        for (row in 0..3) {
            for (col in 0..3) {
                if (index < emojiList.size) {
                    gridEmojis[row][col] = emojiList[index]
                    Log.d(TAG, "Set Emoji[$row][$col] = ${gridEmojis[row][col]}")
                    index++
                }
            }
        }
        emojisInitialized = true
        Log.d(TAG, "✓ Emojis set from list successfully")
    }

    private fun sendEmojiSync() {
        val emojiList = mutableListOf<String>()
        for (row in 0..3) {
            for (col in 0..3) {
                emojiList.add(gridEmojis[row][col])
            }
        }

        Log.d(TAG, "📤 Sending EMOJI_SYNC with ${emojiList.size} emojis")
        Log.d(TAG, "📤 Emojis: $emojiList")
        val emojiSyncMessage = GameMessage.EmojiSync(emojiList)
        sendMessage(emojiSyncMessage)
    }

    private fun setupNetworkListener() {
        Log.d(TAG, "🔧 Setting up network listener...")
        if (NetworkManager.isServer) {
            NetworkManager.gameServer?.setMessageListener { message ->
                Log.d(TAG, "📩 Server received message: ${message::class.simpleName}")
                handleNetworkMessage(message)
            }

            // Detectar desconexão do cliente
            NetworkManager.gameServer?.setDisconnectListener {
                Log.d(TAG, "🔌 Client disconnected!")
                showDisconnectDialog()
            }
        } else {
            NetworkManager.gameClient?.setMessageListener { message ->
                Log.d(TAG, "📩 Client received message: ${message::class.simpleName}")
                handleNetworkMessage(message)
            }

            // Detectar desconexão do servidor
            NetworkManager.gameClient?.setDisconnectListener {
                Log.d(TAG, "🔌 Server disconnected!")
                showDisconnectDialog()
            }
        }
        Log.d(TAG, "✅ Network listener configured")
    }

    private fun setupGrid() {
        if (!emojisInitialized) {
            Log.e(TAG, "❌ ERROR: Trying to setup grid but emojis not initialized!")
            return
        }

        Log.d(TAG, "🎨 Setting up grid...")
        gridLayout.removeAllViews()
        gridLayout.rowCount = 4
        gridLayout.columnCount = 4

        for (row in 0..3) {
            for (col in 0..3) {
                val emoji = gridEmojis[row][col]
                Log.d(TAG, "Creating button[$row][$col] with emoji: $emoji")

                val button = Button(this).apply {
                    text = emoji
                    textSize = 40f
                    setTypeface(typeface, Typeface.BOLD)
                    background = ContextCompat.getDrawable(context, R.drawable.button_square)

                    setShadowLayer(4f, 2f, 2f, Color.parseColor("#000000"))

                    val params = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        columnSpec = GridLayout.spec(col, 1f)
                        rowSpec = GridLayout.spec(row, 1f)
                        setMargins(6, 6, 6, 6)
                    }
                    layoutParams = params
                    gravity = Gravity.CENTER
                    elevation = 8f

                    setOnClickListener {
                        onSquareClicked(row, col)
                    }
                }

                buttons[row][col] = button
                gridLayout.addView(button)
            }
        }
        Log.d(TAG, "✅ Grid setup completed with ${gridLayout.childCount} buttons")
    }

    private fun showCustomMessage(emoji: String, isCorrect: Boolean, showPoints: Boolean = false) {
        // Bloquear novos cliques
        isAnimatingMessage = true

        // Inflar layout customizado
        val messageView = LayoutInflater.from(this).inflate(R.layout.custom_message, messageContainer, false)

        val card = messageView.findViewById<CardView>(R.id.messageCard)
        val tvEmoji = messageView.findViewById<TextView>(R.id.tvEmoji)
        val tvMessage = messageView.findViewById<TextView>(R.id.tvMessage)
        val tvPoints = messageView.findViewById<TextView>(R.id.tvPoints)

        // Configurar emoji e mensagem
        tvEmoji.text = emoji
        val locationName = emojiNames[emoji] ?: "LUGAR"

        if (isCorrect) {
            tvMessage.text = "ACERTOU!\nESTAVA ${getLocationPreposition(locationName)} $locationName!"
            card.setCardBackgroundColor(Color.parseColor("#4CAF50")) // Verde
            if (showPoints) {
                tvPoints.visibility = View.VISIBLE
                tvPoints.text = "+1 PONTO"
            }
        } else {
            tvMessage.text = "NÃO ESTÁ ${getLocationPreposition(locationName)} $locationName!"
            card.setCardBackgroundColor(Color.parseColor("#FF5252")) // Vermelho
        }

        // Adicionar ao container
        messageContainer.addView(messageView)

        // Animação de entrada
        messageView.alpha = 0f
        messageView.scaleX = 0.3f
        messageView.scaleY = 0.3f
        messageView.translationY = -200f

        val fadeIn = ObjectAnimator.ofFloat(messageView, "alpha", 0f, 1f)
        val scaleXIn = ObjectAnimator.ofFloat(messageView, "scaleX", 0.3f, 1f)
        val scaleYIn = ObjectAnimator.ofFloat(messageView, "scaleY", 0.3f, 1f)
        val translateIn = ObjectAnimator.ofFloat(messageView, "translationY", -200f, 0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleXIn, scaleYIn, translateIn)
        animatorSet.duration = 500
        animatorSet.interpolator = BounceInterpolator()
        animatorSet.start()

        // Remover após 2 segundos E desbloquear cliques
        messageView.postDelayed({
            // Animação de saída
            val fadeOut = ObjectAnimator.ofFloat(messageView, "alpha", 1f, 0f)
            val scaleXOut = ObjectAnimator.ofFloat(messageView, "scaleX", 1f, 0.3f)
            val scaleYOut = ObjectAnimator.ofFloat(messageView, "scaleY", 1f, 0.3f)
            val translateOut = ObjectAnimator.ofFloat(messageView, "translationY", 0f, 200f)

            val animatorSetOut = AnimatorSet()
            animatorSetOut.playTogether(fadeOut, scaleXOut, scaleYOut, translateOut)
            animatorSetOut.duration = 300
            animatorSetOut.interpolator = AccelerateDecelerateInterpolator()
            animatorSetOut.start()

            messageView.postDelayed({
                messageContainer.removeView(messageView)

                // Desbloquear cliques após a mensagem desaparecer completamente
                isAnimatingMessage = false
                Log.d(TAG, "Message animation finished - clicks unlocked")
            }, 300)
        }, 2000)
    }

    private fun getLocationPreposition(locationName: String): String {
        // Retorna a preposição correta (NO/NA) baseado no local
        return when (locationName) {
            "CASA", "PRAIA", "ESCOLA", "IGREJA", "PIZZARIA", "CAFETERIA", "ACADEMIA", "PISCINA" -> "NA"
            else -> "NO"
        }
    }

    private fun onSquareClicked(row: Int, col: Int) {
        // Verificar se está em animação - BLOQUEAR CLIQUES
        if (isAnimatingMessage) {
            Log.d(TAG, "Click blocked - message animation in progress")
            return
        }

        if (!gameState.grid[row][col]) {
            Log.d(TAG, "Square ($row,$col) already removed")
            return
        }

        if (gameOverProcessed) {
            Log.d(TAG, "Game is over, ignoring click")
            return
        }

        val currentPlayer = if (gameState.isPlayer1) 1 else 2
        Log.d(TAG, "Square clicked: ($row,$col) by Player $currentPlayer")

        if (currentPlayer == gameState.currentPicker && gameState.selectedSquare == null) {
            Log.d(TAG, "Player $currentPlayer selected secret square ($row,$col)")
            gameState.selectedSquare = Pair(row, col)
            sendMessage(GameMessage.SquareSelected(row, col))
            updateUI()

        } else if (currentPlayer == gameState.currentGuesser && gameState.selectedSquare != null) {
            val selected = gameState.selectedSquare!!
            val correct = selected.first == row && selected.second == col

            // IMPORTANTE: Capturar o emoji ANTES de qualquer modificação
            val clickedEmoji = gridEmojis[row][col]

            Log.d(TAG, "Player $currentPlayer guessed ($row,$col) - Correct: $correct, Emoji: $clickedEmoji")

            if (correct) {
                Log.d(TAG, "✓ CORRECT! Player $currentPlayer (guesser) scores")
                gameState.addPoint(currentPlayer)

                // Mostrar mensagem de acerto
                showCustomMessage(clickedEmoji, true, true)

                if (gameState.isGameOver()) {
                    gridLayout.postDelayed({
                        sendGameOver()
                        showGameOver()
                    }, 2500)
                    return
                }

                gameState.resetGrid()
                gameState.switchRoles()
                gameState.selectedSquare = null

                // Enviar mensagem com o emoji ANTES de embaralhar
                sendMessage(GameMessage.GuessResult(true, row, col, clickedEmoji))

                // Servidor regenera e sincroniza novos emojis DEPOIS de enviar resultado
                if (gameState.isPlayer1) {
                    initializeEmojis()
                    sendEmojiSync()
                }

                gridLayout.postDelayed({
                    setupGrid()
                    updateUI()
                }, 2500)

            } else {
                Log.d(TAG, "✗ WRONG! Player ${gameState.currentPicker} (picker) scores")
                gameState.addPoint(gameState.currentPicker)
                gameState.removeSquare(row, col)

                // Mostrar mensagem de erro
                showCustomMessage(clickedEmoji, false, false)

                if (gameState.isGameOver()) {
                    gridLayout.postDelayed({
                        sendGameOver()
                        showGameOver()
                    }, 2500)
                    return
                }

                // Enviar mensagem com o emoji
                sendMessage(GameMessage.GuessResult(false, row, col, clickedEmoji))

                // Aguardar animação terminar antes de atualizar grid
                gridLayout.postDelayed({
                    setupGrid()
                    updateUI()
                }, 2300)
            }
        } else {
            Log.d(TAG, "Click ignored - not player's turn")
        }
    }

    private fun updateUI() {
        tvScore.text = "Você: ${if (gameState.isPlayer1) gameState.player1Score else gameState.player2Score} | " +
                "Oponente: ${if (gameState.isPlayer1) gameState.player2Score else gameState.player1Score}"

        val currentPlayer = if (gameState.isPlayer1) 1 else 2
        tvStatus.text = when {
            gameState.selectedSquare == null && currentPlayer == gameState.currentPicker ->
                "🎯 Sua vez: Onde você está?"
            gameState.selectedSquare != null && currentPlayer == gameState.currentGuesser ->
                "🤔 Tente adivinhar: Onde ele está?"
            gameState.selectedSquare == null && currentPlayer == gameState.currentGuesser ->
                "⏳ Aguardando oponente escolher..."
            else -> "⏳ Aguardando oponente adivinhar..."
        }

        for (row in 0..3) {
            for (col in 0..3) {
                buttons[row][col]?.apply {
                    visibility = if (gameState.grid[row][col]) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun handleNetworkMessage(message: GameMessage) {
        runOnUiThread {
            when (message) {
                is GameMessage.PlayerDisconnected -> {
                    Log.d(TAG, "<<< Received PLAYER_DISCONNECTED")
                    showDisconnectDialog()
                }
                is GameMessage.ClientReady -> {
                    Log.d(TAG, "<<< Received CLIENT_READY from client GameActivity - sending EmojiSync")
                    if (gameState.isPlayer1) {
                        sendEmojiSync()
                    }
                }
                is GameMessage.EmojiSync -> {
                    Log.d(TAG, "📥 <<< Received EMOJI_SYNC with ${message.emojis.size} emojis")
                    Log.d(TAG, "📥 Emojis received: ${message.emojis}")

                    setEmojisFromList(message.emojis)
                    setupGrid()
                    updateUI()

                    Log.d(TAG, "✅ Emoji sync processed successfully - GAME READY!")
                }
                is GameMessage.SquareSelected -> {
                    Log.d(TAG, "<<< Opponent selected secret (${ message.row},${message.col})")
                    gameState.selectedSquare = Pair(message.row, message.col)
                    updateUI()
                }
                is GameMessage.GuessResult -> {
                    Log.d(TAG, "<<< Guess result - Correct: ${message.correct}, Emoji: ${message.emoji}")

                    // Usar o emoji que veio na mensagem, não o que está no grid
                    val clickedEmoji = message.emoji

                    if (message.correct) {
                        val guesser = if (gameState.isPlayer1) 2 else 1
                        gameState.addPoint(guesser)

                        // Mostrar mensagem de que oponente acertou
                        showCustomMessage(clickedEmoji, true, false)

                        if (gameState.isGameOver()) {
                            gridLayout.postDelayed({
                                showGameOver()
                            }, 2500)
                            return@runOnUiThread
                        }

                        gameState.resetGrid()
                        gameState.switchRoles()
                        gameState.selectedSquare = null

                        gridLayout.postDelayed({
                            setupGrid()
                            updateUI()
                        }, 2500)

                    } else {
                        val picker = gameState.currentPicker
                        gameState.addPoint(picker)
                        gameState.removeSquare(message.row, message.col)

                        // Mostrar mensagem de que oponente errou
                        showCustomMessage(clickedEmoji, false, false)

                        if (gameState.isGameOver()) {
                            gridLayout.postDelayed({
                                showGameOver()
                            }, 2500)
                            return@runOnUiThread
                        }

                        gridLayout.postDelayed({
                            setupGrid()
                            updateUI()
                        }, 2300)
                    }
                }
                is GameMessage.GameOver -> {
                    Log.d(TAG, "<<< Received GAME_OVER: winner=${message.winnerPlayer}")

                    if (!gameOverProcessed) {
                        gameState.player1Score = message.player1Score
                        gameState.player2Score = message.player2Score

                        showGameOver()
                    }
                }
                else -> {
                    Log.d(TAG, "⚠️ Unhandled message type: ${message::class.simpleName}")
                }
            }
        }
    }

    private fun sendMessage(message: GameMessage) {
        messageSender.execute {
            Log.d(TAG, "📤 >>> Sending message: ${message::class.simpleName}")
            if (NetworkManager.isServer) {
                NetworkManager.gameServer?.sendMessage(message)
            } else {
                NetworkManager.gameClient?.sendMessage(message)
            }
        }
    }

    private fun sendGameOver() {
        val winner = gameState.getWinner() ?: return

        Log.d(TAG, ">>> Sending GAME_OVER: winner=$winner")

        val gameOverMessage = GameMessage.GameOver(
            winnerPlayer = winner,
            player1Score = gameState.player1Score,
            player2Score = gameState.player2Score
        )

        sendMessage(gameOverMessage)
    }

    private fun showDisconnectDialog() {
        if (disconnectDialog?.isShowing == true || isFinishing) {
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.disconnect_dialog, null)
        val btnOk = dialogView.findViewById<Button>(R.id.btnDisconnectOk)

        disconnectDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        disconnectDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOk.setOnClickListener {
            disconnectDialog?.dismiss()
            returnToMainMenu()
        }

        disconnectDialog?.show()
    }

    private fun returnToMainMenu() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
        messageSender.execute { NetworkManager.reset() }
    }

    private fun showGameOver() {
        if (gameOverProcessed) {
            Log.d(TAG, "Game over already processed, ignoring")
            return
        }

        gameOverProcessed = true

        val winner = gameState.getWinner()
        val isWinner = (winner == 1 && gameState.isPlayer1) || (winner == 2 && !gameState.isPlayer1)

        val myScore = if (gameState.isPlayer1) gameState.player1Score else gameState.player2Score
        val opponentScore = if (gameState.isPlayer1) gameState.player2Score else gameState.player1Score

        Log.d(TAG, "===== GAME OVER! Winner: Player $winner =====")

        gridLayout.postDelayed({
            val intent = Intent(this, GameOverActivity::class.java)
            intent.putExtra("IS_WINNER", isWinner)
            intent.putExtra("MY_SCORE", myScore)
            intent.putExtra("OPPONENT_SCORE", opponentScore)
            startActivity(intent)
            finish()
        }, 300)
    }

    override fun onBackPressed() {
        // Apenas mostrar o diálogo de confirmação
        // NÃO chamar super.onBackPressed() aqui
        showExitConfirmDialog()
    }

    private fun showExitConfirmDialog() {
        if (exitDialog?.isShowing == true || isFinishing) {
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.exit_confirm_dialog, null)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnExitCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnExitConfirm)

        exitDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        exitDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Botão Cancelar - volta ao jogo
        btnCancel.setOnClickListener {
            exitDialog?.dismiss()
        }

        // Botão Confirmar - sai do jogo
        btnConfirm.setOnClickListener {
            exitDialog?.dismiss()
            confirmExit()
        }

        exitDialog?.show()
    }

    private fun confirmExit() {
        Log.d(TAG, "User confirmed exit")

        // Notificar oponente que estamos saindo
        if (!gameOverProcessed) {
            sendMessage(GameMessage.PlayerDisconnected("quit"))
        }

        // Voltar para o menu principal imediatamente para evitar bloqueio da UI
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()

        // Limpar conexões em segundo plano (não bloqueia a UI)
        messageSender.execute { NetworkManager.reset() }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "===== Activity destroyed =====")

        // Fechar diálogos se estiverem abertos
        exitDialog?.dismiss()
        disconnectDialog?.dismiss()

        messageSender.shutdown()
    }
}