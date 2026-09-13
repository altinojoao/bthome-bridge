package pt.blugateway.ble

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import pt.blugateway.data.Repositorio

/**
 * Gere o scan BLE via PendingIntent (sobrevive ao processo ser morto)
 * e um AlarmManager que reinicia o scan periodicamente com o ecrã
 * bloqueado -- o Handler da main thread não dispara quando o processo
 * está suspenso, mas o AlarmManager acorda o processo garantidamente.
 */
object GestorScan {

    private const val TAG = "GestorScan"
    private const val INTERVALO_ALARME_MS = 45_000L  // reiniciar scan de 45 em 45s
    private const val ACTION_REINICIA_SCAN = "pt.blugateway.REINICIA_SCAN"

    @Volatile private var scanAtivo = false
    @Volatile private var contextoVigiado: Context? = null

    fun marcaAtividade() { /* mantido por compatibilidade */ }

    fun iniciaVigilante(context: Context) {
        contextoVigiado = context.applicationContext
        agendaProximoAlarme(context.applicationContext)
    }

    /** Chamado pelo ReiniciaReceiver via AlarmManager. */
    fun reiniciaSeNecessario(context: Context) {
        val repo = Repositorio(context)
        if (repo.comandos.value.isEmpty()) {
            agendaProximoAlarme(context)
            return
        }
        Log.d(TAG, "[scan] AlarmManager: a reiniciar scan BLE")
        RegistoEventos.adicionaResultado(
            context.getString(pt.blugateway.R.string.scan_reiniciado), false, ""
        )
        paraEscuta(context)
        iniciaEscuta(context)
        agendaProximoAlarme(context)
    }

    private fun agendaProximoAlarme(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReiniciaReceiver::class.java)
            .setAction(ACTION_REINICIA_SCAN)
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // SET_AND_ALLOW_WHILE_IDLE acorda o processo mesmo em Doze mode
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVALO_ALARME_MS,
            pi
        )
    }

    fun suportaBLE(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.bluetoothLeScanner != null
    }

    fun bluetoothLigado(): Boolean {
        return BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    }

    fun estaAtivo(): Boolean = scanAtivo

    @Synchronized
    fun iniciaEscuta(context: Context) {
        if (scanAtivo) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        val filtro = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(BTHome.SERVICE_UUID_STR))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(filtro), settings, pendingIntent(context))
            scanAtivo = true
            Log.d(TAG, "[scan] scan iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "[scan] erro ao iniciar scan: ${e.message}")
        }
    }

    @Synchronized
    fun paraEscuta(context: Context) {
        if (!scanAtivo) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(pendingIntent(context))
        } catch (e: Exception) { /* ignorar */ }
        scanAtivo = false
        Log.d(TAG, "[scan] scan parado")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScanReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
