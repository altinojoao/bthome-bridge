package pt.blugateway.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.blugateway.R
import pt.blugateway.data.Repositorio
import pt.blugateway.net.ExecutorAcoes

/**
 * Processa uma trama BTHome descodificada: deduplica ecos, distingue
 * candidato de comando associado, e dispara as ações configuradas.
 *
 * A deduplicação por PID é gravada em SharedPreferences (não em
 * memória) porque, tal como documentado no projeto original
 * bthome-bridge, o Android pode matar o processo entre pressões do
 * botão — se guardássemos o último PID só em memória, cada
 * reativação do processo trataria o eco como clique novo.
 */
object ProcessadorClique {

    private const val PREFS = "blugateway_dedup"

    fun processa(context: Context, mac: String, nome: String, trama: TramaBTHome, bytesOriginais: ByteArray, rssi: Int) {
        val prefsDedup = requireNotNull(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) {
            "getSharedPreferences nunca deveria devolver null"
        }
        val chavePid = "pid_$mac"

        val novoPacote = if (trama.pid != null) {
            val ultimo = prefsDedup.getInt(chavePid, -1)
            if (ultimo == trama.pid) {
                false
            } else {
                prefsDedup.edit().putInt(chavePid, trama.pid).apply()
                true
            }
        } else {
            true
        }

        val repo = Repositorio(context)
        val comandoExistente = repo.acharComandoPorMac(mac)

        if (comandoExistente != null) {
            repo.atualizaSinal(mac, rssi, trama.bateria)
            if (rssi != null) GestorAlcance.registaLeituraRssi(mac, rssi)
            RegistoDiagnostico.regista(context, "sinal[$mac]: rssi=$rssi recebido, evento=${trama.evento}")

            if (comandoExistente.modoBeaconTrajeto) {
                CoroutineScope(Dispatchers.IO).launch {
                    GestorTrajeto.registaPontoDeBeaconSeNecessario(context, comandoExistente)
                    // so faz sentido verificar semelhanca com os cenarios de
                    // trajeto depois de ter tentado gravar um ponto novo --
                    // GestorSemelhancaTrajeto.verificaCenarios ja verifica
                    // por si so se ha cenarios definidos para este comando,
                    // sai cedo (sem trabalho extra) se nao houver nenhum
                    GestorSemelhancaTrajeto.verificaCenarios(context, comandoExistente.mac)
                }
            }

            if (novoPacote && trama.evento != null) {
                val indice = pt.blugateway.data.TipoClique.indiceDeCodigo(trama.evento)
                if (indice != -1) {
                    val perfil = repo.acharPerfil(comandoExistente.perfilId)

                    if (perfil != null && perfil.modoCombinacao) {
                        val combinacaoDisparada = GestorCombinacoes.acumula(
                            mac, perfil.combinacoes, perfil.janelaCombinacaoMs, indice
                        )
                        if (combinacaoDisparada != null) {
                            RegistoEventos.adicionaCombinacao(combinacaoDisparada.nome)
                            repo.registaCombinacaoDisparada(mac, combinacaoDisparada.nome)
                            DiagnosticoEstado.atualizaCombinacao(nome, rssi, trama, bytesOriginais, combinacaoDisparada.nome)
                            GestorSons.tocaCombinacao(combinacaoDisparada.sequencia)

                            CoroutineScope(Dispatchers.IO).launch {
                                ExecutorAcoes.executaLista(
                                    context = context,
                                    acoes = combinacaoDisparada.acoes,
                                    evento = combinacaoDisparada.nome,
                                    codigo = trama.evento,
                                    mac = mac,
                                    bateria = trama.bateria ?: comandoExistente.bateria,
                                    rssi = rssi,
                                    incluirLocalizacao = comandoExistente.incluirLocalizacao
                                )
                            }
                        } else {
                            DiagnosticoEstado.atualizaEspera(nome, rssi, trama, bytesOriginais)
                        }
                    } else {
                        RegistoEventos.adiciona(indiceParaNomeEvento(context, indice), rssi)
                        DiagnosticoEstado.atualiza(nome, rssi, trama, bytesOriginais, indice)
                        GestorSons.tocaClique(indice)

                        CoroutineScope(Dispatchers.IO).launch {
                            ExecutorAcoes.executa(
                                context = context,
                                comando = comandoExistente,
                                indiceEvento = indice,
                                evento = indiceParaNomeEvento(context, indice),
                                codigo = trama.evento,
                                mac = mac,
                                bateria = trama.bateria ?: comandoExistente.bateria,
                                rssi = rssi
                            )
                        }
                    }
                }
            }
            return
        }

        // desconhecido: só entra na lista de candidatos se o modo de
        // emparelhamento estiver aberto (utilizador a tocar no botão de procura)
        if (!EstadoEmparelhamento.aberto) return
        if (trama.evento == null) return

        if (novoPacote) {
            DiagnosticoEstado.atualiza(nome, rssi, trama, bytesOriginais, pt.blugateway.data.TipoClique.indiceDeCodigo(trama.evento))
        }

        CandidatosEstado.adicionaOuAtualiza(mac, nome, rssi, trama.bateria)
    }

    private fun indiceParaNomeEvento(context: Context, indice: Int): String {
        val ids = intArrayOf(
            R.string.ev0, R.string.ev1, R.string.ev2, R.string.ev3,
            R.string.ev4, R.string.ev5, R.string.ev6
        )
        return context.getString(ids.getOrElse(indice) { R.string.ev0 })
    }
}
