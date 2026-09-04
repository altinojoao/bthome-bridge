package pt.blugateway.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tipo de ação disparada por um clique.
 * CENARIO -> cenário Shelly Cloud (GET manual_run)
 * URL     -> endereço livre, GET ou POST, com marcadores
 * NTFY    -> tópico ntfy.sh, GET ou POST, com mensagem opcional e marcadores
 */
enum class TipoAcao(val codigo: Int) {
    CENARIO(0), URL(1), NTFY(2);

    companion object {
        fun deCodigo(c: Int): TipoAcao = entries.firstOrNull { it.codigo == c } ?: CENARIO
    }
}

enum class Metodo { GET, POST }

data class Acao(
    var tipo: TipoAcao = TipoAcao.CENARIO,
    var valor: String = "",
    var metodo: Metodo = Metodo.GET,
    var mensagem: String = ""
) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("tipo", tipo.codigo)
        put("valor", valor)
        put("metodo", metodo.name)
        put("mensagem", mensagem)
    }

    companion object {
        fun deJson(o: JSONObject): Acao = Acao(
            tipo = TipoAcao.deCodigo(o.optInt("tipo", 0)),
            valor = o.optString("valor", ""),
            metodo = if (o.optString("metodo", "GET") == "POST") Metodo.POST else Metodo.GET,
            mensagem = o.optString("mensagem", "")
        )
    }
}

/**
 * Os 7 tipos de clique oficiais BTHome v2 (objeto 0x3A):
 * 1 press, 2 double_press, 3 triple_press, 4 long_press,
 * 5 long_double_press, 6 long_triple_press, 128 (0x80) hold_press.
 * O índice na lista `eventos` de um Perfil corresponde à posição aqui.
 */
enum class TipoClique(val codigoBTHome: Int) {
    SIMPLES(1), DUPLO(2), TRIPLO(3), LONGO(4),
    LONGO_DUPLO(5), LONGO_TRIPLO(6), MANTER_PREMIDO(128);

    companion object {
        fun indiceDeCodigo(codigo: Int): Int = entries.indexOfFirst { it.codigoBTHome == codigo }
    }
}

data class Combinacao(
    var id: String,
    var nome: String,
    // sequencia de indices de TipoClique (0..6), na ordem em que devem ocorrer
    var sequencia: MutableList<Int> = mutableListOf(),
    var acoes: MutableList<Acao> = mutableListOf()
) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("nome", nome)
        put("sequencia", JSONArray().apply { sequencia.forEach { put(it) } })
        put("acoes", JSONArray().apply { acoes.forEach { put(it.paraJson()) } })
    }

    companion object {
        fun deJson(o: JSONObject): Combinacao {
            val seqJson = o.optJSONArray("sequencia")
            val seq = mutableListOf<Int>()
            if (seqJson != null) {
                for (k in 0 until seqJson.length()) seq.add(seqJson.optInt(k))
            }
            val acoesJson = o.optJSONArray("acoes")
            val acoes = mutableListOf<Acao>()
            if (acoesJson != null) {
                for (k in 0 until acoesJson.length()) {
                    val itemJson = acoesJson.getJSONObject(k)
                    if (itemJson != null) acoes.add(Acao.deJson(itemJson))
                }
            }
            return Combinacao(
                id = o.optString("id"),
                nome = o.optString("nome"),
                sequencia = seq,
                acoes = acoes
            )
        }

        fun nova(nome: String): Combinacao = Combinacao(id = "c" + System.currentTimeMillis(), nome = nome)
    }
}

