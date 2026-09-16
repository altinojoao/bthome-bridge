package pt.blugateway.ble

import android.content.Context
import kotlinx.coroutines.sync.withLock
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.data.BarreiraCheckpoint
import pt.blugateway.data.PontoTemplate
import pt.blugateway.data.OrigemPonto
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

    // Padrões globais usados pelo mapa de trajeto (modo "última viagem")
    // Os cenários usam os seus próprios raioGeofenceMetros e minutosParaNovaViagem
    internal const val RAIO_PARAGEM_METROS_PADRAO = 150.0
    internal const val TEMPO_MIN_PARAGEM_MS_PADRAO = 15L * 60 * 1000

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
    /**
     * Detecta o início da viagem actual usando um GEOFENCE real.
     *
     * Uma "paragem" é: o utilizador ficou dentro de um círculo de
     * [raioGeofenceMetros] durante pelo menos [tempoMinimoParagemMs].
     * O centro do geofence é o primeiro ponto de cada período estático.
     *
     * Ao sair do geofence (distância ao centro > raioGeofenceMetros),
     * começa uma nova viagem. Este critério é muito mais robusto que
     * comparar pontos consecutivos (que falhava com GPS ruidoso).
     *
     * Retrocompatível: chamadas sem parâmetros usam os valores padrão
     * (150m, 15min), que são também os novos padrões dos cenários.
     */
    fun inicioViagemAtual(
        pontosOrdenados: List<PontoTrajeto>,
        raioGeofenceMetros: Double = RAIO_PARAGEM_METROS_PADRAO,
        tempoMinimoParagemMs: Long = TEMPO_MIN_PARAGEM_MS_PADRAO
    ): Long? {
        if (pontosOrdenados.isEmpty()) return null
        if (pontosOrdenados.size == 1) return pontosOrdenados[0].timestamp

        // Percorrer os pontos identificando períodos de permanência
        // dentro de um geofence
        val fronteiras = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < pontosOrdenados.size) {
            // Centro do geofence: posição do primeiro ponto deste período
            val centroLat = pontosOrdenados[i].latitude
            val centroLon = pontosOrdenados[i].longitude
            var j = i
            // Avançar enquanto os pontos seguintes estiverem dentro do geofence
            while (j + 1 < pontosOrdenados.size) {
                val d = distanciaMetros(
                    centroLat, centroLon,
                    pontosOrdenados[j + 1].latitude,
                    pontosOrdenados[j + 1].longitude
                )
                if (d > raioGeofenceMetros) break
                j++
            }
            val duracaoParagem = pontosOrdenados[j].timestamp - pontosOrdenados[i].timestamp
            if (duracaoParagem >= tempoMinimoParagemMs) {
                fronteiras.add(i to j)
                i = j + 1
            } else {
                i++
            }
        }

        if (fronteiras.isEmpty()) return pontosOrdenados[0].timestamp

        val (ultimoInicio, ultimoFim) = fronteiras.last()

        // Se a ultima paragem vai ate ao fim, o utilizador está parado
        // AGORA -- tipicamente porque acabou de chegar ao destino.
        // A viagem relevante é a que levou até esta paragem.
        if (ultimoFim == pontosOrdenados.size - 1) {
            return if (fronteiras.size >= 2) {
                // Há uma paragem anterior: a viagem é entre as duas paragens
                val inicioViagem = fronteiras[fronteiras.size - 2].second + 1
                pontosOrdenados[inicioViagem].timestamp
            } else {
                // Só há uma paragem (a chegada): a viagem começa no ponto 0.
                // Isto acontece quando não houve paragem longa na origem
                // (ex: beacon em alcance desde a saída de casa até chegar
                // ao destino, sem paragem intermédia de 15+ min).
                // Devolver o início do histórico para que a semelhança
                // seja calculada sobre todos os pontos disponíveis.
                pontosOrdenados[0].timestamp
            }
        }

        // Há movimento depois da última paragem -- essa é a viagem actual.
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

    // Comprimento por omissao de cada barreira de checkpoint, em metros.
    // Suficientemente largo para cobrir a rua/via toda incluindo erro
    // normal de GPS (10-20m), sem ser tao largo que duas barreiras
    // vizinhas se sobreponham num percurso com curvas apertadas.
    private const val COMPRIMENTO_BARREIRA_METROS = 90.0

    // Numero de checkpoints gerados automaticamente a partir de um
    // template denso. Determina a granularidade do progresso: com N
    // checkpoints, a percentagem só pode assumir múltiplos de 100/N.
    // Com 4 (valor antigo), a granularidade era de 25% -- qualquer
    // limiarPercentagem que não fosse múltiplo de 25 (ex: 70%, 75%,
    // 76%, 80%) só conseguia disparar aos 100%, porque o próximo
    // múltiplo abaixo (75%) ficava sempre aquém do limiar configurado.
    // Com 10 checkpoints a granularidade passa a ser de 10%, cobrindo
    // correctamente a esmagadora maioria dos limiares configuráveis.
    private const val NUM_CHECKPOINTS_OMISSAO = 10

    /**
     * Gera checkpoints (barreiras numeradas) a partir de um template
     * denso, espacados por DISTANCIA PERCORRIDA (nao por indice) --
     * um template com pontos irregularmente espacados (GPS mais denso
     * nas curvas, mais esparso em troços rectos) ainda produz
     * checkpoints uniformemente distribuidos ao longo do percurso
     * real. O ultimo checkpoint fica sempre no ultimo ponto do
     * template (o destino).
     *
     * Em cada ponto escolhido, a barreira e tracada PERPENDICULAR a
     * direcao local do percurso (vetor entre o ponto anterior e o
     * seguinte no template), centrada nesse ponto, com comprimento
     * COMPRIMENTO_BARREIRA_METROS.
     */
    fun geraCheckpoints(
        template: List<PontoTemplate>,
        numCheckpoints: Int = NUM_CHECKPOINTS_OMISSAO
    ): List<BarreiraCheckpoint> {
        if (template.size < 2 || numCheckpoints < 1) return emptyList()

        val distancias = (0 until template.size - 1).map { i ->
            distanciaMetros(template[i].lat, template[i].lon, template[i + 1].lat, template[i + 1].lon)
        }
        val total = distancias.sum()
        if (total <= 0.0) return emptyList()
        val distAcum = DoubleArray(template.size)
        for (i in 1 until template.size) distAcum[i] = distAcum[i - 1] + distancias[i - 1]

        // indice do ponto do template mais proximo de uma dada
        // distancia acumulada ao longo do percurso
        fun indicePara(distAlvo: Double): Int {
            var melhor = 0; var melhorDif = Double.MAX_VALUE
            for (i in template.indices) {
                val dif = Math.abs(distAcum[i] - distAlvo)
                if (dif < melhorDif) { melhorDif = dif; melhor = i }
            }
            return melhor
        }

        fun tracaBarreira(indice: Int, ordem: Int): BarreiraCheckpoint {
            val ptCentro = template[indice]
            val ptAntes = template[(indice - 1).coerceAtLeast(0)]
            val ptDepois = template[(indice + 1).coerceAtMost(template.size - 1)]
            // direcao local do percurso (vetor antes -> depois)
            var dLat = ptDepois.lat - ptAntes.lat
            var dLon = ptDepois.lon - ptAntes.lon
            val mag = Math.sqrt(dLat * dLat + dLon * dLon)
            if (mag > 0) { dLat /= mag; dLon /= mag } else { dLat = 0.0; dLon = 1.0 }
            // perpendicular: roda 90 graus (-dLon, dLat)
            val perpLat = -dLon; val perpLon = dLat
            // metros -> graus aproximados (suficiente para uma barreira curta)
            val metrosParaGrausLat = COMPRIMENTO_BARREIRA_METROS / 2.0 / 111_320.0
            val metrosParaGrausLon = COMPRIMENTO_BARREIRA_METROS / 2.0 /
                (111_320.0 * Math.cos(Math.toRadians(ptCentro.lat)).coerceAtLeast(0.01))
            return BarreiraCheckpoint(
                ordem = ordem,
                latA = ptCentro.lat + perpLat * metrosParaGrausLat,
                lonA = ptCentro.lon + perpLon * metrosParaGrausLon,
                latB = ptCentro.lat - perpLat * metrosParaGrausLat,
                lonB = ptCentro.lon - perpLon * metrosParaGrausLon
            )
        }

        val checkpoints = mutableListOf<BarreiraCheckpoint>()
        for (k in 1..numCheckpoints) {
            val distAlvo = total * k / numCheckpoints.toDouble()
            val indice = if (k == numCheckpoints) template.size - 1 else indicePara(distAlvo)
            checkpoints.add(tracaBarreira(indice, k))
        }
        return checkpoints
    }

    /**
     * Verifica se o utilizador está PARADO AGORA, olhando apenas para
     * os últimos pontos recentes do trajeto (não o histórico completo
     * desde o início da viagem). Usado como guarda antes de avaliar
     * checkpoints: se o utilizador está parado (dentro de um raio
     * pequeno nos últimos minutos), a navegação não está em curso, e
     * qualquer cruzamento geométrico de uma barreira é jitter de GPS,
     * não deslocação real -- ver caso confirmado em produção: 480
     * pontos de ruído GPS indoor ao longo de 4h parado em casa
     * cruzaram as 4 barreiras de um template curto (697m) por puro
     * acaso estatístico, mesmo sem o utilizador se ter deslocado.
     */
    private fun utilizadorParadoAgora(
        trajetoAtual: List<PontoTrajeto>,
        raioMetros: Double = 100.0,
        janelaMs: Long = 3 * 60_000L
    ): Boolean {
        if (trajetoAtual.size < 2) return false
        val ultimoTs = trajetoAtual.last().timestamp
        val recentes = trajetoAtual.filter { ultimoTs - it.timestamp <= janelaMs }
        if (recentes.size < 2) return false
        val centro = recentes.first()
        return recentes.all {
            distanciaMetros(centro.latitude, centro.longitude, it.latitude, it.longitude) <= raioMetros
        }
    }

    /**
     * Migração automática: cenários gravados antes dos checkpoints
     * existirem ainda só têm o template denso -- gerar os checkpoints
     * agora e guardar, sem obrigar a regravar o cenário.
     */
    private fun checkpointsDoCenario(
        context: Context,
        repo: Repositorio,
        cenario: CenarioTrajeto
    ): List<BarreiraCheckpoint> {
        val existentes = cenario.checkpoints
        if (existentes.isNotEmpty()) {
            // Verificar se a granularidade actual permite atingir o
            // limiar configurado. Com N checkpoints, os múltiplos de
            // progresso possíveis são 0, 100/N, 200/N, ..., 100 -- se
            // nenhum desses valores (abaixo de 100) for >= limiar, o
            // cenário só consegue disparar aos 100%, mesmo que o
            // utilizador tenha configurado um limiar menor (ex: 4
            // checkpoints = saltos de 25% em 25%; um limiar de 76%
            // cai sempre entre o 75% do 3º checkpoint e o 100% do 4º
            // -- matematicamente impossível de atingir com só 4
            // checkpoints). Regenerar com mais checkpoints (a UI
            // actual não permite edição manual de checkpoints
            // individuais, só a geração automática, por isso isto
            // não descarta nenhum ajuste feito pelo utilizador).
            val algumAbaixoDoLimiar = (1 until existentes.size).any { i ->
                (i * 100 / existentes.size) >= cenario.limiarPercentagem
            }
            if (algumAbaixoDoLimiar || existentes.size >= NUM_CHECKPOINTS_OMISSAO) return existentes
            val regenerados = geraCheckpoints(cenario.template, NUM_CHECKPOINTS_OMISSAO)
            if (regenerados.isNotEmpty()) {
                RegistoDiagnostico.regista(
                    context,
                    "[D-cenarios] cenario='${cenario.nome}' checkpoints regenerados (${existentes.size}->${regenerados.size}) -- ${existentes.size} checkpoints não permitiam atingir o limiar de ${cenario.limiarPercentagem}%"
                )
                repo.atualizaCenarioTrajeto(cenario.copy(checkpoints = regenerados))
                // Reiniciar qualquer progresso guardado -- o índice
                // antigo (relativo ao array de checkpoints anterior)
                // fica dessincronizado do novo array: os checkpoints
                // regenerados têm posições geográficas DIFERENTES dos
                // antigos (espaçados por fracção de distância, não os
                // mesmos pontos). Sem isto, um progresso já avançado
                // (ex: 3/4) ficaria a apontar para checkpoints[3] do
                // NOVO array de 10 -- um ponto geográfico que o
                // utilizador pode já ter ultrapassado fisicamente,
                // impedindo o cenário de disparar para sempre nessa
                // viagem (confirmado como causa provável de cenários
                // que deixam de disparar após a migração automática).
                repo.reiniciaProgressoCheckpoints(cenario.id)
                return regenerados
            }
            return existentes
        }
        val gerados = geraCheckpoints(cenario.template)
        if (gerados.isNotEmpty()) {
            repo.atualizaCenarioTrajeto(cenario.copy(checkpoints = gerados))
        }
        return gerados
    }

    private fun ladoDaLinha(p1x: Double, p1y: Double, p2x: Double, p2y: Double, p3x: Double, p3y: Double): Double =
        (p2x - p1x) * (p3y - p1y) - (p2y - p1y) * (p3x - p1x)

    /**
     * Verifica se o segmento GPS (pAx,pAy)-(pBx,pBy) cruza a barreira
     * do checkpoint (segmento cA-cB). Interseccao classica de dois
     * segmentos via sinais de produto vetorial: os extremos de cada
     * segmento tem de estar em lados opostos do outro segmento.
     */
    private fun segmentosIntersectam(
        pAx: Double, pAy: Double, pBx: Double, pBy: Double,
        cAx: Double, cAy: Double, cBx: Double, cBy: Double
    ): Boolean {
        val d1 = ladoDaLinha(cAx, cAy, cBx, cBy, pAx, pAy)
        val d2 = ladoDaLinha(cAx, cAy, cBx, cBy, pBx, pBy)
        val d3 = ladoDaLinha(pAx, pAy, pBx, pBy, cAx, cAy)
        val d4 = ladoDaLinha(pAx, pAy, pBx, pBy, cBx, cBy)
        // Segmentos exactamente colineares (ambos os lados = 0) são
        // um caso ambíguo -- não contar como cruzamento para evitar
        // falsos positivos com segmentos paralelos à barreira.
        if (d1 == 0.0 && d2 == 0.0) return false
        // Usar >=/<= (não >/<) para incluir o caso em que um ponto do
        // segmento cai EXACTAMENTE sobre a linha da barreira (d=0) --
        // situação legítima e comum quando o ponto de origem coincide
        // com o próprio ponto usado para gerar a barreira (ex: o
        // simulador percorre os pontos do template, e cada barreira é
        // centrada num desses pontos por construção).
        return ((d1 >= 0 && d2 <= 0) || (d1 <= 0 && d2 >= 0)) &&
               ((d3 >= 0 && d4 <= 0) || (d3 <= 0 && d4 >= 0))
    }

    /**
     * Avalia os checkpoints de um cenario contra o trajeto atual,
     * a partir do proximo checkpoint esperado (progresso persistido
     * no Repositorio, por viagem). Substitui a comparacao continua
     * de trajeto (map matching / LCSS) por uma verificacao simples e
     * robusta: "o GPS cruzou esta barreira, por ordem?"
     *
     * Sem projecoes em segmentos do template, sem janela adaptativa,
     * sem calculo de percentagem de progresso, sem vetor de direcao
     * separado -- a ordem sequencial das barreiras E a verificacao
     * de direcao: andar ao contrario nunca cruza os checkpoints na
     * ordem certa.
     *
     * Devolve o novo indice do proximo checkpoint esperado (>=
     * indiceAtual). Se igual a checkpoints.size, todos foram
     * atravessados -- o cenario deve disparar.
     */
    fun avaliaCheckpoints(
        trajetoAtual: List<PontoTrajeto>,
        checkpoints: List<BarreiraCheckpoint>,
        indiceAtual: Int
    ): Int {
        if (checkpoints.isEmpty() || indiceAtual >= checkpoints.size) return indiceAtual
        if (trajetoAtual.isEmpty()) return indiceAtual

        // Recuperação de progresso preso: se a posição mais recente
        // está muito mais perto de um checkpoint MAIS ADIANTE do que
        // do checkpoint pendente actual, avança directamente para
        // esse -- cobre o caso em que o utilizador esteve parado
        // (avaliação suspensa) antes de cruzar fisicamente o
        // checkpoint do meio do percurso, retomou a marcha e já
        // está perto do fim, mas o índice nunca teve oportunidade de
        // avançar sequencialmente porque a suspensão intermédia
        // "saltou" essa barreira sem a testar (confirmado: utilizador
        // a 2m do último checkpoint, progresso preso no primeiro).
        // Só avança para um checkpoint cuja distância seja
        // decisivamente menor (<30m) -- evita saltos por coincidência
        // geométrica com um checkpoint fisicamente próximo por acaso.
        val ultimo = trajetoAtual.last()
        var indice = indiceAtual
        for (i in indiceAtual until checkpoints.size) {
            val cp = checkpoints[i]
            val meioLat = (cp.latA + cp.latB) / 2
            val meioLon = (cp.lonA + cp.lonB) / 2
            if (distanciaMetros(ultimo.latitude, ultimo.longitude, meioLat, meioLon) <= 30.0) {
                indice = i + 1
            }
        }

        if (trajetoAtual.size < 2) return indice
        for (i in 0 until trajetoAtual.size - 1) {
            if (indice >= checkpoints.size) break
            val p1 = trajetoAtual[i]; val p2 = trajetoAtual[i + 1]
            val cp = checkpoints[indice]
            if (segmentosIntersectam(
                    p1.latitude, p1.longitude, p2.latitude, p2.longitude,
                    cp.latA, cp.lonA, cp.latB, cp.lonB
                )) {
                indice++
            }
        }
        return indice
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
                if (d > RAIO_PARAGEM_METROS_PADRAO) break
                j++
            }
            val duracaoParagem = pontosOrdenados[j].timestamp - pontosOrdenados[i].timestamp
            if (duracaoParagem >= TEMPO_MIN_PARAGEM_MS_PADRAO) {
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
    /**
     * Flag de sessão: só avaliar cenários após ter gravado pelo menos
     * 1 ponto GPS nesta sessão. Evita que ao reiniciar a app (update,
     * reboot) o histórico acumulado de viagens anteriores dispare
     * cenários imediatamente sem nenhum movimento real ter ocorrido.
     * Reset para false a cada arranque do processo (valor inicial).
     */
    @Volatile
    var pontoGravadoNestaSessao: Boolean = false

    private val mutexProcesso = kotlinx.coroutines.sync.Mutex()

    suspend fun verificaCenarios(context: Context, mac: String) = mutexProcesso.withLock {
        verificaCenariosInterno(context, mac)
    }

    private suspend fun verificaCenariosInterno(context: Context, mac: String) {
        // Não avaliar cenários até ter pelo menos 1 ponto GPS gravado
        // nesta sessão. Ao reiniciar a app (update, reboot, crash),
        // o histórico acumulado de viagens anteriores já pode ter
        // semelhança suficiente para disparar -- mas não houve nenhum
        // movimento real desde que a app arrancou. Só avaliar após
        // o GestorTrajeto ter gravado o primeiro ponto desta sessão.
        if (!pontoGravadoNestaSessao) return

        // Exigir que o beacon que desencadeou esta avaliação esteja
        // realmente presente (RSSI válido e recente, com histerese) --
        // sem isto, um cenário podia ser avaliado e disparar mesmo que
        // o beacon já não estivesse ao alcance há algum tempo, usando
        // apenas pontos GPS históricos que não representam navegação
        // acompanhada em tempo real.
        if (!GestorAlcance.estaPresente(mac)) return

        val repo = Repositorio(context)
        val cenarios = repo.cenariosTrajetoPara(mac).filter { it.ativo }
        if (cenarios.isEmpty()) return

        val historico = repo.historicoTrajeto(mac)
        RegistoDiagnostico.regista(context, "[D-cenarios] mac=$mac historico=${historico.size}pts")
        if (historico.isEmpty()) return

        // Usar os parâmetros de geofence do primeiro cenário activo como
        // referência para calcular o inicioViagem do MAC principal.
        // Se os cenários tiverem parâmetros diferentes, cada um recalcula
        // o seu próprio inicioOutro abaixo.
        val cenarioPrincipal = cenarios.first()
        val raioGeo = cenarioPrincipal.raioGeofenceMetros.toDouble()
        val tempoParagem = cenarioPrincipal.minutosParaNovaViagem * 60_000L

        val inicioViagem = inicioViagemAtual(historico, raioGeo, tempoParagem)
        RegistoDiagnostico.regista(context, "[D-cenarios] inicioViagem=$inicioViagem (null=sem viagem ativa) geo=${cenarioPrincipal.raioGeofenceMetros}m/${cenarioPrincipal.minutosParaNovaViagem}min")
        val inicio = inicioViagem ?: return
        val trajetoViagemAtual = historico.filter { it.timestamp >= inicio }
        RegistoDiagnostico.regista(context, "[D-cenarios] trajetoAtual=${trajetoViagemAtual.size}pts")

        for (cenario in cenarios) {
            val jaDisparado = repo.jaDisparadoNestaViagem(cenario.id, inicio)
            RegistoDiagnostico.regista(context, "[D-cenarios] cenario='${cenario.nome}' id=${cenario.id.take(8)} template=${cenario.template.size}pts jaDisparado=$jaDisparado limiar=${cenario.limiarPercentagem}%")
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
                        val inicioOutro = inicioViagemAtual(
                            hist,
                            cenario.raioGeofenceMetros.toDouble(),
                            cenario.minutosParaNovaViagem * 60_000L
                        ) ?: return@flatMap emptyList()
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

            // Enriquecer o trajetoCombinado com a posição GPS actual
            // (cache do GestorLocalizacao) sem gravar um novo ponto.
            // Elimina o atraso entre atingir o limiar e o trigger:
            // antes, a semelhança só avançava quando o próximo ponto
            // era gravado (até 5s, = até 42m a 30km/h depois do limiar).
            // Agora o mapa de trajeto inclui sempre o ponto mais recente.
            val posicaoAtual = pt.blugateway.net.GestorLocalizacao.ultimaLocalizacaoCache()
            val trajetoComPosAtual = if (posicaoAtual != null) {
                val agora = System.currentTimeMillis()
                val ultimoPonto = trajetoCombinado.lastOrNull()
                // Só adicionar se a posição em cache for mais recente que o último ponto
                // e não for uma duplicata (> 3m de distância do último ponto)
                if (ultimoPonto == null ||
                    (agora - (ultimoPonto.timestamp) > 1_000L &&
                     distanciaMetros(posicaoAtual.first, posicaoAtual.second,
                                     ultimoPonto.latitude, ultimoPonto.longitude) > 3.0)) {
                    trajetoCombinado + PontoTrajeto(
                        posicaoAtual.first, posicaoAtual.second, agora, OrigemPonto.BEACON
                    )
                } else trajetoCombinado
            } else trajetoCombinado

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
                    // calcular tambem a distancia ao ponto do template mais proximo
                    val (iMaisProximo, dMaisProximo) = cenario.template
                        .mapIndexed { i, t -> i to distanciaMetros(p0.latitude, p0.longitude, t.lat, t.lon) }
                        .minByOrNull { it.second } ?: (0 to dist)
                    val totalTemplate = cenario.template.zipWithNext()
                        .sumOf { (a, b) -> distanciaMetros(a.lat, a.lon, b.lat, b.lon) }
                    RegistoDiagnostico.regista(context, "[D-cenarios] dist_inicio=${dist.toInt()}m | ponto_mais_proximo=template[$iMaisProximo] a ${dMaisProximo.toInt()}m | comprimento_template=${totalTemplate.toInt()}m")
                }
            }

            // Migração automática: cenários gravados antes dos
            // checkpoints existirem ainda só têm o template denso --
            // gerar os checkpoints agora e guardar, sem obrigar a
            // regravar o cenário.
            val checkpoints = checkpointsDoCenario(context, repo, cenario)
            if (checkpoints.isEmpty()) {
                RegistoDiagnostico.regista(context, "[D-cenarios] cenario='${cenario.nome}' sem checkpoints válidos, ignorado")
                continue
            }

            val indiceAntes = repo.proximoCheckpointEsperado(cenario.id, inicio)

            // Guarda contra jitter de GPS: se o utilizador está parado
            // agora (últimos minutos dentro de um raio pequeno) E
            // ainda está LONGE do próximo checkpoint pendente, suspende
            // a avaliação -- não há navegação em curso, e qualquer
            // cruzamento geométrico seria ruído acumulado, não
            // deslocação real (caso confirmado: jitter GPS indoor ao
            // longo de horas, longe de qualquer checkpoint).
            //
            // Mas se o utilizador já está parado PERTO de QUALQUER
            // checkpoint ainda pendente (não só o imediatamente
            // seguinte), isso é sinal de navegação real a decorrer --
            // não suspender. Comparar só com o checkpoint imediatamente
            // seguinte falha quando o progresso ficou preso a meio da
            // viagem (ex: parou 3+ minutos num semáforo/trânsito a
            // meio do percurso, a guarda suspendeu aí, e o índice
            // nunca mais avançou) -- o utilizador pode retomar a
            // marcha e chegar fisicamente perto do FIM do percurso,
            // mas a guarda continua a comparar com o checkpoint do
            // MEIO, que ficou geograficamente para trás, nunca
            // desbloqueando (confirmado: parado a 2m do checkpoint
            // final, guarda continuava a suspender indefinidamente).
            val pertoDeAlgumCheckpointPendente = trajetoComPosAtual.isNotEmpty() &&
                run {
                    val ultimo = trajetoComPosAtual.last()
                    checkpoints.drop(indiceAntes).any { cp ->
                        val meioLat = (cp.latA + cp.latB) / 2
                        val meioLon = (cp.lonA + cp.lonB) / 2
                        distanciaMetros(ultimo.latitude, ultimo.longitude, meioLat, meioLon) <= 150.0
                    }
                }
            if (!pertoDeAlgumCheckpointPendente && utilizadorParadoAgora(trajetoComPosAtual)) {
                RegistoDiagnostico.regista(context, "[D-cenarios] cenario='${cenario.nome}' utilizador parado -- avaliação de checkpoints suspensa")
                continue
            }

            val indiceDepois = avaliaCheckpoints(trajetoComPosAtual, checkpoints, indiceAntes)
            if (indiceDepois != indiceAntes) {
                repo.avancaCheckpoint(cenario.id, inicio, indiceDepois)
            }
            // Percentagem de progresso = fracção de checkpoints já
            // atravessados. Restaura o comportamento de "disparar aos
            // X%" que existia com o map matching -- perdido na migração
            // para checkpoints, que por omissão só disparava com 4/4
            // (100%), obrigando a esperar até já estar quase parado no
            // destino em vez de disparar durante a aproximação.
            val progressoPercent = (indiceDepois * 100) / checkpoints.size
            RegistoDiagnostico.regista(
                context,
                "[D-cenarios] cenario='${cenario.nome}' checkpoint ${indiceDepois}/${checkpoints.size} ($progressoPercent%, precisa>=${cenario.limiarPercentagem}%)" +
                    if (indiceDepois < checkpoints.size) " (falta atravessar barreira ${indiceDepois + 1})" else " (todos atravessados)"
            )

            if (progressoPercent >= cenario.limiarPercentagem && !repo.jaDisparadoNestaViagem(cenario.id, inicio)) {
                if (!cenario.dentroDaJanelaHoraria()) {
                    RegistoDiagnostico.regista(context, "[D-cenarios] cenario='${cenario.nome}' fora da janela horária configurada -- disparo suprimido")
                    continue
                }
                repo.marcaDisparado(cenario.id, inicio)
                repo.atualizaUltimoDisparoCenario(cenario.id, System.currentTimeMillis())

                // Notificar no CartaoRegisto (UI em tempo real) e no
                // ecrã de Diagnóstico do Trajeto
                val tsDisparo = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault())
                    .format(java.util.Date())
                RegistoDiagnostico.regista(context,
                    "✅ CENÁRIO DISPARADO: '${cenario.nome}' (${indiceDepois}/${checkpoints.size} checkpoints, $progressoPercent% >= ${cenario.limiarPercentagem}%) às $tsDisparo | sessao=$pontoGravadoNestaSessao"
                )
                RegistoEventos.adicionaTrajeto(cenario.nome, 100)

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
