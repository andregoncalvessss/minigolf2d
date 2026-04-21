package com.andregoncalves29892.minigolf2d

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var layoutInstrucoes: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- MODO ECRÃ INTEIRO ---
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        setContentView(R.layout.activity_main)

        configurarGradienteTitulo()

        val btnJogar = findViewById<ConstraintLayout>(R.id.btnJogarCustom)
        val btnInstrucoes = findViewById<ConstraintLayout>(R.id.btnInstrucoesCustom)
        layoutInstrucoes = findViewById(R.id.layoutInstrucoes)
        val btnFechar = findViewById<ImageButton>(R.id.btnFecharInstrucoes)

        // 1. Iniciar o Jogo
        btnJogar.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }

        // 2. Abrir Instruções
        btnInstrucoes.setOnClickListener {
            layoutInstrucoes.visibility = View.VISIBLE
            callbackBack.isEnabled = true
        }

        // 3. Fechar Instruções (Apenas no botão X)
        btnFechar.setOnClickListener {
            fecharInstrucoes()
        }

        // 4. Configurar o novo OnBackPressedDispatcher (Substitui o antigo onBackPressed)
        onBackPressedDispatcher.addCallback(this, callbackBack)
    }

    // Callback para o botão físico de voltar
    private val callbackBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            fecharInstrucoes()
        }
    }

    private fun fecharInstrucoes() {
        layoutInstrucoes.visibility = View.GONE
        callbackBack.isEnabled = false
    }

    private fun configurarGradienteTitulo() {
        val tvTitulo = findViewById<TextView>(R.id.tvTituloMain)
        tvTitulo.post {
            val larguraTexto = tvTitulo.paint.measureText(tvTitulo.text.toString())
            val shader = LinearGradient(
                0f, 0f, larguraTexto, 0f,
                intArrayOf(Color.parseColor("#534439"), Color.parseColor("#944A00")),
                null,
                Shader.TileMode.CLAMP
            )
            tvTitulo.paint.shader = shader
            tvTitulo.invalidate()
        }
    }
}