data class Perfil(
    var id: String,
    var nome: String,
    // uma lista de acoes por cada um dos 7 tipos de clique, na ordem de TipoClique
    var eventos: MutableList<MutableList<Acao>> = MutableList(7) { mutableListOf() },
    /* Modo combinacao: quando ativo, os 7 eventos acima deixam de
       disparar individualmente. Os cliques ficam a acumular numa
       sequencia (por comando) ate a janela expirar ou corresponder
       a uma das combinacoes definidas pelo utilizador. */
    var modoCombinacao: Boolean = false,
    var janelaCombinacaoMs: Long = 3000L,
    var combinacoes: MutableList<Combinacao> = mutableListOf()
) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("nome", nome)
        put("eventos", JSONArray().apply {
            eventos.forEach { lista ->
                put(JSONArray().apply { lista.forEach { put(it.paraJson()) } })
            }
        })
        put("modoCombinacao", modoCombinacao)
        put("janelaCombinacaoMs", janelaCombinacaoMs)
        put("combinacoes", JSONArray().apply { combinacoes.forEach { put(it.paraJson()) } })
    }

    companion object {
        fun deJson(o: JSONObject): Perfil {
            val eventosJson = o.optJSONArray("eventos")
            val eventos = MutableList(7) { i ->
                val lista = mutableListOf<Acao>()
                val arr = eventosJson?.optJSONArray(i)
                if (arr != null) {
                    for (k in 0 until arr.length()) {
                        val itemJson = arr.getJSONObject(k)
                        if (itemJson != null) lista.add(Acao.deJson(itemJson))
                    }
                }
                lista
            }

            val combinacoesJson = o.optJSONArray("combinacoes")
            val combinacoes = mutableListOf<Combinacao>()
            if (combinacoesJson != null) {
                for (k in 0 until combinacoesJson.length()) {
                    val itemJson = combinacoesJson.getJSONObject(k)
                    if (itemJson != null) combinacoes.add(Combinacao.deJson(itemJson))
                }
            }

            return Perfil(
                id = o.optString("id"),
                nome = o.optString("nome"),
                eventos = eventos,
                modoCombinacao = o.optBoolean("modoCombinacao", false),
                janelaCombinacaoMs = if (o.has("janelaCombinacaoMs")) o.optLong("janelaCombinacaoMs") else 3000L,
                combinacoes = combinacoes
            )
        }

        fun novo(nome: String): Perfil = Perfil(
            id = "p" + System.currentTimeMillis(),
            nome = nome
        )
    }
}

data class PeriodoAgenda(var inicio: String, var fim: String) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("inicio", inicio)
        put("fim", fim)
    }

    companion object {
        fun deJson(o: JSONObject): PeriodoAgenda = PeriodoAgenda(
            inicio = o.optString("inicio"),
            fim = o.optString("fim")
        )
    }
}

