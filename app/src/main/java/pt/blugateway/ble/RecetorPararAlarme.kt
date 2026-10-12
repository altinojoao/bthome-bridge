package pt.blugateway.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recebe o intent do botão "Parar" na notificação do alarme
 * de encontrar o telemóvel e pára o alarme.
 */
class RecetorPararAlarme : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "pt.blugateway.PARAR_ALARME_TELEMOVEL") {
            GestorEncontrarTelemovel.para(context)
        }
    }
}
