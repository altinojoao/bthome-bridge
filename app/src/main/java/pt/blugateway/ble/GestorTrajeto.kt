package pt.blugateway.ble

import android.content.Context
import pt.blugateway.data.Comando
import pt.blugateway.data.OrigemPonto
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.data.Repositorio
import kotlinx.coroutines.launch
import pt.blugateway.net.GestorLocalizacao

/**
 * Grava pontos no historico de trajeto de um comando, em dois
 * momentos possiveis (independentes, um comando pode ter os dois
 * ligados ou so um):
 *
 * - CLIQUE: sempre que ExecutorAcoes.executa/executaLista ja obteve
 *   uma localizacao para os marcadores {lat}/{lon} (comando com
 *   incluirLocalizacao=true) -- ver registaPontoDeClique, chamado
 *   depois da localizacao ja ter sido pedida, para nao pedir GPS
 *   duas vezes ao mesmo clique.
 * - BEACON: sempre que chega qualquer anuncio BTHome de um comando
 *   com modoBeaconTrajeto=true, respeitando intervaloBeaconMs como
 *   espacamento minimo entre pontos gravados -- ver
 *   registaPontoDeBeaconSeNecessario, chamado a partir de
 *   ProcessadorClique para TODO anuncio, nao so cliques.
 */
object GestorTrajeto {

    // MACs com um pedido de localizacao em curso neste momento --
    // evita disparar varios pedidos de GPS em simultaneo para o
    // mesmo comando se anuncios chegarem mais depressa que o tempo
    // que um pedido demora a responder (agora ate 30s, ver
    // GestorLocalizacao.TIMEOUT_MS). Um Set simples e seguro aqui
    // porque so e lido/escrito a partir de Dispatchers.IO, nunca
    // concorrentemente com a UI.
    private val pedidosEmCurso = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Instante (millis) ate ao qual a gravacao esta pausada -- 0
     * significa "nao pausada". Enquanto pausada, nenhum ponto novo
     * e' gravado (nem por clique nem por beacon), independentemente
     * do que os comandos tenham configurado; usado pelo ecra de
     * escolher/desenhar o template de um cenario de trajeto, para a
     * gravacao em fundo nao misturar pontos novos com a escolha que
     * o utilizador esta a fazer nesse momento.
     *
     * E' um INSTANTE LIMITE e nao um simples booleano de proposito:
     * se a UI for destruida pelo sistema sem o onDispose chegar a
     * correr (acontece em Android quando o processo e' morto com o
     * ecra aberto), um booleano ficaria preso em true e a gravacao
     * nunca mais retomava -- e nenhum cenario voltaria a disparar,
     * por falta de pontos novos. Com um limite temporal, o pior caso
     * e' perder alguns minutos de gravacao, nao a funcionalidade
     * toda.
     *
     * @Volatile porque e' lido a partir de Dispatchers.IO
     * (registaPontoDeBeaconSeNecessario) e escrito a partir da UI.
     */
    @Volatile
    private var pausadoAte: Long = 0L

    /** Duracao maxima de uma pausa antes de a gravacao retomar
     *  sozinha -- generosa o suficiente para desenhar/escolher um
     *  trajeto com calma, curta o suficiente para nao inutilizar a
     *  app se algo correr mal. */
    private const val DURACAO_MAXIMA_PAUSA_MS = 15 * 60 * 1000L

    val pausado: Boolean
        get() = System.currentTimeMillis() < pausadoAte

    fun pausaGravacao() {
        pausadoAte = System.currentTimeMillis() + DURACAO_MAXIMA_PAUSA_MS
    }

    fun retomaGravacao() {
        pausadoAte = 0L
    }

    fun registaPontoDeClique(context: Context, mac: String, latitude: Double, longitude: Double) {
        if (pausado) {
            RegistoDiagnostico.regista(context, "trajeto[$mac]: gravacao pausada (a escolher/desenhar cenario), clique ignorado")
            return
        }
        val repo = Repositorio(context)
        repo.adicionaPontoTrajeto(
            mac, PontoTrajeto(latitude, longitude, System.currentTimeMillis(), OrigemPonto.CLIQUE)
        )
    }