data class Comando(
    var mac: String,
    var nome: String,
    var perfilId: String,
    var bateria: Int? = null,
    var rssi: Int? = null,
    // preenchidos quando uma combinacao dispara neste comando, para
    // o cartao mostrar destaque temporario (ver Repositorio.TEMPO_DESTAQUE_COMBINACAO_MS)
    var ultimaCombinacao: String? = null,
    var ultimaCombinacaoEm: Long? = null,
    // alarme de "fora de alcance": alerta se este comando ficar
    // tempoLimiteMs sem enviar nenhum sinal (clique ou beacon), OU
    // se o ultimo RSSI recebido for pior que rssiLimite. ultimoSinalEm
    // e atualizado por QUALQUER pacote BTHome recebido deste MAC, nao
    // so cliques.
    var alertaAlcance: Boolean = false,
    var ultimoSinalEm: Long? = null,
    // true enquanto o alarme esta ativo (fora de alcance ha mais que
    // o limite) -- controla se o som de alarme deve repetir
    var foraDeAlcance: Boolean = false,
    // limite de tempo sem sinal, em milissegundos, e RSSI minimo
    // aceitavel -- configuraveis por comando (antes eram uma
    // constante fixa global em GestorAlcance).
    var tempoLimiteMs: Long = 60_000L,
    var rssiLimite: Int = -80,
    // agenda semanal: se agendaSempreAtiva=true, o alarme corre 24h;
    // caso contrario so dentro dos periodos definidos em agendaDias,
    // indexados 0=domingo .. 6=sabado.
    var agendaSempreAtiva: Boolean = true,
    var agendaDias: MutableMap<Int, MutableList<PeriodoAgenda>> = mutableMapOf(
        0 to mutableListOf(), 1 to mutableListOf(), 2 to mutableListOf(), 3 to mutableListOf(),
        4 to mutableListOf(), 5 to mutableListOf(), 6 to mutableListOf()
    ),
    // chave AES-128 de 32 caracteres hex, obtida pelo utilizador fora
    // da app (a Shelly gera-a ao ativar "Segurança / conexão Bluetooth
    // segura" e so a expoe via ferramentas de debug BLE, nao na app
    // Shelly normal). Sem esta chave, tramas encriptadas deste
    // comando sao descartadas silenciosamente, tal como acontecia
    // antes desta funcionalidade existir.
    var chaveEncriptacao: String? = null,
    // opt-in EXPLICITO, por comando, para incluir a localizacao atual
    // do telemovel (nao do comando) nos marcadores {lat}/{lon} das
    // acoes desse comando. Falso por omissao -- a app so pede a
    // permissao de localizacao em runtime na primeira vez que o
    // utilizador ligar isto num comando, nunca no arranque.
    var incluirLocalizacao: Boolean = false,
    // opt-in SEPARADO de incluirLocalizacao: grava um ponto no
    // historico de trajeto deste comando sempre que chega um anuncio
    // BTHome (nao so em cliques), respeitando intervaloBeaconMs como
    // espacamento minimo entre pontos gravados. Tal como
    // incluirLocalizacao, so pede a permissao de localizacao quando
    // ativado, nunca no arranque -- e os dois opt-ins sao
    // independentes (pode ter um sem o outro).
    var modoBeaconTrajeto: Boolean = false,
    var intervaloBeaconMs: Long = 60_000L,
    var ultimoPontoTrajetoEm: Long? = null
) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("mac", mac)
        put("nome", nome)
        put("perfilId", perfilId)
        bateria?.let { put("bateria", it) }
        rssi?.let { put("rssi", it) }
        ultimaCombinacao?.let { put("ultimaCombinacao", it) }
        ultimaCombinacaoEm?.let { put("ultimaCombinacaoEm", it) }
        put("alertaAlcance", alertaAlcance)
        ultimoSinalEm?.let { put("ultimoSinalEm", it) }
        put("foraDeAlcance", foraDeAlcance)
        put("tempoLimiteMs", tempoLimiteMs)
        put("rssiLimite", rssiLimite)
        put("agendaSempreAtiva", agendaSempreAtiva)
        put("agendaDias", JSONObject().apply {
            agendaDias.forEach { (dia, periodos) ->
                put(dia.toString(), JSONArray().apply {
                    periodos.forEach { put(it.paraJson()) }
                })
            }
        })
        chaveEncriptacao?.let { put("chaveEncriptacao", it) }
        put("incluirLocalizacao", incluirLocalizacao)
        put("modoBeaconTrajeto", modoBeaconTrajeto)
        put("intervaloBeaconMs", intervaloBeaconMs)
        ultimoPontoTrajetoEm?.let { put("ultimoPontoTrajetoEm", it) }
    }

    companion object {
        fun deJson(o: JSONObject): Comando = Comando(
            mac = o.optString("mac"),
            nome = o.optString("nome"),
            perfilId = o.optString("perfilId"),
            bateria = if (o.has("bateria")) o.optInt("bateria") else null,
            rssi = if (o.has("rssi")) o.optInt("rssi") else null,
            ultimaCombinacao = if (o.has("ultimaCombinacao")) o.optString("ultimaCombinacao") else null,
            ultimaCombinacaoEm = if (o.has("ultimaCombinacaoEm")) o.optLong("ultimaCombinacaoEm") else null,
            alertaAlcance = o.optBoolean("alertaAlcance", false),
            ultimoSinalEm = if (o.has("ultimoSinalEm")) o.optLong("ultimoSinalEm") else null,
            foraDeAlcance = o.optBoolean("foraDeAlcance", false),
            tempoLimiteMs = o.optLong("tempoLimiteMs", 60_000L),
            rssiLimite = o.optInt("rssiLimite", -80),
            agendaSempreAtiva = o.optBoolean("agendaSempreAtiva", true),
            agendaDias = run {
                val mapa = mutableMapOf<Int, MutableList<PeriodoAgenda>>(
                    0 to mutableListOf(), 1 to mutableListOf(), 2 to mutableListOf(), 3 to mutableListOf(),
                    4 to mutableListOf(), 5 to mutableListOf(), 6 to mutableListOf()
                )
                val agendaJson = o.optJSONObject("agendaDias")
                if (agendaJson != null) {
                    for (dia in 0..6) {
                        val arr = agendaJson.optJSONArray(dia.toString()) ?: continue
                        val lista = mutableListOf<PeriodoAgenda>()
                        for (i in 0 until arr.length()) {
                            lista.add(PeriodoAgenda.deJson(arr.getJSONObject(i)))
                        }
                        mapa[dia] = lista
                    }
                }
                mapa
            },
            chaveEncriptacao = if (o.has("chaveEncriptacao")) o.optString("chaveEncriptacao") else null,
            incluirLocalizacao = o.optBoolean("incluirLocalizacao", false),
            modoBeaconTrajeto = o.optBoolean("modoBeaconTrajeto", false),
            intervaloBeaconMs = o.optLong("intervaloBeaconMs", 60_000L),
            ultimoPontoTrajetoEm = if (o.has("ultimoPontoTrajetoEm")) o.optLong("ultimoPontoTrajetoEm") else null
        )
    }
}

