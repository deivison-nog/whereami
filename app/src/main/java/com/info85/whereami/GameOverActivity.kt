package com.info85.whereami

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.info85.whereami.models.GameState
import com.info85.whereami.network.NetworkManager

class GameOverActivity : AppCompatActivity() {
    private lateinit var tvResultIcon: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvFinalScore: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvStars: TextView
    private lateinit var resultPanel: ConstraintLayout
    private lateinit var btnPlayAgain: Button
    private lateinit var btnMainMenu: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_over)

        // Inicializar views
        tvResultIcon = findViewById(R.id.tvResultIcon)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvFinalScore = findViewById(R.id.tvFinalScore)
        tvMessage = findViewById(R.id.tvMessage)
        tvStars = findViewById(R.id.tvStars)
        resultPanel = findViewById(R.id.resultPanel)
        btnPlayAgain = findViewById(R.id.btnPlayAgain)
        btnMainMenu = findViewById(R.id.btnMainMenu)

        // Receber dados do jogo
        val isWinner = intent.getBooleanExtra("IS_WINNER", false)
        val myScore = intent.getIntExtra("MY_SCORE", 0)
        val opponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)

        // Configurar tela baseado no resultado
        setupGameOver(isWinner, myScore, opponentScore)

        // Animações
        animateResult()

        // Botões
        btnPlayAgain.setOnClickListener {
            playAgain()
        }

        btnMainMenu.setOnClickListener {
            goToMainMenu()
        }
    }

    private fun setupGameOver(isWinner: Boolean, myScore: Int, opponentScore: Int) {
        tvFinalScore.text = "$myScore x $opponentScore"

        if (isWinner) {
            // Configuração de Vitória
            tvResultIcon.text = "🏆"
            tvResultTitle.text = "VOCÊ VENCEU!"
            tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.orange_border))

            val scoreDiff = myScore - opponentScore
            tvMessage.text = when {
                myScore == GameState.WINNING_SCORE && opponentScore == 0 ->
                    "🎯 VITÓRIA PERFEITA! Impossível! ${GameState.WINNING_SCORE} x 0!"
                scoreDiff >= 30 ->
                    "🔥 DOMINAÇÃO ABSOLUTA! Arrasou completamente!"
                scoreDiff >= 25 ->
                    "💪 ESMAGADORA VITÓRIA! Jogou demais!"
                scoreDiff >= 15 ->
                    "⭐ GRANDE VITÓRIA! Muito superior!"
                scoreDiff >= 10 ->
                    "✨ VITÓRIA CLARA! Jogou bem!"
                else ->
                    "🎉 VITÓRIA! Foi apertado mas você conseguiu!"
            }
            resultPanel.background = ContextCompat.getDrawable(this, R.drawable.winner_panel)
            tvStars.visibility = View.VISIBLE

        } else {
            // Configuração de Derrota
            tvResultIcon.text = "😢"
            tvResultTitle.text = "VOCÊ PERDEU!"
            tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.purple_700))

            val scoreDiff = opponentScore - myScore
            tvMessage.text = when {
                scoreDiff >= 30 ->
                    "😅 Esse foi difícil! Mas não desista!"
                scoreDiff >= 25 ->
                    "💪 Foi complicado! Mais treino e você vence!"
                scoreDiff >= 15 ->
                    "🎯 Quase! Continue tentando!"
                scoreDiff >= 10 ->
                    "💫 Foi por um pouco! Você consegue!"
                else ->
                    "😤 Foi apertado! Revanche agora!"
            }
            resultPanel.background = ContextCompat.getDrawable(this, R.drawable.loser_panel)
            tvStars.visibility = View.GONE
        }
    }

    private fun animateResult() {
        // Animação do ícone
        tvResultIcon.alpha = 0f
        tvResultIcon.scaleX = 0f
        tvResultIcon.scaleY = 0f

        tvResultIcon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animação do painel
        resultPanel.alpha = 0f
        resultPanel.translationY = 100f

        resultPanel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animação das estrelas (se visível)
        if (tvStars.visibility == View.VISIBLE) {
            val rotateAnimation = ObjectAnimator.ofFloat(tvStars, "rotation", 0f, 360f)
            rotateAnimation.duration = 2000
            rotateAnimation.repeatCount = ObjectAnimator.INFINITE
            rotateAnimation.start()
        }
    }

    private fun playAgain() {
        // Limpar conexões antigas
        NetworkManager.reset()

        // Voltar para a tela de espera com as mesmas configurações
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun goToMainMenu() {
        // Limpar conexões
        NetworkManager.reset()

        // Voltar para o menu principal
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Ao pressionar voltar, vai para o menu principal
        goToMainMenu()
    }
}