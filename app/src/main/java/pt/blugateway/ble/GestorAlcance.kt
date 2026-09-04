package pt.blugateway.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import pt.blugateway.data.Comando
import pt.blugateway.data.PeriodoAgenda
import pt.blugateway.data.Repositorio
import java.util.Calendar

/**
 * Alarme de "fora de alcance": para comandos com alertaAlcance=true,
 * verifica periodicamente se algum sinal (clique OU beacon) chegou
 * dentro do limite de tempo configurado (comando.tempoLimiteMs), OU
 * se o ultimo RSSI recebido e pior que o limite configurado
 * (comando.rssiLimite). So dispara dentro da agenda semanal do
 * comando (ou sempre, se agendaSempreAtiva=true). Toca um alarme
 * sonoro -- repetido enquanto continuar fora de alcance, e desliga-se
 * sozinho assim que o comando voltar a emitir (ver
 * Repositorio.atualizaSinal, que limpa foraDeAlcance).
 *
 * Depende do modo beacon do próprio botão Shelly estar ativado --
 * sem beacon, o único sinal é o clique, e não há forma de distinguir
 * "fora de alcance" de "simplesmente não foi premido". Isto é
 * avisado ao utilizador na UI, não imposto aqui.
 */
object GestorAlcance {

    private const val TAG = "GestorAlcance"
    private const val INTERVALO_VERIFICACAO_MS = 15_000L
    private const val INTERVALO_REPETICAO_ALARME_MS = 60_000L

    private var handler: Handler? = null
    @Volatile private var contexto: Context? = null
    private val ultimoAlarmeTocado = HashMap<String, Long>()

    // exige que a condicao de "fora de alcance" se confirme em DUAS
    // verificacoes consecutivas (INTERVALO_VERIFICACAO_MS = 15s entre
    // elas) antes de disparar -- o botao Shelly BLU documenta um
    // intervalo de beacon de 8s, muito mais curto que qualquer
    // tempoLimiteMs razoavel, mas pode ocasionalmente falhar um
    // ciclo de emissao (confirmado por registo de diagnostico real:
    // uma lacuna isolada de ~68s entre anuncios, com o telemovel a
    // continuar a captar outros dispositivos normalmente na mesma
    // janela). Uma unica falha pontual do dispositivo ja nao chega
    // para soar o alarme -- so uma falha que persista na verificacao
    // seguinte, dando tempo real ao dispositivo de recuperar entretanto.
    private const val VERIFICACOES_CONSECUTIVAS_NECESSARIAS = 2
    private val contagemForaDeAlcance = HashMap<String, Int>()

    private val verificacaoRunnable = object : Runnable {
        override fun run() {
            verificaTodos()
            handler?.postDelayed(this, INTERVALO_VERIFICACAO_MS)
        }
    }

    fun inicia(context: Context) {
        contexto = context.applicationContext
        if (handler != null) return
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed(verificacaoRunnable, INTERVALO_VERIFICACAO_MS)
    }

