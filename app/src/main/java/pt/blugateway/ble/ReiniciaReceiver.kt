package pt.blugateway.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Recebe o alarme periódico do AlarmManager e reinicia o scan BLE.
 * Usa wake lock para garantir que o reinício completa antes do
 * processo ser suspenso novamente.
 */
class ReiniciaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BluGateway", "[alarme] ReiniciaReceiver action=${intent.action}")
        if (intent.action != "pt.blugateway.REINICIA_SCAN") return

        // Wake lock temporário: garante que o reinício do scan completa
        // antes do CPU adormecer novamente
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BluGateway:ReiniciaReceiver"
        )
        wl.acquire(10_000L)  // máximo 10s, liberta automaticamente
        try {
            GestorScan.reiniciaSeNecessario(context)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }
}
