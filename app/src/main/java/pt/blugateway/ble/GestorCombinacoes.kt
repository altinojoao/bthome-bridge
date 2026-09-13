package pt.blugateway.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import pt.blugateway.data.Combinacao

/**
 * Acumula os índices de evento que chegam para um comando, dentro de
 * uma janela de tempo, e verifica se a sequência corresponde a alguma
 * combinação definida no perfil.
 *
 * O buffer é persistido em SharedPreferences (não apenas em memória)
 * porque, com o ecrã bloqueado, o processo pode ser morto e reiniciado
 * entre um clique e o seguinte da mesma combinação -- um HashMap em
 * memória perderia a sequência acumulada e a combinação nunca
 * corresponderia. A persistência garante que o 2º/3º clique de uma
 * combinação continua a sequência mesmo que o processo tenha
 * reiniciado entretanto.
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

    private const val PREFS = "combinacoes_buffer"

    private val temporizadores = HashMap<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var contextoApp: Context? = null

    /** Chamado uma vez ao arrancar a app/serviço para poder persistir. */
    fun inicializa(context: Context) {
        contextoApp = context.applicationContext
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lerSequencia(context: Context, mac: String): MutableList<Int> {
        val raw = prefs(context).getString("seq_$mac", null) ?: return mutableListOf()
        val tsRaw = prefs(context).getLong("ts_$mac", 0L)
        // Se a última atualização foi há muito tempo (> 60s), a sequência
        // é considerada expirada mesmo que o temporizador em memória se
        // tenha perdido com o reinício do processo.
        if (System.currentTimeMillis() - tsRaw > 60_000L) return mutableListOf()
        return try {
            raw.split(",").filter { it.isNotBlank() }.map { it.toInt() }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    private fun guardarSequencia(context: Context, mac: String, seq: List<Int>) {
        prefs(context).edit()
            .putString("seq_$mac", seq.joinToString(","))
            .putLong("ts_$mac", System.currentTimeMillis())
            .apply()
    }

    private fun limparSequencia(context: Context, mac: String) {
        prefs(context).edit().remove("seq_$mac").remove("ts_$mac").apply()
    }

    /** Devolve a combinação disparada, ou null se o clique só ficou
     *  acumulado no buffer (a aguardar mais cliques ou reiniciado). */
    fun acumula(context: Context, mac: String, combinacoes: List<Combinacao>, janelaMs: Long, indiceEvento: Int): Combinacao? {
        contextoApp = context.applicationContext
        val sequencia = lerSequencia(context, mac)

        temporizadores[mac]?.let { handler.removeCallbacks(it) }
        sequencia.add(indiceEvento)

        var correspondeExata: Combinacao? = null
        var aindaPossivel = false

        for (comb in combinacoes) {
            if (comb.sequencia == sequencia) {
                correspondeExata = comb
                break
            }
            if (ePrefixo(sequencia, comb.sequencia)) {
                aindaPossivel = true
            }
        }

        if (correspondeExata != null) {
            limparSequencia(context, mac)
            temporizadores.remove(mac)
            return correspondeExata
        }

        if (!aindaPossivel) {
            // a sequencia acumulada ja nao pode virar nenhuma combinacao
            // conhecida -- recomeca com este clique como primeiro passo
            sequencia.clear()
            sequencia.add(indiceEvento)
        }

        guardarSequencia(context, mac, sequencia)

        val runnable = Runnable { limparSequencia(context, mac); temporizadores.remove(mac) }
        temporizadores[mac] = runnable
        handler.postDelayed(runnable, janelaMs)

        return null
    }

    /** Sobrecarga de compatibilidade que usa o último contexto conhecido. */
    fun acumula(mac: String, combinacoes: List<Combinacao>, janelaMs: Long, indiceEvento: Int): Combinacao? {
        val ctx = contextoApp ?: return null
        return acumula(ctx, mac, combinacoes, janelaMs, indiceEvento)
    }

    /** true se "curta" é o início exato de "longa" (e mais curta ou igual). */
    private fun ePrefixo(curta: List<Int>, longa: List<Int>): Boolean {
        if (curta.size > longa.size) return false
        for (i in curta.indices) if (curta[i] != longa[i]) return false
        return true
    }

    /** Indica se há uma sequência em curso para este comando (para a UI
     *  mostrar "a aguardar" no diagnóstico). */
    fun sequenciaEmCurso(mac: String): Boolean {
        val ctx = contextoApp ?: return false
        return lerSequencia(ctx, mac).isNotEmpty()
    }
}