    private fun paraMinutos(hhmm: String): Int {
        val partes = hhmm.split(":")
        return (partes.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (partes.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** dia 0=domingo .. 6=sabado (mesma convencao usada na interface HTML) */
    private fun diaDaSemanaAtual0(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1

    private fun horaAtualEmMinutos(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Verifica se o instante atual esta dentro de algum periodo do
     * dia -- incluindo periodos que atravessam a meia-noite, e
     * verificando tambem o dia ANTERIOR para cobrir a madrugada de
     * um periodo que comecou ontem (ex: 22:00-06:00 de sexta continua
     * ativo na madrugada de sabado).
     */
    private fun agendaAtivaAgora(agendaDias: Map<Int, List<PeriodoAgenda>>, diaSemana0: Int, horaMin: Int): Boolean {
        val periodosHoje = agendaDias[diaSemana0] ?: emptyList()
        for (p in periodosHoje) {
            val ini = paraMinutos(p.inicio)
            val fim = paraMinutos(p.fim)
            if (fim > ini) {
                if (horaMin in ini until fim) return true
            } else {
                if (horaMin >= ini || horaMin < fim) return true
            }
        }
        val diaAnterior = (diaSemana0 + 6) % 7
        val periodosOntem = agendaDias[diaAnterior] ?: emptyList()
        for (p in periodosOntem) {
            val ini = paraMinutos(p.inicio)
            val fim = paraMinutos(p.fim)
            if (fim <= ini && horaMin < fim) return true
        }
        return false
    }

    private fun dentroDaAgenda(comando: Comando): Boolean {
        if (comando.agendaSempreAtiva) return true
        return agendaAtivaAgora(comando.agendaDias, diaDaSemanaAtual0(), horaAtualEmMinutos())
    }

    private fun verificaTodos() {
        val ctx = contexto ?: return
        val repo = Repositorio(ctx)
        val agora = System.currentTimeMillis()

        for (comando in repo.comandos.value) {
            if (!comando.alertaAlcance) continue

            if (!dentroDaAgenda(comando)) {
                // fora do horario configurado -- nunca dispara o alarme
                // aqui, e limpa o estado se estava marcado como fora
                // de alcance de uma verificacao anterior dentro do horario
                if (comando.foraDeAlcance) {
                    repo.defineForaDeAlcance(comando.mac, false)
                    ultimoAlarmeTocado.remove(comando.mac)
                }
                continue
            }

            val ultimoSinal = comando.ultimoSinalEm
            val semSinalDemasiadoTempo = ultimoSinal != null && (agora - ultimoSinal) >= comando.tempoLimiteMs
            val sinalFraco = comando.rssi != null && comando.rssi!! < comando.rssiLimite
            val foraDeAlcanceAgora = ultimoSinal != null && (semSinalDemasiadoTempo || sinalFraco)

            // diagnostico temporario: grava o estado exato de cada
            // verificacao, para investigar disparos do alarme sem
            // sentido aparente (comando perto do telemovel, ecra
            // ligado ou desligado)
            val tempoDesdeUltimoSinal = ultimoSinal?.let { agora - it }
            val contagemAntes = contagemForaDeAlcance[comando.mac] ?: 0
            RegistoDiagnostico.regista(
                ctx,
                "alcance[${comando.mac}]: rssi=${comando.rssi} limite=${comando.rssiLimite} " +
                    "tempoDesdeSinal=${tempoDesdeUltimoSinal}ms limite=${comando.tempoLimiteMs}ms " +
                    "semSinal=$semSinalDemasiadoTempo sinalFraco=$sinalFraco -> foraDeAlcance=$foraDeAlcanceAgora " +
                    "(confirmacoes=$contagemAntes/$VERIFICACOES_CONSECUTIVAS_NECESSARIAS)"
            )

            if (foraDeAlcanceAgora) {
                val contagemAtual = (contagemForaDeAlcance[comando.mac] ?: 0) + 1
                contagemForaDeAlcance[comando.mac] = contagemAtual

                if (contagemAtual < VERIFICACOES_CONSECUTIVAS_NECESSARIAS) {
                    // primeira vez que a condicao se verifica -- ainda nao
                    // confirma, da ao dispositivo oportunidade de recuperar
                    // ate a proxima verificacao (15s depois)
                    continue
                }

                val mudouAgora = repo.defineForaDeAlcance(comando.mac, true)
                val ultimoAlarme = ultimoAlarmeTocado[comando.mac] ?: 0L
                val tempoDesdeUltimoAlarme = agora - ultimoAlarme

                if (mudouAgora || tempoDesdeUltimoAlarme >= INTERVALO_REPETICAO_ALARME_MS) {
                    val motivo = when {
                        semSinalDemasiadoTempo && sinalFraco -> "sem sinal e RSSI fraco"
                        semSinalDemasiadoTempo -> "sem sinal há ${agora - ultimoSinal!!}ms"
                        else -> "RSSI fraco (${comando.rssi} < ${comando.rssiLimite})"
                    }
                    Log.w(TAG, "${comando.nome} fora de alcance: $motivo")
                    RegistoEventos.adicionaAlertaAlcance(comando.nome)
                    GestorSons.tocaAlarmeAlcance()
                    ultimoAlarmeTocado[comando.mac] = agora
                }
            } else {
                contagemForaDeAlcance.remove(comando.mac)
                if (comando.foraDeAlcance) {
                    // ainda nao devia acontecer aqui (atualizaSinal ja limpa
                    // foraDeAlcance ao receber um pacote), mas serve de
                    // salvaguarda caso o estado fique desalinhado
                    repo.defineForaDeAlcance(comando.mac, false)
                    ultimoAlarmeTocado.remove(comando.mac)
                }
            }
        }
    }
}
