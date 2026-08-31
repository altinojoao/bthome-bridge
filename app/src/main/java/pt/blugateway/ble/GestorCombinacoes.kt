package pt.blugateway.ble

import android.os.Handler
import android.os.Looper
import pt.blugateway.data.Combinacao

/**
 * Porta direta do bufferCombinacao / acumulaCombinacao() já validado
 * na versão web: acumula os índices de evento que chegam para um
 * comando, dentro de uma janela de tempo, e verifica se a sequência
 * corresponde a alguma combinação definida no perfil.
 *
 * Regras (idênticas à versão web):
 * - Corresponde exatamente a uma combinação -> dispara e limpa.
 * - Ainda é prefixo de alguma combinação possível -> espera mais um
 *   clique, reiniciando o temporizador da janela.
 * - Já não pode corresponder a nada -> reinicia a sequência a partir
 *   deste clique (não descarta, tenta de novo como primeiro passo).
 * - Janela expira sem corresponder -> limpa sem disparar nada.
 */
object GestorCombinacoes {

    private data class Buffer(val sequencia: MutableList<Int> = mutableListOf())

    private val buffers = HashMap<String, Buffer>()
    private val temporizadores = HashMap<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

    /** Devolve a combinação disparada, ou null se o clique só ficou
     *  acumulado no buffer (a aguardar mais cliques ou reiniciado). */
    fun acumula(mac: String, combinacoes: List<Combinacao>, janelaMs: Long, indiceEvento: Int): Combinacao? {
        val buffer = buffers.getOrPut(mac) { Buffer() }

        temporizadores[mac]?.let { handler.removeCallbacks(it) }
        buffer.sequencia.add(indiceEvento)

        var correspondeExata: Combinacao? = null
        var aindaPossivel = false

        for (comb in combinacoes) {
            if (comb.sequencia == buffer.sequencia) {
                correspondeExata = comb
                break
            }
            if (ePrefixo(buffer.sequencia, comb.sequencia)) {
                aindaPossivel = true
            }
        }

        if (correspondeExata != null) {
            buffers.remove(mac)
            temporizadores.remove(mac)
            return correspondeExata
        }

        if (!aindaPossivel) {
            // a sequencia acumulada ja nao pode virar nenhuma combinacao
            // conhecida -- recomeca com este clique como primeiro passo
            buffer.sequencia.clear()
            buffer.sequencia.add(indiceEvento)
        }

        val runnable = Runnable { buffers.remove(mac); temporizadores.remove(mac) }
        temporizadores[mac] = runnable
        handler.postDelayed(runnable, janelaMs)

        return null
    }

    /** true se "curta" é o início exato de "longa" (e mais curta ou igual). */
    private fun ePrefixo(curta: List<Int>, longa: List<Int>): Boolean {
        if (curta.size > longa.size) return false
        for (i in curta.indices) if (curta[i] != longa[i]) return false
        return true
    }

    /** Indica se há uma sequência em curso para este comando (para a UI
     *  mostrar "a aguardar" no diagnóstico, tal como na versão web). */
    fun sequenciaEmCurso(mac: String): Boolean = buffers.containsKey(mac)
}
