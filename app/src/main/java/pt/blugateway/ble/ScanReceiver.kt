package pt.blugateway.ble

import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.runBlocking
import pt.blugateway.data.Repositorio

class ScanReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val errorCode = intent.getIntExtra(android.bluetooth.le.BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (errorCode != -1) {
            Log.w("ScanReceiver", "erro de scan: $errorCode")
            return
        }

        val resultados: ArrayList<ScanResult> = intent.getParcelableArrayListExtra(
            android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
        ) ?: return

        if (resultados.isNotEmpty()) GestorScan.marcaAtividade()
        RegistoDiagnostico.regista(context, "onReceive: ${resultados.size} resultado(s)")

        // goAsync() + runBlocking: mantém o processo vivo E executa
        // todas as coroutines de forma síncrona antes de finish().
        // runBlocking bloqueia a thread do goAsync até tudo completar --
        // incluindo ExecutorAcoes, GestorTrajeto e GestorSemelhanca.
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking {
                    for (resultado in resultados) {
                        processaResultado(context, resultado)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private suspend fun processaResultado(context: Context, resultado: ScanResult) {
        val dispositivo = resultado.device ?: return
        val registo = resultado.scanRecord ?: return
        val mac = dispositivo.address ?: return

        val serviceDataOriginal = registo.getServiceData(
            android.os.ParcelUuid.fromString(BTHome.SERVICE_UUID_STR)
        ) ?: return

        val header = if (serviceDataOriginal.isNotEmpty()) serviceDataOriginal[0].toInt() and 0xFF else 0
        val encriptado = (header and 0x01) != 0

        val serviceData = if (encriptado) {
            val repo = Repositorio(context)
            val chaveHex = repo.acharComandoPorMac(mac)?.chaveEncriptacao ?: return
            val chaveBytes = hexParaBytes(chaveHex) ?: return
            val macBytes = macParaBytes(mac) ?: return
            BTHomeCripto.decifra(serviceDataOriginal, macBytes, chaveBytes) ?: return
        } else serviceDataOriginal

        val trama = BTHome.descodifica(serviceData) ?: return
        val nome = registo.deviceName ?: dispositivo.name ?: "BTHome"
        val rssi = resultado.rssi

        RegistoDiagnostico.regista(context, "processaResultado: mac=$mac rssi=$rssi evento=${trama.evento}")

        ProcessadorClique.processa(context, mac, nome, trama, serviceData, rssi)
    }

    private fun hexParaBytes(hex: String): ByteArray? {
        val limpo = hex.trim().replace(":", "").replace(" ", "")
        if (limpo.length != 32) return null
        return try {
            ByteArray(16) { i -> limpo.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) { null }
    }

    private fun macParaBytes(mac: String): ByteArray? {
        val partes = mac.split(":")
        if (partes.size != 6) return null
        return try {
            ByteArray(6) { i -> partes[i].toInt(16).toByte() }
        } catch (e: NumberFormatException) { null }
    }
}
