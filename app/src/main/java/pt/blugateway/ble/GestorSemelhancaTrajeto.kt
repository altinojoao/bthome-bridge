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
    // Fracao maxima do template que o cursor pode saltar entre dois
    // pontos reais consecutivos. Era 0.15 (15%); aumentado para 0.40
    // (40%) porque com intervalos de GPS de 5s a velocidades de carro
    // (30km/h = ~42m entre pontos), o cursor precisa de saltar zonas
    // do template sem cobertura GPS. Com 40%, um unico ponto real pode
    // "cobrir" ate 40% do template se o proximo ponto correspondente
    // estiver dentro do raio -- evita bloquear em lacunas inevitaveis
    // a alta velocidade. Validado em Python para varios cenarios de
    // velocidade e intervalo antes de implementar.
    private const val SALTO_MAXIMO_FRACAO = 0.40

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
    /**
     * Projecta o ponto (px,py) no segmento (ax,ay)-(bx,by).
     * Devolve (t, distancia_perpendicular) onde t in [0,1] e' a
     * posicao na linha (0=inicio, 1=fim do segmento).
     */
    private fun projectaNoSegmento(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Pair<Double, Double> {
        val dx = bx - ax; val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return 0.0 to distanciaMetros(px, py, ax, ay)
        val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        val tc = t.coerceIn(0.0, 1.0)
        return tc to distanciaMetros(px, py, ax + tc * dx, ay + tc * dy)
    }

    fun calculaSemelhanca(
        trajetoAtual: List<PontoTrajeto>,
        template: List<PontoTemplate>,
        raioMetros: Int
    ): Double {
        if (template.isEmpty() || trajetoAtual.isEmpty()) return 0.0
        if (template.size < 2) return 0.0

        // --- Geometria do template ---
        val distancias = (0 until template.size - 1).map { i ->
            distanciaMetros(template[i].lat, template[i].lon, template[i+1].lat, template[i+1].lon)
        }
        val total = distancias.sum()
        if (total == 0.0) return 0.0
        val distAcum = DoubleArray(distancias.size)
        for (i in 1 until distancias.size) distAcum[i] = distAcum[i-1] + distancias[i-1]
        val dAvgTemplate = total / distancias.size

        // Raio: min configurado ou 1.5x espaçamento médio do template
        val raio = maxOf(raioMetros.toDouble(), dAvgTemplate * 1.5)

        // Velocidade GPS estimada (distância média entre pontos GPS consecutivos)
        val dMediaGps = if (trajetoAtual.size >= 2) {
            (0 until trajetoAtual.size - 1).map { i ->
                distanciaMetros(trajetoAtual[i].latitude, trajetoAtual[i].longitude,
                                trajetoAtual[i+1].latitude, trajetoAtual[i+1].longitude)
            }.average()
        } else 50.0

        // Janela de map matching: quantos segmentos do template cobrem
        // a distância que o GPS percorre entre duas leituras consecutivas.
        // Adaptativa: a velocidades altas (GPS espaçado), a janela é maior.
        val janela = if (dAvgTemplate > 0)
            (dMediaGps / dAvgTemplate * 1.5).toInt().coerceIn(3, distancias.size)
        else distancias.size

        // Ponto de entrada: segmento do template mais próximo do
        // primeiro ponto GPS (dentro dos primeiros 50% do template)
        val maxEntrada = (distancias.size / 2).coerceAtLeast(1)
        val p0 = trajetoAtual.first()
        var segEntrada = 0; var melhorDEntrada = Double.MAX_VALUE
        for (i in 0 until maxEntrada) {
            val (_, dp) = projectaNoSegmento(
                p0.latitude, p0.longitude,
                template[i].lat, template[i].lon,
                template[i+1].lat, template[i+1].lon
            )
            if (dp < melhorDEntrada) { melhorDEntrada = dp; segEntrada = i }
        }

        // --- MAP MATCHING ---
        // Para cada ponto GPS, projecta no segmento mais próximo dentro
        // da janela à frente do segmento actual. O progresso em metros
        // só avança (nunca recua mais de 5% do total).
        var segActual = segEntrada
        var maxProgMetros = distAcum[segEntrada]
        var pontosAceites = 0

        for (pt in trajetoAtual) {
            val segFim = (segActual + janela).coerceAtMost(distancias.size)
            var melhorProj = -1.0; var melhorDp = Double.MAX_VALUE; var melhorSeg = segActual
            for (i in segActual until segFim) {
                val (t, dp) = projectaNoSegmento(
                    pt.latitude, pt.longitude,
                    template[i].lat, template[i].lon,
                    template[i+1].lat, template[i+1].lon
                )
                if (dp <= raio && dp < melhorDp) {
                    melhorDp = dp
                    melhorProj = distAcum[i] + t * distancias[i]
                    melhorSeg = i
                }
            }
            if (melhorProj >= 0 && melhorProj > maxProgMetros - total * 0.05) {
                if (melhorProj > maxProgMetros) { maxProgMetros = melhorProj; segActual = melhorSeg }
                pontosAceites++
            }
        }

        val cobertura = pontosAceites.toDouble() / trajetoAtual.size
        val semMM = if (cobertura >= 0.4) (maxProgMetros / total).coerceIn(0.0, 1.0) else 0.0

        // --- LCSS melhorado (fallback e complemento) ---
        // Usado quando o map matching tem pouca cobertura (GPS esparso,
        // lacunas de sinal), e como check de direcção (sentido inverso).
        val saltoIdeal = if (dAvgTemplate > 0)
            ((raioMetros * 2.0) / dAvgTemplate).toInt().coerceAtLeast(1) else 1
        val saltoMaximo = saltoIdeal.coerceIn(
            (template.size * SALTO_MAXIMO_FRACAO).toInt().coerceAtLeast(1),
            (template.size * 0.50).toInt().coerceAtLeast(1)
        )

        // Verificação de direcção (sentido inverso -> penalização)
        val factorDireccao: Double = if (trajetoAtual.size >= 2 && template.size >= 4) {
            val dtLat = trajetoAtual[1].latitude - trajetoAtual[0].latitude
            val dtLon = trajetoAtual[1].longitude - trajetoAtual[0].longitude
            val q = (template.size / 4).coerceAtLeast(1)
            val dmLat = template[q].lat - template[0].lat
            val dmLon = template[q].lon - template[0].lon
            val magT = Math.sqrt(dtLat * dtLat + dtLon * dtLon)
            val magM = Math.sqrt(dmLat * dmLat + dmLon * dmLon)
            if (magT > 0.0 && magM > 0.0) {
                val produto = (dtLat * dmLat + dtLon * dmLon) / (magT * magM)
                when { produto < -0.5 -> 0.3; produto < 0.0 -> 0.7; else -> 1.0 }
            } else 1.0
        } else 1.0

        var cursor = segEntrada; var totalAvancos = 0; var nAvancos = 0
        for (pt in trajetoAtual) {
            val cursorAntes = cursor
            val lim = (cursor + saltoMaximo).coerceAtMost(template.size)
            var i = cursor
            while (i < lim) {
                val d = distanciaMetros(pt.latitude, pt.longitude, template[i].lat, template[i].lon)
                if (d <= raio) { totalAvancos += (i - cursorAntes + 1); nAvancos++; cursor = i + 1; break }
                i++
            }
        }
        val semLcssRaw = cursor.toDouble() / template.size
        val factorSaltos = if (nAvancos > 0) {
            val avanco = (totalAvancos.toDouble() / nAvancos) / template.size
            if (avanco > 0.25) maxOf(0.2, 1.0 - (avanco - 0.25) * 3.0) else 1.0
        } else 1.0
        val semLcss = (semLcssRaw * factorDireccao * factorSaltos).coerceIn(0.0, 1.0)

        // Resultado final: map matching tem prioridade quando tem boa
        // cobertura; LCSS serve de complemento para casos com GPS esparso
        return if (cobertura >= 0.6) maxOf(semMM, semLcss * 0.8)
        else maxOf(semMM, semLcss)
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

            // Agregar historico de todos os MACs do cenario -- o MAC
            // que acionou esta verificacao ja foi calculado acima;
            // os MACs adicionais sao acrescentados, o conjunto e'
            // ordenado por timestamp e duplicados proximos eliminados
            val todosMacs = (listOf(cenario.macComando) + cenario.macsAdicionais).distinct()
            val trajetoCombinado = if (todosMacs.size <= 1) {
                trajetoViagemAtual
            } else {
                val pontosAdicionais = todosMacs
                    .filter { it != mac }
                    .flatMap { outroMac ->
                        val hist = repo.historicoTrajeto(outroMac)
                        val inicioOutro = inicioViagemAtual(hist) ?: return@flatMap emptyList()
                        hist.filter { it.timestamp >= inicioOutro }
                    }
                (trajetoViagemAtual + pontosAdicionais)
                    .sortedBy { it.timestamp }
                    .let { ordenados ->
                        // eliminar pontos gravados < 2s de distancia
                        // temporal uns dos outros (beacon a beacon)
                        val filtrado = mutableListOf<PontoTrajeto>()
                        for (pt in ordenados) {
                            if (filtrado.isEmpty() || pt.timestamp - filtrado.last().timestamp > 2000) {
                                filtrado.add(pt)
                            }
                        }
                        filtrado
                    }
            }
            RegistoDiagnostico.regista(context, "[D-cenarios] trajetoCombinado=${trajetoCombinado.size}pts (${todosMacs.size} beacons)")

            // Log das coordenadas reais dos primeiros pontos, para
            // confirmar se o template e o trajeto estao na mesma zona
            if (cenario.template.isNotEmpty()) {
                val t0 = cenario.template.first()
                RegistoDiagnostico.regista(context, "[D-cenarios] template[0]=(${t0.lat.toBigDecimal().toPlainString().take(10)},${t0.lon.toBigDecimal().toPlainString().take(10)})")
            }
            if (trajetoCombinado.isNotEmpty()) {
                val p0 = trajetoCombinado.first()
                RegistoDiagnostico.regista(context, "[D-cenarios] trajeto[0]=(${p0.latitude.toBigDecimal().toPlainString().take(10)},${p0.longitude.toBigDecimal().toPlainString().take(10)})")
                if (cenario.template.isNotEmpty()) {
                    val t0 = cenario.template.first()
                    val dist = distanciaMetros(p0.latitude, p0.longitude, t0.lat, t0.lon)
                    RegistoDiagnostico.regista(context, "[D-cenarios] dist_inicio=${dist.toInt()}m (>5000m = zonas diferentes)")
                }
            }

            val semelhanca = calculaSemelhanca(trajetoCombinado, cenario.template, cenario.raioMetros)
            RegistoDiagnostico.regista(context, "[D-cenarios] semelhanca=${(semelhanca*100).toInt()}% (precisa>=${cenario.limiarPercentagem}%)")
            if (semelhanca * 100 >= cenario.limiarPercentagem) {
                repo.marcaDisparado(cenario.id, inicio)
                repo.atualizaUltimoDisparoCenario(cenario.id, System.currentTimeMillis())

                // Notificar no CartaoRegisto (UI em tempo real) e no
                // ecrã de Diagnóstico do Trajeto
                val pctFinal = (semelhanca * 100).toInt()
                RegistoDiagnostico.regista(context,
                    "✅ CENÁRIO DISPARADO: '${cenario.nome}' ($pctFinal% >= ${cenario.limiarPercentagem}%)"
                )
                RegistoEventos.adicionaTrajeto(cenario.nome, pctFinal)

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
