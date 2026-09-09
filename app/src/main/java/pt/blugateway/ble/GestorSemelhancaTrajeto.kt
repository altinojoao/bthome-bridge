package pt.blugateway.ble

import android.content.Context
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.data.PontoTemplate
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.data.Repositorio
import pt.blugateway.net.ExecutorAcoes

/**
 * Compara o historico de trajeto de um comando com os templates dos
 * cenarios de trajeto definidos para esse comando, e dispara as
 * acoes associadas assim que a semelhanca ultrapassa o limiar
 * configurado -- uma unica vez por viagem (ver
 * Repositorio.jaDisparadoNestaViagem/marcaDisparado).
 *
 * Chamado a partir de ProcessadorClique, a seguir a
 * GestorTrajeto.registaPontoDeBeaconSeNecessario -- so faz sentido
 * verificar semelhanca depois de um ponto novo ter sido gravado.
 */
object GestorSemelhancaTrajeto {

    private const val RAIO_PARAGEM_METROS = 50.0
    private const val TEMPO_MIN_PARAGEM_MS = 20L * 60 * 1000 // 20 minutos, mesmo criterio do modo "ultima viagem" do mapa
    private const val SALTO_MAXIMO_FRACAO = 0.15

    fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val toRad = { g: Double -> g * Math.PI / 180 }
        val dLat = toRad(lat2 - lat1)
        val dLon = toRad(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * Encontra o inicio da viagem atual dentro do historico completo
     * -- mesmo criterio ja usado no modo "ultima viagem" do mapa
     * (ver mapa.html/filtraUltimaViagem): uma "viagem" termina
     * quando o comando fica parado (todos os pontos dentro de
     * RAIO_PARAGEM_METROS uns dos outros) durante mais de
     * TEMPO_MIN_PARAGEM_MS seguidos.
     *
     * Devolve o TIMESTAMP do primeiro ponto da viagem atual, usado
     * como identificador dessa viagem para efeitos de bloqueio de
     * disparo repetido (ver Repositorio.jaDisparadoNestaViagem).
     */
    fun inicioViagemAtual(pontosOrdenados: List<PontoTrajeto>): Long? {
        if (pontosOrdenados.isEmpty()) return null
        if (pontosOrdenados.size == 1) return pontosOrdenados[0].timestamp

        // fronteiras de todas as paragens longas encontradas
        val fronteiras = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < pontosOrdenados.size) {
            var j = i
            while (j + 1 < pontosOrdenados.size) {
                val d = distanciaMetros(
                    pontosOrdenados[i].latitude, pontosOrdenados[i].longitude,
                    pontosOrdenados[j + 1].latitude, pontosOrdenados[j + 1].longitude
                )
                if (d > RAIO_PARAGEM_METROS) break
                j++
            }
            val duracaoParagem = pontosOrdenados[j].timestamp - pontosOrdenados[i].timestamp
            if (duracaoParagem >= TEMPO_MIN_PARAGEM_MS) {
                fronteiras.add(i to j)
                i = j + 1
            } else {
                i++
            }
        }

        if (fronteiras.isEmpty()) return pontosOrdenados[0].timestamp

        val (ultimoInicio, ultimoFim) = fronteiras.last()

        // Se a ultima paragem vai ate ao fim, o comando esta parado
        // AGORA -- tipicamente porque acabou de chegar ao destino. A
        // viagem relevante para avaliar cenarios e' a que LEVOU ate
        // essa paragem, nao "nenhuma": devolver null aqui fazia
        // verificaCenarios sair sem avaliar nada, e um cenario do
        // tipo "a chegar a casa" nunca disparava.
        if (ultimoFim == pontosOrdenados.size - 1) {
            val inicio = if (fronteiras.size >= 2) fronteiras[fronteiras.size - 2].second + 1 else 0
            return if (inicio <= ultimoInicio) pontosOrdenados[inicio].timestamp else null
        }

        // Ha movimento depois da ultima paragem -- a viagem atual e'
        // esse movimento.
        return pontosOrdenados[ultimoFim + 1].timestamp
    }

    /**
     * LCSS geometrico simplificado com limite de salto -- ver
     * comentario completo na validacao isolada deste algoritmo.
     * Sensivel a ordem: um trajeto no sentido inverso do template
     * nao consegue avancar o cursor de forma significativa.
     * Devolve um valor entre 0.0 e 1.0.
     */
    fun calculaSemelhanca(
        trajetoAtual: List<PontoTrajeto>,
        template: List<PontoTemplate>,
        raioMetros: Int
    ): Double {
        if (template.isEmpty() || trajetoAtual.isEmpty()) return 0.0

        var cursor = 0
        val saltoMaximo = (template.size * SALTO_MAXIMO_FRACAO).toInt().coerceAtLeast(1)

        for (pontoAtual in trajetoAtual) {
            val limiteAvanco = (cursor + saltoMaximo).coerceAtMost(template.size)
            var i = cursor
            while (i < limiteAvanco) {
                val d = distanciaMetros(pontoAtual.latitude, pontoAtual.longitude, template[i].lat, template[i].lon)
                if (d <= raioMetros) {
                    cursor = i + 1
                    break
                }
                i++
            }
        }

        return cursor.toDouble() / template.size
    }