data class ContaShelly(
    var servidorNum: String = "",
    var regiao: String = "eu",
    var authKey: String = ""
) {
    fun servidorUrl(): String? {
        if (servidorNum.isBlank()) return null
        return "https://shelly-$servidorNum-$regiao.shelly.cloud"
    }
}

/** Um ponto do historico de trajeto de um comando, gravado ao
 *  disparar um clique com incluirLocalizacao=true, ou ao receber
 *  um anuncio com modoBeaconTrajeto=true (ver GestorTrajeto). */
data class PontoTrajeto(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val origem: OrigemPonto
) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("lat", latitude)
        put("lon", longitude)
        put("timestamp", timestamp)
        put("origem", origem.name)
    }

    companion object {
        fun deJson(o: JSONObject): PontoTrajeto? {
            if (!o.has("lat") || !o.has("lon") || !o.has("timestamp")) return null
            val origemStr = o.optString("origem", OrigemPonto.CLIQUE.name)
            val origem = try {
                OrigemPonto.valueOf(origemStr)
            } catch (e: IllegalArgumentException) {
                OrigemPonto.CLIQUE
            }
            return PontoTrajeto(o.optDouble("lat"), o.optDouble("lon"), o.optLong("timestamp"), origem)
        }
    }
}

enum class OrigemPonto { CLIQUE, BEACON }

/** Politica de retencao do historico de trajeto -- decide o que se
 *  GUARDA em disco (ver Repositorio.adicionaPontoTrajeto), aplicada
 *  igualmente a todos os comandos (nao configuravel por comando,
 *  para manter simples). "Ultima viagem" no mapa e um MODO DE
 *  VISUALIZACAO calculado a partir do historico ja guardado, nao
 *  depende desta politica -- so precisa que o historico guardado
 *  cubra pelo menos a ultima viagem inteira, o que qualquer uma
 *  destas tres opcoes garante em uso normal. */
enum class ModoRetencaoTrajeto {
    DIAS,       // mantem so os ultimos N dias (comportamento original)
    QUANTIDADE  // mantem so os ultimos N pontos, independente da idade
}

