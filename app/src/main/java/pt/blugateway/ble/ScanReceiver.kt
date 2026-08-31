package pt.blugateway.ble

import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import pt.blugateway.data.Repositorio

/**
 * Recebe anúncios BLE via PendingIntent-based scanning.
 *
 * Esta é a peça central que faz a app funcionar com o ecrã apagado
 * e depois de fechada: em vez de um ScanCallback normal (que morre
 * quando o processo é morto pelo Android), registamos um PendingIntent
 * junto do BluetoothLeScanner. O sistema entrega os anúncios a este
 * receiver mesmo que o processo da app já não exista — o Android
 * recria-o só para processar o broadcast, chama onReceive(), e o
 * processo pode voltar a ser fechado a seguir. Por isso todo o
 * trabalho aqui tem de ser síncrono e rápido: ler, decidir, agir,
 * gravar em disco (nunca em memória que não sobrevive).
 */
class ScanReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val errorCode = intent.getIntExtra(android.bluetooth.le.BluetoothLeScanner.EXTRA_ERROR_CODE, -1)

        if (errorCode != -1) {
            Log.w("ScanReceiver", "erro de scan reportado pelo sistema: $errorCode")
            return
        }

        // getParcelableArrayListExtra(String) sem a classe (API 33+) foi
        // descontinuado a favor da versao com Class<T>, mas essa exige
        // minSdk 33 -- como o minSdk do projeto e 26, usamos a forma antiga
        // e silenciamos o aviso de depreciacao aqui, no unico sitio que a usa.
        val resultados: ArrayList<ScanResult> = intent.getParcelableArrayListExtra(
            android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
        ) ?: return

        if (resultados.isNotEmpty()) GestorScan.marcaAtividade()

        for (resultado in resultados) {
            processaResultado(context, resultado)
        }
    }

    private fun processaResultado(context: Context, resultado: ScanResult) {
        val dispositivo = resultado.device ?: return
        val registo = resultado.scanRecord ?: return
        val mac = dispositivo.address ?: return

        val serviceDataOriginal = registo.getServiceData(
            android.os.ParcelUuid.fromString(BTHome.SERVICE_UUID_STR)
        ) ?: return

        // Se a trama vier encriptada (bit 0 do 1º byte) e já tivermos
        // uma chave guardada para este comando, tenta decifrar antes
        // de descodificar. Sem chave guardada, ou se a decifra falhar
        // (chave errada, dados corrompidos), a trama é descartada em
        // silêncio -- tal como já acontecia antes desta funcionalidade
        // existir, para não confundir o utilizador com tramas ilegíveis.
        val header = if (serviceDataOriginal.isNotEmpty()) serviceDataOriginal[0].toInt() and 0xFF else 0
        val encriptado = (header and 0x01) != 0

        val serviceData = if (encriptado) {
            val repo = Repositorio(context)
            val chaveHex = repo.acharComandoPorMac(mac)?.chaveEncriptacao ?: return
            val chaveBytes = hexParaBytes(chaveHex) ?: return
            val macBytes = macParaBytes(mac) ?: return
            BTHomeCripto.decifra(serviceDataOriginal, macBytes, chaveBytes) ?: return
        } else {
            serviceDataOriginal
        }

        val trama = BTHome.descodifica(serviceData) ?: return
        val nome = registo.deviceName ?: dispositivo.name ?: "BTHome"
        val rssi = resultado.rssi

        ProcessadorClique.processa(
            context = context,
            mac = mac,
            nome = nome,
            trama = trama,
            bytesOriginais = serviceData,
            rssi = rssi
        )
    }

    private fun hexParaBytes(hex: String): ByteArray? {
        val limpo = hex.trim().replace(":", "").replace(" ", "")
        if (limpo.length != 32) return null // 16 bytes = 32 caracteres hex
        return try {
            ByteArray(16) { i -> limpo.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Converte "AA:BB:CC:DD:EE:FF" nos 6 bytes na ordem que o BLE usa
     *  para o endereço no anúncio -- é a mesma ordem em que o
     *  BluetoothDevice.getAddress() já devolve a string, só sem os
     *  dois pontos. */
    private fun macParaBytes(mac: String): ByteArray? {
        val partes = mac.split(":")
        if (partes.size != 6) return null
        return try {
            ByteArray(6) { i -> partes[i].toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
