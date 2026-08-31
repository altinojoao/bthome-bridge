package pt.blugateway.ble

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Feedback sonoro dos cliques: em vez de sons distintos por tipo,
 * usa-se o MESMO bip repetido N vezes, com a duração do bip a
 * distinguir curto/longo -- espelha a contagem visual já usada nos
 * símbolos (pontos/mãos):
 *
 *   Simples          -> 1 bip curto
 *   Duplo             -> 2 bips curtos
 *   Triplo            -> 3 bips curtos
 *   Longo             -> 1 bip longo
 *   Longo duplo       -> 2 bips longos
 *   Longo triplo      -> 3 bips longos
 *   Manter premido    -> 1 bip longo (mesma "contagem" do Longo --
 *                        não tem número natural próprio)
 *
 * Uma Combinação soletra a sequência completa: toca o padrão de CADA
 * clique que a compõe, em ordem, com uma pausa maior entre cliques
 * consecutivos da sequência.
 */
object GestorSons {

    private const val DURACAO_BIP_CURTO_MS = 90
    private const val DURACAO_BIP_LONGO_MS = 260
    private const val PAUSA_ENTRE_BIPS_MS = 110L
    private const val PAUSA_ENTRE_CLIQUES_SEQUENCIA_MS = 320L

    // [contagem, ehLongo] por indice de TipoClique (0..6)
    private val PADRAO_POR_INDICE = arrayOf(
        1 to false, // SIMPLES
        2 to false, // DUPLO
        3 to false, // TRIPLO
        1 to true,  // LONGO
        2 to true,  // LONGO_DUPLO
        3 to true,  // LONGO_TRIPLO
        1 to true   // MANTER_PREMIDO
    )

    @Volatile private var somAtivo = true
    private val handler = Handler(Looper.getMainLooper())

    fun defineSomAtivo(ativo: Boolean) {
        somAtivo = ativo
    }

    fun somAtivo(): Boolean = somAtivo

    /** Toca o padrão de bips do clique indicado (0..6). Chamada
     *  assíncrona -- agenda os bips no Handler principal e devolve
     *  de imediato, para nunca atrasar o processamento do clique. */
    fun tocaClique(indiceTipo: Int) {
        if (!somAtivo) return
        val (contagem, longo) = PADRAO_POR_INDICE.getOrNull(indiceTipo) ?: return
        agendaSequenciaDeBips(contagem, longo, atraso = 0L)
    }

    /** Toca a sequência completa de uma combinação: o padrão de cada
     *  clique da sequência, em ordem, com pausa maior entre eles. */
    fun tocaCombinacao(sequencia: List<Int>) {
        if (!somAtivo || sequencia.isEmpty()) return
        var atraso = 0L
        for (indiceTipo in sequencia) {
            val (contagem, longo) = PADRAO_POR_INDICE.getOrNull(indiceTipo) ?: continue
            agendaSequenciaDeBips(contagem, longo, atraso)
            val duracaoBip = if (longo) DURACAO_BIP_LONGO_MS else DURACAO_BIP_CURTO_MS
            val duracaoDesteClique = contagem * duracaoBip + (contagem - 1) * PAUSA_ENTRE_BIPS_MS
            atraso += duracaoDesteClique + PAUSA_ENTRE_CLIQUES_SEQUENCIA_MS
        }
    }

    private fun agendaSequenciaDeBips(contagem: Int, longo: Boolean, atraso: Long) {
        val duracaoBip = if (longo) DURACAO_BIP_LONGO_MS else DURACAO_BIP_CURTO_MS
        for (i in 0 until contagem) {
            val atrasoDoBip = atraso + i * (duracaoBip + PAUSA_ENTRE_BIPS_MS)
            handler.postDelayed({ tocaUmBip(duracaoBip) }, atrasoDoBip)
        }
    }

    private fun tocaUmBip(duracaoMs: Int) {
        try {
            // instancia nova por bip: ToneGenerator so deve tocar um tom
            // de cada vez, e reaproveitar a mesma instancia entre bips
            // proximos no tempo (dentro da janela do Handler) causa
            // cortes -- criar/libertar por bip e mais fiavel aqui.
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, duracaoMs)
            handler.postDelayed({ tg.release() }, (duracaoMs + 60).toLong())
        } catch (e: RuntimeException) {
            // ToneGenerator pode falhar a inicializar em alguns
            // dispositivos/streams de audio -- o som e so um extra,
            // nunca deve interromper o resto da app.
        }
    }

    /** Alarme de "comando fora de alcance" -- distinto dos bips de
     *  clique: 3 tons mais longos e agudos, no stream de ALARME
     *  (mais volume e prioridade que notificações), para chamar
     *  mais a atenção do que o feedback normal de um clique. */
    fun tocaAlarmeAlcance() {
        if (!somAtivo) return
        for (i in 0 until 3) {
            val atraso = i * 450L
            handler.postDelayed({ tocaUmTomAlarme() }, atraso)
        }
    }

    private fun tocaUmTomAlarme() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
            handler.postDelayed({ tg.release() }, 410L)
        } catch (e: RuntimeException) {
            // idem: som e um extra, nunca deve interromper a app
        }
    }
}
