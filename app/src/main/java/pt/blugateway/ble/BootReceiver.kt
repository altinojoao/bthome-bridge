package pt.blugateway.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import pt.blugateway.data.Repositorio

/**
 * Depois de reiniciar o telemóvel, todos os scans BLE são cancelados
 * pelo sistema. Se já havia comandos associados, recomeçamos o
 * serviço em primeiro plano automaticamente — sem isto, o
 * utilizador teria de abrir a app manualmente todas as vezes que o
 * telemóvel reiniciasse.
 *
 * Arrancar um foreground service a partir de BOOT_COMPLETED é
 * permitido (ao contrário de a partir de outros contextos de
 * fundo), mas o serviço tem um prazo curto para chamar
 * startForeground() -- já é o que ServicoGateway.onStartCommand()
 * faz logo no início.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val repo = Repositorio(context)
        if (repo.comandos.value.isEmpty()) return

        val intentServico = Intent(context, ServicoGateway::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intentServico)
        } else {
            context.startService(intentServico)
        }
    }
}
