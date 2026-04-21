package com.andregoncalves29892.minigolf2d

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.sqrt

class GameActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null

    private lateinit var gameView: GameView
    private lateinit var layoutPauseMenu: ConstraintLayout
    private lateinit var btnPause: ImageButton
    private lateinit var layoutGameOverMenu: ConstraintLayout
    private lateinit var tvPontuacaoFinalValor: TextView
    private lateinit var tvMelhorPontuacaoLabel: TextView
    private lateinit var sharedPreferences: SharedPreferences

    // --- ÁUDIO ---
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var soundPool: SoundPool
    private var soundIdShot: Int = 0
    private var volumeMusica: Float = 0.5f
    private var volumeFX: Float = 0.8f

    private var forcaSuave = 0f
    private val alpha = 0.15f
    private var ultimoTempoTacada: Long = 0
    private val COOLDOWN_MS = 1500
    private var capturandoAbanao = false
    private var tempoInicioAbanao = 0L
    private var forcaMaximaCapturada = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        setContentView(R.layout.activity_game)

        sharedPreferences = getSharedPreferences("MiniGolfPrefs", Context.MODE_PRIVATE)

        gameView = findViewById(R.id.gameView)
        layoutPauseMenu = findViewById(R.id.layoutPauseMenu)
        btnPause = findViewById(R.id.btnPause)
        layoutGameOverMenu = findViewById(R.id.layoutGameOverMenu)
        tvPontuacaoFinalValor = findViewById(R.id.tvPontuacaoFinalValor)
        tvMelhorPontuacaoLabel = findViewById(R.id.tvMelhorPontuacaoLabel)

        inicializarAudio()
        setupPauseMenu()
        setupGameOverMenu()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        gameView.onGameOver = { pontuacaoFinal -> mostrarEcraGameOver(pontuacaoFinal) }

        // Só toca som se o jogo não estiver pausado
        gameView.onShotFired = {
            if (!gameView.isPaused()) {
                soundPool.play(soundIdShot, volumeFX, volumeFX, 1, 0, 1f)
            }
        }
    }

    private fun inicializarAudio() {
        mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
        mediaPlayer?.isLooping = true
        mediaPlayer?.setVolume(volumeMusica, volumeMusica)
        mediaPlayer?.start()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(audioAttributes).build()
        soundIdShot = soundPool.load(this, R.raw.shot_fx, 1)
    }

    // --- LÓGICA DE PAUSA (ÁUDIO + VISUAL) ---
    private fun abrirMenuPausa() {
        if (!gameView.isPaused()) {
            gameView.pause()

            // Pausar Áudio
            mediaPlayer?.pause()
            soundPool.autoPause() // Para qualquer efeito sonoro a decorrer

            layoutPauseMenu.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
        }
    }

    private fun retomarJogo() {
        gameView.resume()

        // Retomar Áudio
        mediaPlayer?.start()
        soundPool.autoResume()

        layoutPauseMenu.visibility = View.GONE
        btnPause.visibility = View.VISIBLE
    }

    private fun setupPauseMenu() {
        val btnContinue = findViewById<Button>(R.id.btnContinue)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnExit = findViewById<Button>(R.id.btnExit)
        val sbFX = findViewById<SeekBar>(R.id.sbFX)
        val sbMusic = findViewById<SeekBar>(R.id.sbMusic)

        sbMusic.progress = (volumeMusica * 100).toInt()
        sbFX.progress = (volumeFX * 100).toInt()

        btnPause.setOnClickListener { abrirMenuPausa() }

        btnContinue.setOnClickListener { retomarJogo() }

        btnReset.setOnClickListener {
            gameView.resetarJogoCompleto()
            retomarJogo()
        }

        btnExit.setOnClickListener { finish() }

        sbMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                volumeMusica = p / 100f
                mediaPlayer?.setVolume(volumeMusica, volumeMusica)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        sbFX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { volumeFX = p / 100f }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupGameOverMenu() {
        val btnJogarNovamente = findViewById<Button>(R.id.btnJogarNovamente)
        val btnSairGameOver = findViewById<Button>(R.id.btnSairGameOver)
        btnJogarNovamente.setOnClickListener {
            layoutGameOverMenu.visibility = View.GONE
            btnPause.visibility = View.VISIBLE
            gameView.resetarJogoCompleto()
            // Retomar áudio caso tenha parado no Game Over
            mediaPlayer?.start()
            gameView.resume()
        }
        btnSairGameOver.setOnClickListener { finish() }
    }

    private fun mostrarEcraGameOver(pontuacaoFinal: Int) {
        gameView.pause()
        mediaPlayer?.pause() // Opcional: pausar música no fim do curso

        btnPause.visibility = View.GONE
        val melhorPontuacaoAtual = sharedPreferences.getInt("melhor_pontuacao", 0)
        if (pontuacaoFinal > melhorPontuacaoAtual) {
            sharedPreferences.edit().putInt("melhor_pontuacao", pontuacaoFinal).apply()
            tvMelhorPontuacaoLabel.text = "NOVO RECORDE!"
        } else {
            tvMelhorPontuacaoLabel.text = "Melhor Pontuação: $melhorPontuacaoAtual"
        }
        tvPontuacaoFinalValor.text = pontuacaoFinal.toString()
        runOnUiThread { layoutGameOverMenu.visibility = View.VISIBLE }
    }

    override fun onResume() {
        super.onResume()
        // Se não estiver no menu de pausa, retoma a música ao voltar para a app
        if (layoutPauseMenu.visibility != View.VISIBLE) {
            mediaPlayer?.start()
        }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        soundPool.release()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            if (lux < 5.0f) {
                runOnUiThread { abrirMenuPausa() }
            }
        }

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val aceleracaoBruta = sqrt((event.values[0] * event.values[0] + y * y + event.values[2] * event.values[2]).toDouble()).toFloat()

            if (gameView.isNoArOuARolar()) {
                gameView.aplicarEfeitoTilt(y)
                forcaSuave = 0f
                return
            }

            if (gameView.isProntaParaTacada()) {
                val tempoAtual = System.currentTimeMillis()
                if (!capturandoAbanao) {
                    forcaSuave = (aceleracaoBruta * alpha) + (forcaSuave * (1 - alpha))
                    if (aceleracaoBruta > 16.0f && (tempoAtual - ultimoTempoTacada) > COOLDOWN_MS) {
                        capturandoAbanao = true
                        tempoInicioAbanao = tempoAtual
                        forcaMaximaCapturada = aceleracaoBruta
                    } else {
                        gameView.atualizarForcaVisual(forcaSuave)
                    }
                } else {
                    if (aceleracaoBruta > forcaMaximaCapturada) forcaMaximaCapturada = aceleracaoBruta
                    gameView.atualizarForcaVisual(forcaMaximaCapturada)
                    if (tempoAtual - tempoInicioAbanao > 850) {
                        capturandoAbanao = false
                        ultimoTempoTacada = tempoAtual
                        gameView.iniciarContagemTacada(forcaMaximaCapturada)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}