/** Um ponto do template de um cenario de trajeto -- so lat/lon, ao
 *  contrario de PontoTrajeto nao guarda timestamp nem origem (o
 *  template e uma forma geometrica de referencia, nao um historico
 *  temporal). A ORDEM na lista e' que importa -- representa a
 *  sequencia do inicio ao fim do percurso de referencia. */
data class PontoTemplate(val lat: Double, val lon: Double) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
    }

    companion object {
        fun deJson(o: JSONObject): PontoTemplate? {
            if (!o.has("lat") || !o.has("lon")) return null
            return PontoTemplate(o.optDouble("lat"), o.optDouble("lon"))
        }
    }
}

/**
 * Um cenario de trajeto: vigia o historico de trajeto de UM comando
 * especifico, compara-o continuamente com um template de referencia
 * (gravado a partir de um percurso ja feito, ou desenhado a mao no
 * mapa), e dispara uma lista de acoes proprias assim que a
 * semelhanca ultrapassar limiarPercentagem -- uma unica vez por
 * viagem (ver Repositorio.jaDisparadoNestaViagem), mesmo que a
 * semelhanca continue a subir depois disso. So volta a poder
 * disparar numa viagem seguinte, que tem de progredir na MESMA
 * direcao do template (ver GestorSemelhancaTrajeto -- o algoritmo
 * de comparacao e sensivel a ordem, um trajeto no sentido inverso
 * nao consegue avancar o cursor de correspondencia).
 */
data class CenarioTrajeto(
    var id: String,
    var nome: String,
    var macComando: String,
    var template: List<PontoTemplate>,
    // % (0-100) de semelhanca necessaria para disparar
    var limiarPercentagem: Int = 80,
    // raio de correspondencia entre um ponto do trajeto atual e um
    // ponto do template, em metros -- mais folgado que o raio de
    // "paragem" (50m) usado para detetar fim de viagem, porque aqui
    // e' sobre o erro do GPS em movimento, nao sobre permanencia
    var raioMetros: Int = 40,
    var ativo: Boolean = true,
    var acoes: MutableList<Acao> = mutableListOf(),
    // timestamp da ultima vez que este cenario disparou -- usado so
    // para mostrar na UI quando foi a ultima vez, nao para logica de
    // bloqueio (essa fica no Repositorio, associada ao MAC + inicio
    // da viagem atual, ver jaDisparadoNestaViagem)
    var ultimoDisparoEm: Long? = null
) {
    fun paraJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("nome", nome)
        put("macComando", macComando)
        put("template", JSONArray().apply { template.forEach { put(it.paraJson()) } })
        put("limiarPercentagem", limiarPercentagem)
        put("raioMetros", raioMetros)
        put("ativo", ativo)
        put("acoes", JSONArray().apply { acoes.forEach { put(it.paraJson()) } })
        ultimoDisparoEm?.let { put("ultimoDisparoEm", it) }
    }

    companion object {
        fun deJson(o: JSONObject): CenarioTrajeto? {
            val id = o.optString("id").ifBlank { return null }
            val mac = o.optString("macComando").ifBlank { return null }
            val arrTemplate = o.optJSONArray("template") ?: return null
            val template = (0 until arrTemplate.length()).mapNotNull { i ->
                PontoTemplate.deJson(arrTemplate.getJSONObject(i))
            }
            if (template.size < 2) return null

            val arrAcoes = o.optJSONArray("acoes")
            val acoes = if (arrAcoes != null) {
                (0 until arrAcoes.length()).map { i -> Acao.deJson(arrAcoes.getJSONObject(i)) }.toMutableList()
            } else mutableListOf()

            return CenarioTrajeto(
                id = id,
                nome = o.optString("nome", "Trajeto"),
                macComando = mac,
                template = template,
                limiarPercentagem = o.optInt("limiarPercentagem", 80),
                raioMetros = o.optInt("raioMetros", 40),
                ativo = o.optBoolean("ativo", true),
                acoes = acoes,
                ultimoDisparoEm = if (o.has("ultimoDisparoEm")) o.optLong("ultimoDisparoEm") else null
            )
        }
    }
}