    /**
     * Separa o historico completo (ordenado cronologicamente) numa
     * lista de "viagens" -- cada viagem e' uma sublista contigua de
     * pontos, cortada nos mesmos criterios de paragem usados por
     * inicioViagemAtual. Usada pela UI para o utilizador escolher
     * qual viagem ja gravada quer usar como template de um cenario
     * novo (ver EcraCenarios / SeletorTrajetoMapa). A mais recente vem por
     * ultimo na lista devolvida.
     */
    fun separaEmViagens(pontosOrdenados: List<PontoTrajeto>): List<List<PontoTrajeto>> {
        if (pontosOrdenados.isEmpty()) return emptyList()
        if (pontosOrdenados.size == 1) return listOf(pontosOrdenados)

        val viagens = mutableListOf<List<PontoTrajeto>>()
        var inicioAtual = 0
        var i = 0
        while (i < pontosOrdenados.size) {
            var j = i
            while (j + 1 < pontosOrdenados.size) {
                val d = distanciaMetros(
                    pontosOrdenados[i].latitude, pontosOrdenados[i].longitude,
                    pontosOrdenados[j + 1].latitude, pontosOrdenados[j + 1].longitude
                )
                if (d > RAIO_PARAGEM_METROS) break
                j++
            }
            val duracaoParagem = pontosOrdenados[j].timestamp - pontosOrdenados[i].timestamp
            if (duracaoParagem >= TEMPO_MIN_PARAGEM_MS) {
                if (j + 1 > inicioAtual) {
                    viagens.add(pontosOrdenados.subList(inicioAtual, j + 1))
                }
                inicioAtual = j + 1
                i = j + 1
            } else {
                i++
            }
        }
        if (inicioAtual < pontosOrdenados.size) {
            viagens.add(pontosOrdenados.subList(inicioAtual, pontosOrdenados.size))
        }
        return viagens
    }

    /**
     * Verifica todos os cenarios de trajeto ativos para este
     * comando, e dispara as acoes do primeiro que ultrapassar o
     * limiar e ainda nao tiver disparado nesta viagem. Chamado a
     * cada ponto novo gravado -- barato o suficiente para correr a
     * cada anuncio de beacon (o template raramente tem mais que
     * algumas centenas de pontos).
     */
    suspend fun verificaCenarios(context: Context, mac: String) {
        val repo = Repositorio(context)
        val cenarios = repo.cenariosTrajetoPara(mac).filter { it.ativo }
        if (cenarios.isEmpty()) return

        val historico = repo.historicoTrajeto(mac)
        RegistoDiagnostico.regista(context, "[D-cenarios] mac=$mac historico=${historico.size}pts")
        if (historico.isEmpty()) return

        val inicioViagem = inicioViagemAtual(historico)
        RegistoDiagnostico.regista(context, "[D-cenarios] inicioViagem=$inicioViagem (null=sem viagem ativa)")
        val inicio = inicioViagem ?: return
        val trajetoViagemAtual = historico.filter { it.timestamp >= inicio }
        RegistoDiagnostico.regista(context, "[D-cenarios] trajetoAtual=${trajetoViagemAtual.size}pts")

        for (cenario in cenarios) {
            val jaDisparado = repo.jaDisparadoNestaViagem(cenario.id, inicio)
            RegistoDiagnostico.regista(context, "[D-cenarios] cenario='${cenario.nome}' template=${cenario.template.size}pts jaDisparado=$jaDisparado limiar=${cenario.limiarPercentagem}%")
            if (jaDisparado) continue

            // Log das coordenadas reais dos primeiros pontos, para
            // confirmar se o template e o trajeto estao na mesma zona
            if (cenario.template.isNotEmpty()) {
                val t0 = cenario.template.first()
                RegistoDiagnostico.regista(context, "[D-cenarios] template[0]=(${t0.lat.toBigDecimal().toPlainString().take(10)},${t0.lon.toBigDecimal().toPlainString().take(10)})")
            }
            if (trajetoViagemAtual.isNotEmpty()) {
                val p0 = trajetoViagemAtual.first()
                RegistoDiagnostico.regista(context, "[D-cenarios] trajeto[0]=(${p0.latitude.toBigDecimal().toPlainString().take(10)},${p0.longitude.toBigDecimal().toPlainString().take(10)})")
                // distancia do primeiro ponto do trajeto ao primeiro
                // ponto do template -- se for > 100km, as coordenadas
                // estao definitivamente em zonas diferentes
                if (cenario.template.isNotEmpty()) {
                    val t0 = cenario.template.first()
                    val dist = distanciaMetros(p0.latitude, p0.longitude, t0.lat, t0.lon)
                    RegistoDiagnostico.regista(context, "[D-cenarios] dist_inicio=${dist.toInt()}m (>5000m = zonas diferentes)")
                }
            }

            val semelhanca = calculaSemelhanca(trajetoViagemAtual, cenario.template, cenario.raioMetros)
            RegistoDiagnostico.regista(context, "[D-cenarios] semelhanca=${(semelhanca*100).toInt()}% (precisa>=${cenario.limiarPercentagem}%)")
            if (semelhanca * 100 >= cenario.limiarPercentagem) {
                repo.marcaDisparado(cenario.id, inicio)
                repo.atualizaUltimoDisparoCenario(cenario.id, System.currentTimeMillis())
                // evento/codigo identificam a origem como um cenario de
                // trajeto (nao um clique real) nos marcadores {evento}/
                // {codigo} das acoes, para quem receber o pedido poder
                // distinguir a causa se precisar
                ExecutorAcoes.executaLista(
                    context = context,
                    acoes = cenario.acoes,
                    evento = "trajeto",
                    codigo = -1,
                    mac = mac,
                    bateria = null,
                    rssi = null
                )
            }
        }
    }
}
