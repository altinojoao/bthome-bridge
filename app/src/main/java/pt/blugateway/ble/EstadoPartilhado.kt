package pt.blugateway.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estes objetos vivem no processo da app. Quando a app está em
 * primeiro plano, a UI observa-os diretamente (StateFlow). Quando o
 * processo é recriado só para processar um broadcast do ScanReceiver
 * (ecrã apagado, app fechada), eles arrancam vazios/zerados — o que
 * é aceitável porque o que importa nesse caminho (persistência de
 * comandos, perfis, deduplicação, execução de ações) já está todo em
 * SharedPreferences, não nestes objetos. Estes só existem para dar
 * feedback visual quando há um ecrã a mostrar.
 */

data class Candidato(val mac: String, val nome: String, var rssi: Int?, var bateria: Int?)

object EstadoEmparelhamento {
    @Volatile var aberto: Boolean = false
}

object CandidatosEstado {
    private val _lista = MutableStateFlow<List<Candidato>>(emptyList())
    val lista: StateFlow<List<Candidato>> = _lista

    fun adicionaOuAtualiza(mac: String, nome: String, rssi: Int?, bateria: Int?) {
        val atual = _lista.value.toMutableList()
        val idx = atual.indexOfFirst { it.mac == mac }
        if (idx == -1) {
            atual.add(Candidato(mac, nome, rssi, bateria))
        } else {
            atual[idx] = atual[idx].copy(rssi = rssi ?: atual[idx].rssi, bateria = bateria ?: atual[idx].bateria)
        }
        _lista.value = atual
    }

    fun remove(mac: String) {
        _lista.value = _lista.value.filter { it.mac != mac }
    }

    fun limpa() {
        _lista.value = emptyList()
    }
}

data class LinhaRegisto(val hora: String, val texto: String, val ok: Boolean = true)

object RegistoEventos {
    private val _linhas = MutableStateFlow<List<LinhaRegisto>>(emptyList())
    val linhas: StateFlow<List<LinhaRegisto>> = _linhas

    private const val MAX_LINHAS = 100

    fun adiciona(evento: String, rssi: Int?) {
        val hora = horaAtual()
        val texto = evento + (rssi?.let { "  $it dBm" } ?: "")
        adicionaLinha(hora, texto, true)
    }

    fun adicionaCombinacao(nomeCombinacao: String) {
        val hora = horaAtual()
        adicionaLinha(hora, "\uD83D\uDD17 $nomeCombinacao", true)
    }

    fun adicionaAlertaAlcance(nomeComando: String) {
        val hora = horaAtual()
        adicionaLinha(hora, "\uD83D\uDCE1 $nomeComando", false)
    }

    fun adicionaResultado(etiqueta: String, sucesso: Boolean, detalhe: String) {
        val hora = horaAtual()
        adicionaLinha(hora, "\u2192 $etiqueta  $detalhe", sucesso)
    }

    private fun adicionaLinha(hora: String, texto: String, ok: Boolean) {
        val nova = (listOf(LinhaRegisto(hora, texto, ok)) + _linhas.value).take(MAX_LINHAS)
        _linhas.value = nova
    }

    private fun horaAtual(): String {
        val c = java.util.Calendar.getInstance()
        return "%02d:%02d:%02d".format(
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE),
            c.get(java.util.Calendar.SECOND)
        )
    }
}

data class Diagnostico(
    val nome: String,
    val rssi: Int?,
    val indiceEvento: Int,
    val hora: String,
    val bytesHex: String,
    val eventoPos: Int,
    // preenchido quando o clique completou uma combinacao -- a UI
    // mostra o nome da combinacao em vez do nome do evento isolado
    val combinacao: String? = null,
    // true enquanto o clique faz parte de uma sequencia ainda a meio
    // (ainda nao completou nem foi descartada) -- a UI mostra "a aguardar"
    val emEspera: Boolean = false
)

object DiagnosticoEstado {
    private val _atual = MutableStateFlow<Diagnostico?>(null)
    val atual: StateFlow<Diagnostico?> = _atual

    fun atualiza(nome: String, rssi: Int?, trama: TramaBTHome, bytesOriginais: ByteArray, indiceEvento: Int) {
        _atual.value = Diagnostico(
            nome, rssi, indiceEvento, horaAtual(),
            BTHome.hexString(bytesOriginais), trama.eventoPos
        )
    }

    fun atualizaCombinacao(nome: String, rssi: Int?, trama: TramaBTHome, bytesOriginais: ByteArray, nomeCombinacao: String) {
        _atual.value = Diagnostico(
            nome, rssi, -1, horaAtual(),
            BTHome.hexString(bytesOriginais), trama.eventoPos,
            combinacao = nomeCombinacao
        )
    }

    fun atualizaEspera(nome: String, rssi: Int?, trama: TramaBTHome, bytesOriginais: ByteArray) {
        _atual.value = Diagnostico(
            nome, rssi, -1, horaAtual(),
            BTHome.hexString(bytesOriginais), trama.eventoPos,
            emEspera = true
        )
    }

    private fun horaAtual(): String {
        val c = java.util.Calendar.getInstance()
        return "%02d:%02d:%02d".format(
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE),
            c.get(java.util.Calendar.SECOND)
        )
    }
}