    /**
     * So obtem localizacao (que tem custo e demora) se de facto for
     * preciso -- confirma primeiro modoBeaconTrajeto e o intervalo
     * minimo, antes de pedir ao GestorLocalizacao. Atualiza
     * ultimoPontoTrajetoEm no comando so depois de gravar com
     * sucesso, para o intervalo ser medido a partir do ultimo ponto
     * realmente guardado, nao da ultima tentativa.
     */
    suspend fun registaPontoDeBeaconSeNecessario(context: Context, comando: Comando) {
        if (pausado) {
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: gravacao pausada (a escolher/desenhar cenario), ignorado")
            return
        }
        if (!comando.modoBeaconTrajeto) {
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: modoBeaconTrajeto desligado, ignorado")
            return
        }

        val agora = System.currentTimeMillis()
        val ultimo = comando.ultimoPontoTrajetoEm
        if (ultimo != null && (agora - ultimo) < comando.intervaloBeaconMs) {
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: dentro do intervalo (${agora - ultimo}ms < ${comando.intervaloBeaconMs}ms), ignorado")
            return
        }

        // ja ha um pedido de localizacao em curso para este comando
        // (pode acontecer se o intervalo configurado for menor que o
        // tempo que um pedido de GPS demora a responder) -- ignora
        // este anuncio, o proximo tenta de novo
        if (!pedidosEmCurso.add(comando.mac)) {
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: ja ha pedido de GPS em curso, ignorado")
            return
        }
        try {
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: a pedir localizacao...")
            val localizacao = GestorLocalizacao.obtemLocalizacaoAtual(context)
            if (localizacao == null) {
                RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: SEM localizacao (timeout, sem permissao, ou providers desligados)")
                return
            }
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: localizacao obtida com sucesso")

            val repo = Repositorio(context)
            repo.adicionaPontoTrajeto(
                comando.mac, PontoTrajeto(localizacao.first, localizacao.second, agora, OrigemPonto.BEACON)
            )
            repo.atualizaUltimoPontoTrajeto(comando.mac, agora)
            // Se o GPS continuo esta desligado, ve se ja ha movimento
            // para o ligar (ver GestorLocalizacao.avaliaArranque)
            GestorLocalizacao.avaliaArranque(context, localizacao.first, localizacao.second)
            // Sinalizar que já houve pelo menos 1 ponto GPS nesta sessão
            // -- a partir daqui os cenários de trajeto podem ser avaliados
            GestorSemelhancaTrajeto.pontoGravadoNestaSessao = true
            RegistoDiagnostico.regista(context, "trajeto[${comando.mac}]: ponto gravado")
        } finally {
            pedidosEmCurso.remove(comando.mac)
        }
    }

    // ---- Registo de pontos guiado pelo GPS (o beacon so confirma presenca) ----

    private const val DISTANCIA_MIN_PONTO_GPS_M = 40.0
    private const val DISTANCIA_MIN_LENTO_M = 10.0
    private const val TEMPO_LENTO_PONTO_GPS_MS = 30_000L

    private val ocupadoGps = java.util.concurrent.atomic.AtomicBoolean(false)
    private val ultimoPontoGps =
        java.util.concurrent.ConcurrentHashMap<String, Triple<Double, Double, Long>>()

    /**
     * Chamado pelo GestorLocalizacao a cada posicao GPS (modo
     * continuo). Grava um ponto por cada comando com modoBeaconTrajeto
     * cujo beacon esteja presente, e avalia logo os cenarios -- sem
     * esperar pelo proximo anuncio do beacon. Se ja houver um
     * processamento em curso, esta posicao e' ignorada (a seguinte
     * chega daqui a ~1s).
     */
    fun aoReceberPosicaoGps(context: Context, lat: Double, lon: Double) {
        if (pausado) return
        if (!ocupadoGps.compareAndSet(false, true)) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                processaPosicaoGps(context, lat, lon)
            } catch (e: Exception) {
                RegistoDiagnostico.regista(context, "trajeto[gps]: erro ${e.message}")
            } finally {
                ocupadoGps.set(false)
            }
        }
    }

    private suspend fun processaPosicaoGps(context: Context, lat: Double, lon: Double) {
        val repo = Repositorio(context)
        val agora = System.currentTimeMillis()
        val macsParaAvaliar = mutableListOf<String>()
        for (cmd in repo.comandos.value) {
            if (!cmd.modoBeaconTrajeto) continue
            if (!pt.blugateway.ble.GestorAlcance.estaPresente(cmd.mac)) continue
            val ult = ultimoPontoGps[cmd.mac]
            if (ult != null) {
                val d = GestorSemelhancaTrajeto.distanciaMetros(ult.first, ult.second, lat, lon)
                val dt = agora - ult.third
                val gravar = d >= DISTANCIA_MIN_PONTO_GPS_M ||
                    (dt >= TEMPO_LENTO_PONTO_GPS_MS && d >= DISTANCIA_MIN_LENTO_M)
                if (!gravar) continue
            }
            repo.adicionaPontoTrajeto(cmd.mac, PontoTrajeto(lat, lon, agora, OrigemPonto.BEACON))
            repo.atualizaUltimoPontoTrajeto(cmd.mac, agora)
            ultimoPontoGps[cmd.mac] = Triple(lat, lon, agora)
            macsParaAvaliar.add(cmd.mac)
        }
        if (macsParaAvaliar.isEmpty()) return
        GestorSemelhancaTrajeto.pontoGravadoNestaSessao = true
        for (mac in macsParaAvaliar) {
            RegistoDiagnostico.regista(context, "trajeto[$mac]: ponto gravado por GPS (modo continuo)")
            GestorSemelhancaTrajeto.verificaCenarios(context, mac)
        }
    }
}
