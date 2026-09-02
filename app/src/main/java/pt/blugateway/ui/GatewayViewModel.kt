package pt.blugateway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.blugateway.R
import pt.blugateway.ble.CandidatosEstado
import pt.blugateway.ble.DiagnosticoEstado
import pt.blugateway.ble.EstadoEmparelhamento
import pt.blugateway.ble.GestorScan
import pt.blugateway.ble.RegistoEventos
import pt.blugateway.data.Acao
import pt.blugateway.data.Combinacao
import pt.blugateway.data.Comando
import pt.blugateway.data.ContaShelly
import pt.blugateway.data.Perfil
import pt.blugateway.data.Repositorio

class GatewayViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    val perfis: StateFlow<List<Perfil>> = repo.perfis
    val comandos: StateFlow<List<Comando>> = repo.comandos
    val conta: StateFlow<ContaShelly> = repo.conta
    val candidatos = CandidatosEstado.lista
    val registo = RegistoEventos.linhas
    val diagnostico = DiagnosticoEstado.atual

    private val _perfilAtivoId = MutableStateFlow<String?>(null)
    val perfilAtivoId: StateFlow<String?> = _perfilAtivoId

    private val _emparelhamentoAberto = MutableStateFlow(false)
    val emparelhamentoAberto: StateFlow<Boolean> = _emparelhamentoAberto

    private val _notacaoPontos = MutableStateFlow(repo.notacaoPontos())
    val notacaoPontos: StateFlow<Boolean> = _notacaoPontos

    // idioma escolhido pelo utilizador; null enquanto seguir o idioma do
    // sistema (nunca foi trocado explicitamente no seletor)
    private val _idioma = MutableStateFlow(repo.idioma())
    val idioma: StateFlow<String?> = _idioma

    fun escolheIdioma(codigo: String) {
        _idioma.value = codigo
        repo.defineIdioma(codigo)
    }

    private val _temaClaro = MutableStateFlow(repo.temaClaro())
    val temaClaro: StateFlow<Boolean> = _temaClaro

    fun trocaTema() {
        val novo = !_temaClaro.value
        _temaClaro.value = novo
        repo.defineTemaClaro(novo)
    }

    private val _somAtivo = MutableStateFlow(repo.somAtivo())
    val somAtivo: StateFlow<Boolean> = _somAtivo

    fun trocaSom() {
        val novo = !_somAtivo.value
        _somAtivo.value = novo
        repo.defineSomAtivo(novo)
        pt.blugateway.ble.GestorSons.defineSomAtivo(novo)
    }

    /** Blocos desativados pelo utilizador no ecra "Blocos visiveis"
     *  (Escuta, Ultimo clique, Comandos, Configuracao, Registo) --
     *  guarda-se so os desativados, por omissao todos estao ativos. */
    private val _cardsDesativados = MutableStateFlow(repo.cardsDesativados())
    val cardsDesativados: StateFlow<Set<String>> = _cardsDesativados

    fun defineCardAtivo(idBloco: String, ativo: Boolean) {
        repo.defineCardAtivo(idBloco, ativo)
        _cardsDesativados.value = repo.cardsDesativados()
    }

    private val _confirmaApagarPerfil = MutableStateFlow<String?>(null)
    val confirmaApagarPerfil: StateFlow<String?> = _confirmaApagarPerfil

    private val _confirmaEsquecerComando = MutableStateFlow<String?>(null)
    val confirmaEsquecerComando: StateFlow<String?> = _confirmaEsquecerComando

    fun acharPerfil(id: String?): Perfil? = repo.acharPerfil(id)

    // --- emparelhamento ---

    fun alternaDetecao() {
        val ctx = getApplication<Application>()
        if (_emparelhamentoAberto.value) {
            _emparelhamentoAberto.value = false
            EstadoEmparelhamento.aberto = false
            CandidatosEstado.limpa()
            return
        }
        if (!GestorScan.suportaBLE()) {
            RegistoEventos.adicionaResultado(ctx.getString(R.string.bt_sem_suporte), false, "")
            return
        }
        if (!GestorScan.bluetoothLigado()) {
            RegistoEventos.adicionaResultado(ctx.getString(R.string.bt_desligado), false, "")
            return
        }
        _emparelhamentoAberto.value = true
        EstadoEmparelhamento.aberto = true
        RegistoEventos.adiciona(ctx.getString(R.string.carregue_botao), null)
        GestorScan.iniciaEscuta(ctx)
    }

    fun associaCandidato(mac: String, nome: String) {
        val perfilPadrao = perfis.value.firstOrNull()?.id ?: return
        repo.associaComando(mac, nome, perfilPadrao)
        CandidatosEstado.remove(mac)
    }

    /** Devolve false se o MAC for inválido ou já estiver associado --
     *  o chamador deve mostrar um erro nesse caso. */
    fun associaComandoManual(mac: String, nome: String, chaveHex: String?): Boolean {
        val perfilPadrao = perfis.value.firstOrNull()?.id ?: return false
        val nomeFinal = nome.trim().ifEmpty { "BTHome" }
        return repo.associaComandoManual(mac, nomeFinal, perfilPadrao, chaveHex)
    }

    // --- comandos ---

    fun pedeEsquecer(mac: String) {
        _confirmaEsquecerComando.value = mac
    }

    fun confirmaEsquecer(mac: String) {
        repo.esqueceComando(mac)
        _confirmaEsquecerComando.value = null
    }

    fun defineChaveEncriptacao(mac: String, chaveHex: String?) {
        repo.defineChaveEncriptacao(mac, chaveHex)
    }

    fun defineIncluirLocalizacao(mac: String, incluir: Boolean) {
        repo.defineIncluirLocalizacao(mac, incluir)
    }

    fun alternaModoBeaconTrajeto(mac: String, ativo: Boolean) {
        repo.alternaModoBeaconTrajeto(mac, ativo)
    }

    fun defineIntervaloBeaconTrajeto(mac: String, segundos: Int) {
        repo.defineIntervaloBeaconTrajeto(mac, segundos)
    }

    fun limpaTrajeto(mac: String) {
        repo.limpaTrajeto(mac)
    }

    fun historicoTrajeto(mac: String): List<pt.blugateway.data.PontoTrajeto> {
        return repo.historicoTrajeto(mac)
    }

    fun mudaPerfilComando(mac: String, perfilId: String) {
        repo.mudaPerfilComando(mac, perfilId)
    }

    fun alternaAlertaAlcance(mac: String, ativo: Boolean) {
        repo.alternaAlertaAlcance(mac, ativo)
    }

    fun defineTempoLimiteAlcance(mac: String, segundos: Int) {
        repo.defineTempoLimiteAlcance(mac, segundos)
    }

    fun defineRssiLimiteAlcance(mac: String, rssi: Int) {
        repo.defineRssiLimiteAlcance(mac, rssi)
    }

    fun alternaAgendaSempreAtiva(mac: String, sempreAtiva: Boolean) {
        repo.alternaAgendaSempreAtiva(mac, sempreAtiva)
    }

    fun adicionaPeriodoAgenda(mac: String, dia: Int, inicio: String, fim: String): Boolean {
        return repo.adicionaPeriodoAgenda(mac, dia, inicio, fim)
    }

    fun removePeriodoAgenda(mac: String, dia: Int, indice: Int) {
        repo.removePeriodoAgenda(mac, dia, indice)
    }

    // --- perfis ---

    fun abrePerfil(id: String) {
        _perfilAtivoId.value = id
    }

    fun fechaPerfil() {
        _perfilAtivoId.value = null
    }

    fun novoPerfil(nomeBase: String) {
        val nome = "$nomeBase ${perfis.value.size + 1}"
        val p = repo.novoPerfil(nome)
        abrePerfil(p.id)
    }

    fun renomeiaPerfil(id: String, novoNome: String) {
        val nome = novoNome.trim().ifEmpty { getApplication<Application>().getString(R.string.sem_nome) }
        repo.renomeiaPerfil(id, nome)
    }

    fun pedeApagarPerfil(id: String) {
        _confirmaApagarPerfil.value = id
    }

    fun confirmaApagar(id: String) {
        val substituto = repo.apagaPerfil(id)
        _confirmaApagarPerfil.value = null
        if (_perfilAtivoId.value == id) {
            _perfilAtivoId.value = substituto
        }
    }

    fun trocaNotacao() {
        val novo = !_notacaoPontos.value
        _notacaoPontos.value = novo
        repo.defineNotacao(novo)
    }

    // --- ações dentro de um perfil ---

    fun adicionaAcao(perfilId: String, indiceEvento: Int) {
        val perfil = repo.acharPerfil(perfilId) ?: return
        val lista = perfil.eventos[indiceEvento].toMutableList()
        lista.add(Acao())
        repo.atualizaAcoes(perfilId, indiceEvento, lista)
    }

    fun removeAcao(perfilId: String, indiceEvento: Int, indiceAcao: Int) {
        val perfil = repo.acharPerfil(perfilId) ?: return
        val lista = perfil.eventos[indiceEvento].toMutableList()
        if (indiceAcao in lista.indices) lista.removeAt(indiceAcao)
        repo.atualizaAcoes(perfilId, indiceEvento, lista)
    }

    fun atualizaAcao(perfilId: String, indiceEvento: Int, indiceAcao: Int, transformacao: (Acao) -> Acao) {
        val perfil = repo.acharPerfil(perfilId) ?: return
        val lista = perfil.eventos[indiceEvento].toMutableList()
        if (indiceAcao !in lista.indices) return
        lista[indiceAcao] = transformacao(lista[indiceAcao])
        repo.atualizaAcoes(perfilId, indiceEvento, lista)
    }

    // --- conta shelly ---

    fun guardaConta(nova: ContaShelly) {
        repo.guardaConta(nova)
    }

    // --- combinações (modo especial) ---

    private val _menuPerfisEspeciais = MutableStateFlow<List<Perfil>?>(null)
    val menuPerfisEspeciais: StateFlow<List<Perfil>?> = _menuPerfisEspeciais

    /** Botão do topo. Mesma lógica de 4 casos já validada na versão
     *  web: perfil aberto -> alterna o SEU modo; nenhum aberto mas
     *  exatamente 1 perfil com o modo ligado -> abre-o e desliga;
     *  vários -> mostra escolha; nenhum -> avisa sem ativar nada. */
    fun alternaModoEspecialTopo() {
        val ctx = getApplication<Application>()
        val perfilAberto = acharPerfil(_perfilAtivoId.value)

        if (perfilAberto != null) {
            repo.alternaModoCombinacao(perfilAberto.id, !perfilAberto.modoCombinacao)
            return
        }

        val comModo = perfis.value.filter { it.modoCombinacao }

        when {
            comModo.isEmpty() -> {
                RegistoEventos.adicionaResultado(ctx.getString(R.string.sem_comandos_especiais), false, "")
            }
            comModo.size == 1 -> {
                abrePerfil(comModo[0].id)
                repo.alternaModoCombinacao(comModo[0].id, false)
            }
            else -> {
                _menuPerfisEspeciais.value = comModo
            }
        }
    }

    fun escolhePerfilEspecial(id: String) {
        _menuPerfisEspeciais.value = null
        abrePerfil(id)
        repo.alternaModoCombinacao(id, false)
    }

    fun fechaMenuPerfisEspeciais() {
        _menuPerfisEspeciais.value = null
    }

    fun alternaModoCombinacao(perfilId: String, ligado: Boolean) {
        repo.alternaModoCombinacao(perfilId, ligado)
    }

    fun defineJanelaCombinacao(perfilId: String, segundos: Float) {
        val ms = if (segundos > 0) (segundos * 1000).toLong() else 3000L
        repo.defineJanelaCombinacao(perfilId, ms)
    }

    private val _construtorAberto = MutableStateFlow(false)
    val construtorAberto: StateFlow<Boolean> = _construtorAberto

    private val _construtorSequencia = MutableStateFlow<List<Int>>(emptyList())
    val construtorSequencia: StateFlow<List<Int>> = _construtorSequencia

    fun abreConstrutorCombinacao() {
        _construtorSequencia.value = emptyList()
        _construtorAberto.value = true
    }

    fun fechaConstrutorCombinacao() {
        _construtorAberto.value = false
    }

    fun adicionaAoConstrutor(indiceEvento: Int) {
        _construtorSequencia.value = _construtorSequencia.value + indiceEvento
    }

    fun limpaConstrutor() {
        _construtorSequencia.value = emptyList()
    }

    fun apagaUltimoDoConstrutor() {
        _construtorSequencia.value = _construtorSequencia.value.dropLast(1)
    }

    /** Devolve false (e não guarda nada) se a sequência tiver menos de
     *  2 cliques — o chamador deve avisar o utilizador nesse caso. */
    fun guardaCombinacao(perfilId: String, nomeBruto: String): Boolean {
        val seq = _construtorSequencia.value
        if (seq.size < 2) return false

        val nome = nomeBruto.trim().ifEmpty {
            val ctx = getApplication<Application>()
            seq.joinToString(" ") { ctx.getString(nomeRecursoEvento(it)) }
        }
        repo.novaCombinacao(perfilId, nome, seq)
        _construtorAberto.value = false
        _construtorSequencia.value = emptyList()
        return true
    }

    fun apagaCombinacao(perfilId: String, combinacaoId: String) {
        repo.apagaCombinacao(perfilId, combinacaoId)
    }

    fun adicionaAcaoCombinacao(perfilId: String, combinacaoId: String) {
        val comb = acharCombinacao(perfilId, combinacaoId) ?: return
        val lista = comb.acoes.toMutableList()
        lista.add(Acao())
        repo.atualizaAcoesCombinacao(perfilId, combinacaoId, lista)
    }

    fun removeAcaoCombinacao(perfilId: String, combinacaoId: String, indiceAcao: Int) {
        val comb = acharCombinacao(perfilId, combinacaoId) ?: return
        val lista = comb.acoes.toMutableList()
        if (indiceAcao in lista.indices) lista.removeAt(indiceAcao)
        repo.atualizaAcoesCombinacao(perfilId, combinacaoId, lista)
    }

    fun atualizaAcaoCombinacao(perfilId: String, combinacaoId: String, indiceAcao: Int, transformacao: (Acao) -> Acao) {
        val comb = acharCombinacao(perfilId, combinacaoId) ?: return
        val lista = comb.acoes.toMutableList()
        if (indiceAcao !in lista.indices) return
        lista[indiceAcao] = transformacao(lista[indiceAcao])
        repo.atualizaAcoesCombinacao(perfilId, combinacaoId, lista)
    }

    private fun acharCombinacao(perfilId: String, combinacaoId: String): Combinacao? =
        acharPerfil(perfilId)?.combinacoes?.firstOrNull { it.id == combinacaoId }

    private fun nomeRecursoEvento(indice: Int): Int = when (indice) {
        0 -> R.string.ev0; 1 -> R.string.ev1; 2 -> R.string.ev2; 3 -> R.string.ev3
        4 -> R.string.ev4; 5 -> R.string.ev5; else -> R.string.ev6
    }
}
