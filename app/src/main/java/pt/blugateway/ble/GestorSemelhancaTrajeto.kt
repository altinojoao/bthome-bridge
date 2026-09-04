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

        var inicioViagemAtual = 0
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
                inicioViagemAtual = j + 1
                i = j + 1
            } else {
                i++
            }
        }

        return if (inicioViagemAtual < pontosOrdenados.size) {
            pontosOrdenados[inicioViagemAtual].timestamp
        } else null
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
     * novo (ver DialogoCenariosTrajeto). A mais recente vem por
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
        if (historico.isEmpty()) return

        val inicioViagem = inicioViagemAtual(historico) ?: return
        val trajetoViagemAtual = historico.filter { it.timestamp >= inicioViagem }

        for (cenario in cenarios) {
            if (repo.jaDisparadoNestaViagem(cenario.id, inicioViagem)) continue

            val semelhanca = calculaSemelhanca(trajetoViagemAtual, cenario.template, cenario.raioMetros)
            if (semelhanca * 100 >= cenario.limiarPercentagem) {
                repo.marcaDisparado(cenario.id, inicioViagem)
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
