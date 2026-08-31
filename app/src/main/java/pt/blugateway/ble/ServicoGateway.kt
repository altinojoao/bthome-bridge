package pt.blugateway.ble

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.blugateway.GatewayApp
import pt.blugateway.MainActivity
import pt.blugateway.R
import pt.blugateway.data.Repositorio

/**
 * Serviço em primeiro plano (foreground service) com notificação
 * persistente. Isto é o que distingue a app nativa de uma simples
 * página web: sinaliza explicitamente ao sistema que o utilizador
 * pediu para esta tarefa continuar mesmo com o ecrã apagado ou
 * outra app em primeiro plano — o que reduz (não elimina) a
 * probabilidade de o Android suspender o processo, sobretudo em
 * fabricantes agressivos com gestão de bateria (Xiaomi, Samsung,
 * Huawei). Uma página de browser nunca pode pedir este privilégio;
 * só apps instaladas o conseguem.
 *
 * O scan BLE em si já sobrevive ao processo morrer, graças ao
 * PendingIntent-based scanning em GestorScan — este serviço não
 * substitui isso, reforça-o: mantém o processo vivo por mais tempo
 * e informa o utilizador (via notificação) de que a app está ativa.
 */
class ServicoGateway : Service() {

    companion object {
        private const val ID_NOTIFICACAO = 1
        const val ACAO_PARAR = "pt.blugateway.PARAR_SERVICO"

        private val _ativo = MutableStateFlow(false)
        val ativo: StateFlow<Boolean> = _ativo
    }

    override fun onCreate() {
        super.onCreate()
        _ativo.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            pararServico()
            return START_NOT_STICKY
        }

        val notificacao = construirNotificacao()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ID_NOTIFICACAO,
                notificacao,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(ID_NOTIFICACAO, notificacao)
        }

        GestorScan.iniciaEscuta(applicationContext)
        GestorScan.iniciaVigilante(applicationContext)
        GestorAlcance.inicia(applicationContext)

        // START_STICKY: se o sistema matar o processo por falta de
        // memória, tenta recriar o serviço assim que houver recursos
        // -- reforça a continuidade, embora não seja garantia absoluta.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _ativo.value = false
        super.onDestroy()
    }

    private fun pararServico() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun construirNotificacao(): Notification {
        val repo = Repositorio(applicationContext)
        val nComandos = repo.comandos.value.size

        val intentAbrir = Intent(this, MainActivity::class.java)
        val pendingAbrir = PendingIntent.getActivity(
            this, 0, intentAbrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, GatewayApp.CANAL_SERVICO)
            .setContentTitle(getString(R.string.notif_servico_titulo))
            .setContentText(getString(R.string.notif_servico_texto, nComandos))
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentIntent(pendingAbrir)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
