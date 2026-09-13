package pt.blugateway.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recebe o alarme periódico do AlarmManager e reinicia o scan BLE.
 * O AlarmManager acorda o processo mesmo com o ecrã bloqueado e em
 * Doze mode (setAndAllowWhileIdle), ao contrário do Handler da main
 * thread que não dispara quando o processo está suspenso.
 */
class ReiniciaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "pt.blugateway.REINICIA_SCAN") {
            GestorScan.reiniciaSeNecessario(context)
        }
    }
}
