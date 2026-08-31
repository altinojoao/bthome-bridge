package pt.blugateway

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import pt.blugateway.ble.GestorSons
import pt.blugateway.data.Repositorio

/**
 * Prepara o canal de notificação do serviço em primeiro plano.
 * O arranque da escuta em si fica a cargo da MainActivity, depois
 * de confirmadas as permissões -- a partir do Android 12,
 * startForegroundService() chamado sem uma Activity visível pode
 * lançar ForegroundServiceStartNotAllowedException.
 */
class GatewayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        criaCanalNotificacao()
        // restaura a preferencia de som guardada -- GestorSons arranca
        // sempre com som ligado por omissao, mas o utilizador pode ja
        // o ter desligado numa sessao anterior
        GestorSons.defineSomAtivo(Repositorio(this).somAtivo())
    }

    private fun criaCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_SERVICO,
                getString(R.string.canal_servico_nome),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.canal_servico_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(canal)
        }
    }

    companion object {
        const val CANAL_SERVICO = "gateway_servico"
    }
}
