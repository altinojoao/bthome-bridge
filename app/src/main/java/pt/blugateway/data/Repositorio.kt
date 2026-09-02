package pt.blugateway.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fonte única de verdade para perfis, comandos e conta Shelly.
 * Persistido em SharedPreferences (mesmo padrão do projeto original
 * bthome-bridge), exposto à UI via StateFlow para recomposição automática.
 *
 * SINGLETON: mantido como instância única para toda a app, através do
 * companion object com operator fun invoke() abaixo -- Repositorio(context)
 * continua a funcionar em qualquer sítio do código exatamente como
 * antes, mas devolve sempre a mesma instância em memória. Sem isto,
 * cada ponto do código (ProcessadorClique, GestorAlcance, BootReceiver,
 * o ViewModel) criava a sua própria cópia dos StateFlow -- mudanças
 * feitas em segundo plano por uma instância nunca chegavam a notificar
 * as outras, mesmo estando todas a persistir no mesmo SharedPreferences
 * por baixo. A UI só veria essas mudanças se recriasse o seu próprio
 * Repositorio (o que só acontecia, por exemplo, ao reabrir a app).
 *
 * allowBackup=false no manifesto (decisão já tomada no projeto original):
 * a auth key da cloud nunca deve ir para o Android Auto Backup.
 */
class Repositorio private constructor(context: Context) {

    companion object {
        const val TEMPO_DESTAQUE_COMBINACAO_MS = 6000L

        @Volatile private var instancia: Repositorio? = null

        operator fun invoke(context: Context): Repositorio =
            instancia ?: synchronized(this) {
                instancia ?: Repositorio(context.applicationContext).also { instancia = it }
            }
    }

    private val prefs: SharedPreferences =
        requireNotNull(context.getSharedPreferences("blugateway", Context.MODE_PRIVATE)) {
            "getSharedPreferences nunca deveria devolver null"
        }

    private val _perfis = MutableStateFlow(carregaPerfis())
    val perfis: StateFlow<List<Perfil>> = _perfis

    private val _comandos = MutableStateFlow(carregaComandos())
    val comandos: StateFlow<List<Comando>> = _comandos

    private val _conta = MutableStateFlow(carregaConta())
    val conta: StateFlow<ContaShelly> = _conta

    // --- perfis ---

