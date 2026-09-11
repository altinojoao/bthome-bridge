package pt.blugateway.ble

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.util.Log

/**
 * Liga/desliga o scan BLE em modo PendingIntent.
 *
 * Ao contrário de um ScanCallback normal, este scan sobrevive ao
 * fecho da app: o sistema entrega os resultados ao ScanReceiver
 * mesmo que o processo já não exista, recriando-o só para esse fim.
 * É esta característica que faz a app funcionar com o ecrã apagado.
 *
 * A escuta arranca automaticamente ao abrir a app (chamamos
 * iniciaEscuta no arranque, tal como a versão web chama
 * iniciaEscuta() no fim do script) e nunca precisa de ser desligada
 * manualmente — só o MODO DE EMPARELHAMENTO (aceitar candidatos
 * novos) é que o botão de procura liga/desliga.
 */
object GestorScan {

    private const val TAG = "GestorScan"
    @Volatile private var scanAtivo = false

    // Scan com PendingIntent: o Android mantém o registo mesmo que
    // o processo seja morto. Não é necessário reiniciar por inactividade
    // de anúncios -- o beacon pode estar simplesmente longe.
    // Só reiniciamos se o adaptador BLE reportar uma falha explícita
    // (SCAN_FAILED_*) ou se o Bluetooth for desligado e religado.
    private var contextoVigiado: Context? = null

    /** Guarda o contexto para uso interno (iniciaEscuta/paraEscuta). */
    fun iniciaVigilante(context: Context) {
        contextoVigiado = context.applicationContext
    }

    /** Chamado pelo ScanReceiver -- mantido por compatibilidade, não faz nada. */
    fun marcaAtividade() {}

    fun suportaBLE(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.bluetoothLeScanner != null
    }

    fun bluetoothLigado(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.isEnabled
    }

    /** Arranca a escuta em fundo. Idempotente — chamar várias vezes não duplica o registo. */
    fun iniciaEscuta(context: Context) {
        if (scanAtivo) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        try {
            // IMPORTANTE: ao contrário da versão web (onde filtros de
            // serviço de 16-bit não disparavam advertisementreceived de
            // forma fiável no Chrome), aqui usamos um ScanFilter real
            // pelo serviço BTHome. Isto não é só uma otimização: um scan
            // SEM filtros é parado automaticamente pelo sistema quando o
            // ecrã se apaga, e só retoma quando o ecrã volta a ligar --
            // o que quebraria exatamente o requisito de funcionar com o
            // ecrã apagado. Com um filtro ativo, o Android mantém o scan
            // vivo mesmo de ecrã apagado.
            //
            // SCAN_MODE_LOW_LATENCY em vez de LOW_POWER, e SEM
            // reportDelay: o utilizador quer resposta rápida sempre,
            // não só em primeiro plano. LOW_POWER escuta em ciclos
            // curtos (~0.5s) com longas pausas de rádio desligado
            // (vários segundos) entre eles -- um clique que aconteça
            // durante a pausa só é apanhado no ciclo seguinte, o que
            // explica atrasos variáveis de vários segundos. reportDelay
            // agrupava resultados em lotes antes de os entregar, o que
            // também atrasava tudo mesmo com o rádio já a ouvir.
            // O compromisso: se algum fabricante suspender este scan em
            // segundo plano por não termos reportDelay, o vigilante
            // (iniciaVigilante) já deteta e reinicia -- é preferível a
            // ter sempre alguns segundos de atraso mesmo em primeiro
            // plano.
            val filtro = ScanFilter.Builder()
                .setServiceData(
                    ParcelUuid.fromString(BTHome.SERVICE_UUID_STR),
                    byteArrayOf(), byteArrayOf() // mascara vazia: aceita qualquer conteudo do serviço, só filtra pelo UUID
                )
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    }
                }
                .build()

            scanner.startScan(listOf(filtro), settings, pendingIntent(context))
            scanAtivo = true
        } catch (e: SecurityException) {
            Log.w(TAG, "sem permissão de Bluetooth ao arrancar escuta", e)
        } catch (e: Exception) {
            Log.w(TAG, "falha ao arrancar escuta", e)
        }
    }

    fun paraEscuta(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(pendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "falha ao parar escuta", e)
        }
        scanAtivo = false
    }

    fun estaAtiva(): Boolean = scanAtivo

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScanReceiver::class.java).apply {
            action = "pt.blugateway.SCAN_RESULT"
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
