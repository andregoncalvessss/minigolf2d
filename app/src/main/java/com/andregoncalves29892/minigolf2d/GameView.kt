package com.andregoncalves29892.minigolf2d

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // --- PAINTS ---
    private val paintCeu = Paint()
    private val paintTerra = Paint().apply { isAntiAlias = true; pathEffect = CornerPathEffect(60f) }
    private val paintRelva = Paint().apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        isAntiAlias = true; pathEffect = CornerPathEffect(60f)
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
    }
    private val paintBola = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val paintBolaContorno = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val paintAura = Paint().apply { color = Color.parseColor("#FFEB3B"); style = Paint.Style.STROKE; isAntiAlias = true }

    private val paintTrajetoriaPontos = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; setShadowLayer(4f, 0f, 2f, Color.BLACK); isAntiAlias = true }
    private val paintTrajetoriaCauda = Paint().apply {
        color = Color.parseColor("#CCFFFFFF"); style = Paint.Style.STROKE; strokeWidth = 12f; strokeCap = Paint.Cap.ROUND
        setShadowLayer(4f, 0f, 2f, Color.BLACK); isAntiAlias = true
    }

    private val paintBarraFundo = Paint().apply { color = Color.parseColor("#44000000") }
    private val paintBarraVermelha = Paint().apply { color = Color.RED }
    private val paintTextoTerra = Paint().apply { color = Color.parseColor("#60FFFFFF"); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val paintTextoAbana = Paint().apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val paintTextoAbanaContorno = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 8f; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val paintMsgFundo = Paint().apply { color = Color.parseColor("#AA000000") }
    private val paintMsgTexto = Paint().apply { color = Color.WHITE; textSize = 60f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val paintPontuacao = Paint().apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); isAntiAlias = true; setShadowLayer(4f, 2f, 2f, Color.BLACK) }
    private val paintContagemStroke = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 20f; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val paintContagemFill = Paint().apply { color = Color.parseColor("#FF5722"); style = Paint.Style.FILL; typeface = Typeface.create("sans-serif-black", Typeface.BOLD); textAlign = Paint.Align.CENTER; isAntiAlias = true }

    // --- ESTADO GERAL ---
    enum class EstadoJogo { MIRA, CONTAGEM, A_ROLAR, VITORIA, FORA_LIMITES, FIM_JOGO }
    private var estadoAtual = EstadoJogo.MIRA
    private var isPaused = false
    private var pontuacao = 100
    private var nivelAtual = 1

    var onGameOver: ((Int) -> Unit)? = null
    var onShotFired: (() -> Unit)? = null

    private var bolaX = 0f; private var bolaY = 0f; private var velX = 0f; private var velY = 0f
    private var raioBola = 0f; private var percentagemForca = 0f; private var jogoIniciado = false
    private var tempoAnimacao = 0f; private var tempoContagem = 0f; private var forcaGuardada = 0f; private var tempoMensagem = 0L

    private var dirXNormalizada = 1f; private var dirYNormalizada = -0.5f
    private var touchStartX = 0f; private var touchStartY = 0f
    private var distanciaDedoVisual = 150f
    private var buracoX = 0f; private var buracoY = 0f; private var buracoRaio = 0f
    private var tiltAtual = 0f

    private var mapaRatios = listOf<PointF>()
    private var corCeuTopo = 0; private var corCeuBase = 0
    private var corTerraTopo = 0; private var corTerraBase = 0
    private var corRelva = 0
    private var startXRatio = 0.15f; private var buracoXRatio = 0.85f

    private var gravidade = 0f; private val FORCA_MAXIMA = 38f; private val amortecimentoY = 0.35f
    private var atritoRelva = 0.94f; private val LIMIAR_SALTO = 1.2f

    private val loop = object : Runnable {
        override fun run() {
            if (!isPaused) {
                when (estadoAtual) {
                    EstadoJogo.MIRA -> tempoAnimacao += 0.1f
                    EstadoJogo.CONTAGEM -> {
                        tempoAnimacao += 0.3f
                        tempoContagem -= 0.03f
                        if (tempoContagem <= 0f) efetuarDisparoFinal()
                    }
                    EstadoJogo.A_ROLAR -> atualizarFisica()
                    EstadoJogo.VITORIA -> {
                        bolaX += (buracoX - bolaX) * 0.15f
                        bolaY += ((buracoY + 5f) - bolaY) * 0.15f
                        if (raioBola > 0.5f) raioBola *= 0.85f
                        if (System.currentTimeMillis() - tempoMensagem > 2500) {
                            if (nivelAtual >= 5) {
                                estadoAtual = EstadoJogo.FIM_JOGO
                                onGameOver?.invoke(pontuacao)
                            } else {
                                nivelAtual++
                                jogoIniciado = false
                                resetarPartida()
                            }
                        }
                    }
                    EstadoJogo.FORA_LIMITES -> if (System.currentTimeMillis() - tempoMensagem > 2500) resetarPartida()
                    EstadoJogo.FIM_JOGO -> {}
                }
            }
            invalidate()
            handler?.postDelayed(this, 16)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler?.post(loop) }
    fun pause() { isPaused = true }
    fun resume() { isPaused = false }
    fun isPaused() = isPaused
    fun resetar() { pontuacao = 100; resetarPartida() }

    fun resetarJogoCompleto() {
        pontuacao = 100
        nivelAtual = 1
        jogoIniciado = false
        estadoAtual = EstadoJogo.MIRA
        resetarPartida()
    }

    private fun carregarDadosDoNivel(nivel: Int) {
        when (nivel) {
            1 -> {
                corCeuTopo = Color.parseColor("#81D4FA"); corCeuBase = Color.WHITE
                corTerraTopo = Color.parseColor("#5D4037"); corTerraBase = Color.parseColor("#2D1B16")
                corRelva = Color.parseColor("#7CB342"); atritoRelva = 0.94f
                startXRatio = 0.15f; buracoXRatio = 0.85f
                mapaRatios = listOf(PointF(-0.1f, 0.75f), PointF(0.2f, 0.70f), PointF(0.45f, 0.85f), PointF(0.7f, 0.65f), PointF(1.1f, 0.65f))
            }
            2 -> {
                corCeuTopo = Color.parseColor("#FF7B00"); corCeuBase = Color.parseColor("#6A0DAD")
                corTerraTopo = Color.parseColor("#B87333"); corTerraBase = Color.parseColor("#5C3A21")
                corRelva = Color.parseColor("#EEDC82"); atritoRelva = 0.90f
                startXRatio = 0.10f; buracoXRatio = 0.90f
                mapaRatios = listOf(PointF(-0.1f, 0.6f), PointF(0.15f, 0.6f), PointF(0.35f, 0.4f), PointF(0.50f, 0.8f), PointF(0.70f, 0.5f), PointF(0.80f, 0.8f), PointF(0.90f, 0.3f), PointF(1.1f, 0.3f))
            }
            3 -> {
                corCeuTopo = Color.parseColor("#9E9E9E"); corCeuBase = Color.parseColor("#E0E0E0")
                corTerraTopo = Color.parseColor("#B3E5FC"); corTerraBase = Color.parseColor("#4FC3F7")
                corRelva = Color.parseColor("#E3F2FD"); atritoRelva = 0.99f
                startXRatio = 0.10f; buracoXRatio = 0.90f
                mapaRatios = listOf(PointF(-0.1f, 0.2f), PointF(0.3f, 0.85f), PointF(1.1f, 0.85f))
            }
            4 -> {
                corCeuTopo = Color.parseColor("#000000"); corCeuBase = Color.parseColor("#1A1A1A")
                corTerraTopo = Color.parseColor("#212121"); corTerraBase = Color.parseColor("#000000")
                corRelva = Color.parseColor("#39FF14"); atritoRelva = 0.94f
                startXRatio = 0.15f; buracoXRatio = 0.85f
                mapaRatios = listOf(PointF(-0.1f, 0.6f), PointF(0.42f, 0.6f), PointF(0.43f, 3.0f), PointF(0.57f, 3.0f), PointF(0.58f, 0.6f), PointF(1.1f, 0.6f))
            }
            5 -> {
                corCeuTopo = Color.parseColor("#8B0000"); corCeuBase = Color.parseColor("#3E0000")
                corTerraTopo = Color.parseColor("#111111"); corTerraBase = Color.parseColor("#000000")
                corRelva = Color.parseColor("#FF4500"); atritoRelva = 0.94f
                startXRatio = 0.15f; buracoXRatio = 0.85f
                mapaRatios = listOf(PointF(-0.1f, 0.5f), PointF(0.2f, 0.9f), PointF(0.65f, 0.9f), PointF(0.82f, 0.3f), PointF(0.88f, 0.3f), PointF(1.05f, 0.9f), PointF(1.2f, 0.9f))
            }
            else -> { nivelAtual = 1; carregarDadosDoNivel(1) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (estadoAtual != EstadoJogo.MIRA || isPaused) return true
        if (event.action == MotionEvent.ACTION_DOWN) { touchStartX = event.x; touchStartY = event.y }
        if (event.action == MotionEvent.ACTION_MOVE) {
            val dx = touchStartX - event.x; val dy = touchStartY - event.y
            val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist > 10f) { dirXNormalizada = dx / dist; dirYNormalizada = kotlin.math.min(dy / dist, -0.1f); distanciaDedoVisual = kotlin.math.min(dist, 500f) }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val escalaBase = min(w, h)

        if (!jogoIniciado) {
            carregarDadosDoNivel(nivelAtual)
            paintCeu.shader = LinearGradient(0f, 0f, 0f, h * 0.8f, corCeuTopo, corCeuBase, Shader.TileMode.CLAMP)
            paintTerra.shader = LinearGradient(0f, h * 0.5f, 0f, h, corTerraTopo, corTerraBase, Shader.TileMode.CLAMP)
            paintRelva.color = corRelva
            bolaX = w * startXRatio; raioBola = escalaBase * 0.02f
            buracoX = w * buracoXRatio; buracoY = obterAlturaNoX(buracoX); buracoRaio = raioBola * 1.5f
            configurarPincéis(escalaBase, h)
            bolaY = obterAlturaNoX(bolaX) - raioBola; jogoIniciado = true
        }

        canvas.drawRect(0f, 0f, w, h, paintCeu)
        desenharCenarioResponsivo(canvas, w, h)
        desenharBuraco(canvas)
        desenharBandeira(canvas, buracoX, buracoY, escalaBase)

        if(estadoAtual != EstadoJogo.FIM_JOGO) {
            canvas.drawText("PONTOS: $pontuacao", 40f, 80f, paintPontuacao)
            canvas.drawText("NÍVEL $nivelAtual", 40f, 80f + paintPontuacao.textSize * 1.1f, paintPontuacao)

            // NOVO TEXTO: Instrução do Sensor de Luminosidade
            canvas.drawText("Encosta o dedo ao sensor de luminosidade para pausar", w / 2f, h * 0.94f, paintTextoTerra)
        }

        if (estadoAtual == EstadoJogo.MIRA || estadoAtual == EstadoJogo.CONTAGEM) {
            val pulso = (Math.sin(tempoAnimacao.toDouble()).toFloat() + 1f) / 2f
            canvas.drawCircle(bolaX, bolaY, raioBola + (pulso * escalaBase * 0.03f), paintAura.apply { alpha = ((1f - pulso) * 200).toInt() })
            desenharTrajetoria(canvas)
            if (estadoAtual == EstadoJogo.MIRA) {
                canvas.drawText("ABANA!", bolaX, bolaY - raioBola - 40f, paintTextoAbanaContorno)
                canvas.drawText("ABANA!", bolaX, bolaY - raioBola - 40f, paintTextoAbana)
            }
        }

        if (raioBola > 0.5f && estadoAtual != EstadoJogo.FIM_JOGO) {
            canvas.drawCircle(bolaX, bolaY, raioBola, paintBola)
            canvas.drawCircle(bolaX, bolaY, raioBola, paintBolaContorno)
        }

        if (estadoAtual == EstadoJogo.CONTAGEM) {
            val num = tempoContagem.toInt()
            val txt = if (num > 0) num.toString() else "FOGO!"
            paintContagemStroke.textSize = escalaBase * 0.4f; paintContagemFill.textSize = escalaBase * 0.4f
            canvas.drawText(txt, w/2f, h/2f + (escalaBase * 0.1f), paintContagemStroke)
            canvas.drawText(txt, w/2f, h/2f + (escalaBase * 0.1f), paintContagemFill)
        }

        if (estadoAtual == EstadoJogo.FORA_LIMITES) {
            canvas.drawRect(0f, h*0.4f, w, h*0.6f, paintMsgFundo)
            canvas.drawText("SAIU FORA DOS LIMITES!", w/2, h/2 + 20f, paintMsgTexto)
        }

        if (estadoAtual == EstadoJogo.VITORIA && raioBola < 2f) {
            canvas.drawRect(0f, h*0.4f, w, h*0.6f, paintMsgFundo)
            canvas.drawText("PARABÉNS!", w/2, h/2 + 20f, paintMsgTexto.apply { color = Color.YELLOW })
        }

        if(estadoAtual != EstadoJogo.FIM_JOGO) {
            desenharBarraForca(canvas, w, h, escalaBase)
        }
    }

    private fun desenharBuraco(canvas: Canvas) {
        canvas.drawOval(buracoX - buracoRaio, buracoY - 5f, buracoX + buracoRaio, buracoY + 15f, Paint().apply { color = Color.BLACK; isAntiAlias = true })
    }

    private fun configurarPincéis(escala: Float, h: Float) {
        paintRelva.strokeWidth = escala * 0.015f; paintTextoTerra.textSize = escala * 0.035f; // Texto um pouco menor para caber melhor
        paintTextoAbana.textSize = escala * 0.05f; paintTextoAbanaContorno.textSize = escala * 0.05f; paintAura.strokeWidth = escala * 0.01f; gravidade = h * 0.0010f; paintPontuacao.textSize = escala * 0.06f
    }

    private fun atualizarFisica() {
        if (estadoAtual != EstadoJogo.A_ROLAR) return
        val distAoBuraco = Math.abs(bolaX - buracoX)
        if (distAoBuraco < buracoRaio * 0.8f && Math.abs(velX) < 4f && Math.abs(bolaY - buracoY) < (raioBola * 2)) {
            velX = 0f; velY = 0f; estadoAtual = EstadoJogo.VITORIA; tempoMensagem = System.currentTimeMillis(); return
        }
        velY += gravidade; bolaX += velX; bolaY += velY
        if (bolaX < 0 || bolaX > width || bolaY > height + 50f) { estadoAtual = EstadoJogo.FORA_LIMITES; tempoMensagem = System.currentTimeMillis(); return }
        val yChao = obterAlturaNoX(bolaX)
        val noChao = bolaY + raioBola >= yChao - 10f
        if (noChao) velX += tiltAtual * 0.025f
        if (bolaY + raioBola > yChao) {
            bolaY = yChao - raioBola
            if (Math.abs(velY) < LIMIAR_SALTO) velY = 0f else velY = -velY * amortecimentoY
            velX *= atritoRelva
            if (Math.abs(velX) < 0.15f && Math.abs(tiltAtual) < 1.5f && velY == 0f) { velX = 0f; estadoAtual = EstadoJogo.MIRA }
        }
    }

    private fun resetarPartida() {
        val escalaBase = min(width.toFloat(), height.toFloat())
        raioBola = escalaBase * 0.02f; bolaX = width * startXRatio; bolaY = obterAlturaNoX(bolaX) - raioBola; velX = 0f; velY = 0f; tiltAtual = 0f
        if (estadoAtual != EstadoJogo.FIM_JOGO) estadoAtual = EstadoJogo.MIRA
    }

    private fun desenharTrajetoria(canvas: Canvas) {
        val tailX = bolaX - (dirXNormalizada * distanciaDedoVisual); val tailY = bolaY - (dirYNormalizada * distanciaDedoVisual)
        canvas.drawLine(bolaX, bolaY, tailX, tailY, paintTrajetoriaCauda)
        canvas.drawCircle(tailX, tailY, raioBola * 0.8f, Paint().apply { color = Color.parseColor("#AAFFFFFF"); style = Paint.Style.FILL; isAntiAlias = true })
        var simX = bolaX; var simY = bolaY; var simVelX = (dirXNormalizada * FORCA_MAXIMA) * (width / 1000f); var simVelY = (dirYNormalizada * FORCA_MAXIMA) * (height / 1500f)
        for (i in 1..400) {
            simVelY += gravidade; simX += simVelX; simY += simVelY
            val y = obterAlturaNoX(simX)
            if (simY + raioBola > y) {
                simY = y - raioBola
                if (Math.abs(simVelY) < LIMIAR_SALTO) simVelY = 0f else simVelY = -simVelY * amortecimentoY
                simVelX *= atritoRelva
                canvas.drawCircle(simX, simY, raioBola * 0.5f, paintTrajetoriaPontos)
                if (Math.abs(simVelX) < 0.4f && simVelY == 0f) break
            }
            if (simX < 0 || simX > width || simY > height) break
            if (i % 6 == 0) canvas.drawCircle(simX, simY, raioBola * 0.3f, paintTrajetoriaPontos)
        }
    }

    private fun desenharCenarioResponsivo(canvas: Canvas, w: Float, h: Float) {
        val pathRelva = Path(); pathRelva.moveTo(mapaRatios[0].x * w, mapaRatios[0].y * h)
        for (i in 1 until mapaRatios.size) pathRelva.lineTo(mapaRatios[i].x * w, mapaRatios[i].y * h)
        canvas.drawPath(pathRelva, paintRelva)
        val pathTerra = Path(pathRelva); pathTerra.lineTo(w + 200, h + 500); pathTerra.lineTo(-200f, h + 500); pathTerra.close()
        canvas.drawPath(pathTerra, paintTerra)
    }

    private fun desenharBarraForca(canvas: Canvas, w: Float, h: Float, escalaBase: Float) {
        val larguraBarra = w * 0.30f; val alturaBarra = escalaBase * 0.04f; val x = (w - larguraBarra) / 2f; val y = escalaBase * 0.05f
        canvas.drawRoundRect(x, y, x + larguraBarra, y + alturaBarra, 15f, 15f, paintBarraFundo)
        canvas.drawRoundRect(x, y, x + (larguraBarra * percentagemForca), y + alturaBarra, 15f, 15f, paintBarraVermelha)
    }

    private fun desenharBandeira(canvas: Canvas, x: Float, y: Float, escala: Float) {
        val mH = escala * 0.2f
        canvas.drawLine(x, y, x, y - mH, Paint().apply { color = Color.LTGRAY; strokeWidth = 6f })
        val flag = Path(); flag.moveTo(x, y - mH); flag.lineTo(x + escala * 0.08f, y - mH + mH/4); flag.lineTo(x, y - mH + mH/2); flag.close()
        canvas.drawPath(flag, Paint().apply { color = Color.RED })
    }

    private fun obterAlturaNoX(x: Float): Float {
        val xRatio = x / width
        for (i in 0 until mapaRatios.size - 1) {
            val p1 = mapaRatios[i]; val p2 = mapaRatios[i + 1]
            if (xRatio >= p1.x && xRatio <= p2.x) return (p1.y + (xRatio - p1.x) / (p2.x - p1.x) * (p2.y - p1.y)) * height
        }
        return height.toFloat()
    }

    fun isProntaParaTacada() = estadoAtual == EstadoJogo.MIRA
    fun isNoArOuARolar() = estadoAtual == EstadoJogo.A_ROLAR
    fun aplicarEfeitoTilt(tiltY: Float) { if (!isPaused) tiltAtual = tiltY }
    fun atualizarForcaVisual(acel: Float) { if (estadoAtual == EstadoJogo.MIRA && !isPaused) percentagemForca = ((acel - 9.8f).coerceAtLeast(0f) / 20f).coerceIn(0f, 1f) }
    fun iniciarContagemTacada(forca: Float) {
        if (estadoAtual == EstadoJogo.MIRA && !isPaused) { forcaGuardada = forca; tempoContagem = 3.99f; estadoAtual = EstadoJogo.CONTAGEM; pontuacao -= 2; if (pontuacao < 0) pontuacao = 0 }
    }
    private fun efetuarDisparoFinal() {
        val mult = ((forcaGuardada - 9.8f).coerceAtLeast(0f) / 20f).coerceIn(0.1f, 1.0f)
        velX = (dirXNormalizada * FORCA_MAXIMA * mult) * (width / 1000f); velY = (dirYNormalizada * FORCA_MAXIMA * mult) * (height / 1500f); estadoAtual = EstadoJogo.A_ROLAR
        onShotFired?.invoke()
    }
}