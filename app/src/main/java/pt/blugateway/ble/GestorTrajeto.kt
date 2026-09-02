package pt.blugateway.ble

import android.content.Context
import pt.blugateway.data.Comando
import pt.blugateway.data.OrigemPonto
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.data.Repositorio
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

    fun registaPontoDeClique(context: Context, mac: String, latitude: Double, longitude: Double) {
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
        if (!comando.modoBeaconTrajeto) return

        val agora = System.currentTimeMillis()
        val ultimo = comando.ultimoPontoTrajetoEm
        if (ultimo != null && (agora - ultimo) < comando.intervaloBeaconMs) return

        val localizacao = GestorLocalizacao.obtemLocalizacaoAtual(context) ?: return

        val repo = Repositorio(context)
        repo.adicionaPontoTrajeto(
            comando.mac, PontoTrajeto(localizacao.first, localizacao.second, agora, OrigemPonto.BEACON)
        )
        repo.atualizaUltimoPontoTrajeto(comando.mac, agora)
    }
}