    private fun carregaPerfis(): List<Perfil> {
        val raw = prefs.getString("perfis", null)
        if (raw != null) {
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it)?.let { o -> Perfil.deJson(o) } }
            } catch (e: Exception) {
                listOf(perfilPadrao())
            }
        }
        return listOf(perfilPadrao())
    }

    private fun perfilPadrao(): Perfil {
        val p = Perfil.novo("Perfil principal")
        p.eventos[0].add(Acao(TipoAcao.NTFY, "meu-topico", Metodo.POST, "{evento} \u00b7 {bateria}% \u00b7 {rssi} dBm"))
        return p
    }

    private fun guardaPerfis(lista: List<Perfil>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.paraJson()) }
        prefs.edit().putString("perfis", arr.toString()).apply()
        _perfis.value = lista
    }

    fun acharPerfil(id: String?): Perfil? = _perfis.value.firstOrNull { it.id == id }

    fun novoPerfil(nome: String): Perfil {
        val p = Perfil.novo(nome)
        guardaPerfis(_perfis.value + p)
        return p
    }

    fun renomeiaPerfil(id: String, novoNome: String) {
        val lista = _perfis.value.map { if (it.id == id) it.copy(nome = novoNome) else it }
        guardaPerfis(lista)
    }

    fun atualizaAcoes(perfilId: String, indiceEvento: Int, acoes: MutableList<Acao>) {
        val lista = _perfis.value.map {
            if (it.id == perfilId) {
                val novosEventos = it.eventos.toMutableList()
                novosEventos[indiceEvento] = acoes
                it.copy(eventos = novosEventos)
            } else it
        }
        guardaPerfis(lista)
    }

    fun alternaModoCombinacao(perfilId: String, ligado: Boolean) {
        val lista = _perfis.value.map { if (it.id == perfilId) it.copy(modoCombinacao = ligado) else it }
        guardaPerfis(lista)
    }

    fun defineJanelaCombinacao(perfilId: String, ms: Long) {
        val lista = _perfis.value.map { if (it.id == perfilId) it.copy(janelaCombinacaoMs = ms) else it }
        guardaPerfis(lista)
    }

    fun novaCombinacao(perfilId: String, nome: String, sequencia: List<Int>): Combinacao? {
        val p = acharPerfil(perfilId) ?: return null
        val nova = Combinacao.nova(nome).apply { this.sequencia.addAll(sequencia) }
        val novasCombinacoes = p.combinacoes.toMutableList().apply { add(nova) }
        val lista = _perfis.value.map { if (it.id == perfilId) it.copy(combinacoes = novasCombinacoes) else it }
        guardaPerfis(lista)
        return nova
    }

    fun apagaCombinacao(perfilId: String, combinacaoId: String) {
        val p = acharPerfil(perfilId) ?: return
        val novasCombinacoes = p.combinacoes.filter { it.id != combinacaoId }.toMutableList()
        val lista = _perfis.value.map { if (it.id == perfilId) it.copy(combinacoes = novasCombinacoes) else it }
        guardaPerfis(lista)
    }

    fun atualizaAcoesCombinacao(perfilId: String, combinacaoId: String, acoes: MutableList<Acao>) {
        val p = acharPerfil(perfilId) ?: return
        val novasCombinacoes = p.combinacoes.map {
            if (it.id == combinacaoId) it.copy(acoes = acoes) else it
        }.toMutableList()
        val lista = _perfis.value.map { if (it.id == perfilId) it.copy(combinacoes = novasCombinacoes) else it }
        guardaPerfis(lista)
    }

    /** Apaga um perfil. Comandos órfãos são reatribuídos ao primeiro perfil
     *  restante. Nunca apaga o último perfil (a app precisa de pelo menos um). */
    fun apagaPerfil(id: String): String? {
        if (_perfis.value.size <= 1) return null
        val restantes = _perfis.value.filter { it.id != id }
        val substituto = restantes.first().id
        guardaPerfis(restantes)

        val comandosAtualizados = _comandos.value.map {
            if (it.perfilId == id) it.copy(perfilId = substituto) else it
        }
        guardaComandos(comandosAtualizados)
        return substituto
    }

    // --- comandos ---

    private fun carregaComandos(): List<Comando> {
        val raw = prefs.getString("comandos", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.getJSONObject(it)?.let { o -> Comando.deJson(o) } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun guardaComandos(lista: List<Comando>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.paraJson()) }
        prefs.edit().putString("comandos", arr.toString()).apply()
        _comandos.value = lista
    }

    fun acharComandoPorMac(mac: String): Comando? = _comandos.value.firstOrNull { it.mac == mac }

    fun associaComando(mac: String, nome: String, perfilId: String) {
        if (acharComandoPorMac(mac) != null) return
        guardaComandos(_comandos.value + Comando(mac = mac, nome = nome, perfilId = perfilId))
    }

    /** Associa manualmente um comando por MAC, para o caso de um
     *  botão já com "Segurança / conexão Bluetooth segura" ativada
     *  no firmware -- nunca envia um clique legível para a app o
     *  detetar durante a procura normal, por isso o utilizador tem de
     *  introduzir o MAC e a chave à mão. Devolve false se o MAC já
     *  estiver associado (nesse caso nada é alterado) ou se o formato
     *  do MAC for inválido. */
    fun associaComandoManual(mac: String, nome: String, perfilId: String, chaveHex: String?): Boolean {
        val macNormalizado = mac.trim().uppercase()
        if (!macNormalizado.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$"))) return false
        if (acharComandoPorMac(macNormalizado) != null) return false
        val chaveLimpa = chaveHex?.trim()?.takeIf { it.isNotEmpty() }
        guardaComandos(_comandos.value + Comando(
            mac = macNormalizado, nome = nome, perfilId = perfilId, chaveEncriptacao = chaveLimpa
        ))
        return true
    }

    fun esqueceComando(mac: String) {
        guardaComandos(_comandos.value.filter { it.mac != mac })
    }

    fun mudaPerfilComando(mac: String, novoPerfilId: String) {
        val lista = _comandos.value.map { if (it.mac == mac) it.copy(perfilId = novoPerfilId) else it }
        guardaComandos(lista)
    }

    /** Chamado por QUALQUER pacote BTHome recebido deste comando --
     *  clique ou beacon. Atualiza tambem ultimoSinalEm, que alimenta
     *  o alarme de fora-de-alcance: se o comando estava marcado como
     *  fora de alcance e agora voltou a emitir, o alarme desliga-se
     *  sozinho aqui. */
    fun atualizaSinal(mac: String, rssi: Int?, bateria: Int?) {
        val agora = System.currentTimeMillis()
        val lista = _comandos.value.map {
            if (it.mac == mac) {
                it.copy(
                    rssi = rssi ?: it.rssi,
                    bateria = bateria ?: it.bateria,
                    ultimoSinalEm = agora,
                    foraDeAlcance = false
                )
            } else it
        }
        guardaComandos(lista)
    }

    fun alternaAlertaAlcance(mac: String, ativo: Boolean) {
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(alertaAlcance = ativo, foraDeAlcance = false) else it
        }
        guardaComandos(lista)
    }

    fun defineTempoLimiteAlcance(mac: String, segundos: Int) {
        val ms = (segundos.coerceAtLeast(1)) * 1000L
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(tempoLimiteMs = ms) else it
        }
        guardaComandos(lista)
    }

    fun defineRssiLimiteAlcance(mac: String, rssi: Int) {
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(rssiLimite = rssi) else it
        }
        guardaComandos(lista)
    }

    fun alternaAgendaSempreAtiva(mac: String, sempreAtiva: Boolean) {
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(agendaSempreAtiva = sempreAtiva) else it
        }
        guardaComandos(lista)
    }

    /** Adiciona um periodo ao dia indicado (0=domingo..6=sabado),
     *  rejeitando periodos invalidos (inicio igual ao fim) ou que se
     *  sobrepoem a um periodo ja existente nesse dia -- incluindo
     *  sobreposicao atraves da meia-noite. Devolve false sem alterar
     *  nada se a validacao falhar, para a UI poder avisar o
     *  utilizador. */
    fun adicionaPeriodoAgenda(mac: String, dia: Int, inicio: String, fim: String): Boolean {
        if (inicio == fim) return false
        val comando = _comandos.value.firstOrNull { it.mac == mac } ?: return false
        val existentes = comando.agendaDias[dia] ?: emptyList()
        val novo = PeriodoAgenda(inicio, fim)
        if (existentes.any { periodosSobrepostos(novo, it) }) return false

        val lista = _comandos.value.map {
            if (it.mac == mac) {
                val novaAgenda = it.agendaDias.toMutableMap()
                val novaLista = (novaAgenda[dia] ?: mutableListOf()).toMutableList()
                novaLista.add(novo)
                novaAgenda[dia] = novaLista
                it.copy(agendaDias = novaAgenda)
            } else it
        }
        guardaComandos(lista)
        return true
    }

    fun removePeriodoAgenda(mac: String, dia: Int, indice: Int) {
        val lista = _comandos.value.map {
            if (it.mac == mac) {
                val novaAgenda = it.agendaDias.toMutableMap()
                val novaLista = (novaAgenda[dia] ?: mutableListOf()).toMutableList()
                if (indice in novaLista.indices) novaLista.removeAt(indice)
                novaAgenda[dia] = novaLista
                it.copy(agendaDias = novaAgenda)
            } else it
        }
        guardaComandos(lista)
    }

    private fun paraMinutosAgenda(hhmm: String): Int {
        val partes = hhmm.split(":")
        return (partes.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (partes.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun periodosSobrepostos(a: PeriodoAgenda, b: PeriodoAgenda): Boolean {
        var a1 = paraMinutosAgenda(a.inicio); var a2 = paraMinutosAgenda(a.fim)
        if (a2 <= a1) a2 += 1440
        var b1 = paraMinutosAgenda(b.inicio); var b2 = paraMinutosAgenda(b.fim)
        if (b2 <= b1) b2 += 1440
        for (deslocamento in intArrayOf(-1440, 0, 1440)) {
            val d1 = b1 + deslocamento; val d2 = b2 + deslocamento
            if (a1 < d2 && d1 < a2) return true
        }
        return false
    }

    /** Define a chave de encriptação de um comando (32 caracteres hex,
     *  16 bytes). Passar null ou string vazia remove a chave -- o
     *  comando volta a ser tratado como não encriptado. */
    fun defineChaveEncriptacao(mac: String, chaveHex: String?) {
        val limpa = chaveHex?.trim()?.takeIf { it.isNotEmpty() }
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(chaveEncriptacao = limpa) else it
        }
        guardaComandos(lista)
    }

    fun defineIncluirLocalizacao(mac: String, incluir: Boolean) {
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(incluirLocalizacao = incluir) else it
        }
        guardaComandos(lista)
    }

    /** Chamado pelo vigilante de alcance quando um comando passa a
     *  estar/deixar de estar fora de alcance. Devolve true se o
     *  estado realmente mudou (para o chamador so tocar o alarme na
     *  transicao, nao a cada verificacao). */
    fun defineForaDeAlcance(mac: String, foraDeAlcance: Boolean): Boolean {
        var mudou = false
        val lista = _comandos.value.map {
            if (it.mac == mac && it.foraDeAlcance != foraDeAlcance) {
                mudou = true
                it.copy(foraDeAlcance = foraDeAlcance)
            } else it
        }
        if (mudou) guardaComandos(lista)
        return mudou
    }

    /** Marca que uma combinacao acabou de disparar neste comando --
     *  usado para o cartao mostrar destaque temporario (ver
     *  Repositorio.TEMPO_DESTAQUE_COMBINACAO_MS na UI). */
    fun registaCombinacaoDisparada(mac: String, nomeCombinacao: String) {
        val agora = System.currentTimeMillis()
        val lista = _comandos.value.map {
            if (it.mac == mac) it.copy(ultimaCombinacao = nomeCombinacao, ultimaCombinacaoEm = agora) else it
        }
        guardaComandos(lista)
    }

    // --- conta shelly ---

    private fun carregaConta(): ContaShelly = ContaShelly(
        servidorNum = prefs.getString("srv_num", "") ?: "",
        regiao = prefs.getString("srv_regiao", "eu") ?: "eu",
        authKey = prefs.getString("srv_key", "") ?: ""
    )

    fun guardaConta(conta: ContaShelly) {
        prefs.edit()
            .putString("srv_num", conta.servidorNum)
            .putString("srv_regiao", conta.regiao)
            .putString("srv_key", conta.authKey)
            .apply()
        _conta.value = conta
    }

    // --- preferências de UI (não sensíveis) ---

    fun notacaoPontos(): Boolean = prefs.getBoolean("notacao_pontos", true)
    fun defineNotacao(pontos: Boolean) {
        prefs.edit().putBoolean("notacao_pontos", pontos).apply()
    }

    fun idioma(): String? = prefs.getString("idioma", null)
    fun defineIdioma(cod: String) {
        prefs.edit().putString("idioma", cod).apply()
    }

    fun temaClaro(): Boolean = prefs.getBoolean("tema_claro", false)
    fun defineTemaClaro(claro: Boolean) {
        prefs.edit().putBoolean("tema_claro", claro).apply()
    }

    fun somAtivo(): Boolean = prefs.getBoolean("som_ativo", true)
    fun defineSomAtivo(ativo: Boolean) {
        prefs.edit().putBoolean("som_ativo", ativo).apply()
    }

    /** Blocos que o utilizador desativou explicitamente no ecra
     *  "Blocos visiveis" (Escuta, Ultimo clique, Comandos,
     *  Configuracao, Registo). Guarda-se so os DESATIVADOS -- por
     *  omissao todos estao ativos, sem precisar de nada persistido. */
    fun cardsDesativados(): Set<String> = prefs.getStringSet("cards_desativados", emptySet()) ?: emptySet()
    fun defineCardAtivo(idBloco: String, ativo: Boolean) {
        val atuais = cardsDesativados().toMutableSet()
        if (ativo) atuais.remove(idBloco) else atuais.add(idBloco)
        prefs.edit().putStringSet("cards_desativados", atuais).apply()
    }
}
