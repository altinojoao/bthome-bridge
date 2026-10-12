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

    // Segunda linha de defesa contra o Bluetooth parar de responder
    // em silencio -- o mesmo problema ja diagnosticado e corrigido na
    // versao web (ver GestorScan da app: aqui ja usamos ScanFilter em
    // vez de scan sem filtro, o que evita a causa mais comum, mas o
    // radio pode falhar por outras razoes). Se houver comandos
    // associados e nenhum anuncio BLE chegar durante muito tempo,
    // presumimos que o scan morreu e reiniciamo-lo.
    @Volatile private var ultimaAtividade = System.currentTimeMillis()
    private var handlerVigilante: android.os.Handler? = null
    private val vigilanteRunnable = object : Runnable {
        override fun run() {
            val inatividade = System.currentTimeMillis() - ultimaAtividade
            if (inatividade > TEMPO_INATIVIDADE_MS && contextoVigiado != null) {
                val repo = pt.blugateway.data.Repositorio(contextoVigiado!!)
                if (repo.comandos.value.isNotEmpty()) {
                    Log.w(TAG, "sem atividade de scan há ${inatividade}ms, a reiniciar")
                    RegistoEventos.adicionaResultado(
                        contextoVigiado!!.getString(pt.blugateway.R.string.scan_reiniciado), false, ""
                    )
                    reinicia(contextoVigiado!!, "sem anuncios ha ${inatividade / 1000}s")
                }
            }
            contextoVigiado?.let { ctx ->
                if (!renovaSeNecessario(ctx)) verificaBeaconsSilenciosos(ctx)
            }
            handlerVigilante?.postDelayed(this, INTERVALO_VERIFICACAO_MS)
        }
    }
    @Volatile private var contextoVigiado: Context? = null

    private const val TEMPO_INATIVIDADE_MS = 60_000L   // 60s sem anúncios → reiniciar scan
    private const val INTERVALO_VERIFICACAO_MS = 30_000L // verificar de 30 em 30s

    /** Chamado pelo ScanReceiver sempre que qualquer anúncio BLE
     *  chega — sinal de que o scan continua vivo. */
    fun marcaAtividade(context: Context? = null) {
        ultimaAtividade = System.currentTimeMillis()
        context?.let {
            agendaAlarmeReforco(it.applicationContext)
            renovaSeNecessario(it.applicationContext)
        }
    }

    /** AlarmManager como reforço ao Handler: o Handler da main thread
     *  não dispara de forma fiável quando o processo está suspenso
     *  com o ecrã bloqueado. O AlarmManager acorda o processo mesmo
     *  nesse caso. Reagendado a cada anúncio recebido -- só dispara
     *  se não chegar nenhum anúncio durante TEMPO_INATIVIDADE_MS. */
    private fun agendaAlarmeReforco(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ReiniciaReceiver::class.java)
            .setAction("pt.blugateway.REINICIA_SCAN")
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + TEMPO_INATIVIDADE_MS,
                    pi
                )
            } else {
                am.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + TEMPO_INATIVIDADE_MS,
                    pi
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "sem permissão para alarme exacto", e)
        }
    }

    fun iniciaVigilante(context: Context) {
        contextoVigiado = context.applicationContext
        if (handlerVigilante != null) return
        handlerVigilante = android.os.Handler(android.os.Looper.getMainLooper())
        handlerVigilante?.postDelayed(vigilanteRunnable, INTERVALO_VERIFICACAO_MS)
        agendaAlarmeReforco(context.applicationContext)
    }

    /** Chamado pelo ReiniciaReceiver via AlarmManager -- mesmo reinício
     *  que o Handler faria, mas acordado mesmo com o processo suspenso. */
    fun reiniciaSeNecessario(context: Context) {
        val repo = pt.blugateway.data.Repositorio(context)
        if (repo.comandos.value.isNotEmpty()) {
            Log.w(TAG, "[alarme] a reiniciar scan BLE")
            RegistoEventos.adicionaResultado(
                context.getString(pt.blugateway.R.string.scan_reiniciado), false, ""
            )
            reinicia(context, "alarme de inatividade")
        }
    }

    @Synchronized
    private fun reinicia(context: Context, motivo: String) {
        RegistoDiagnostico.regista(context, "[scan] a reiniciar o scan BLE: $motivo")
        paraEscuta(context)
        ultimaAtividade = System.currentTimeMillis()
        iniciaEscuta(context)
    }

    // ---- Renovacao preventiva e vigilancia por beacon ----
    //
    // O Android converte scans BLE de longa duracao (cerca de 30 min)
    // em scans "oportunistas": a app so recebe anuncios quando OUTRA
    // app estiver a fazer scan -- continuam a chegar alguns anuncios
    // soltos, por isso o vigilante classico (60s sem QUALQUER anuncio)
    // nunca dispara, e os cliques perdem-se ate a app ser reiniciada.
    // Para evitar isto o scan e' renovado de forma preventiva, bem
    // antes desse limite, e tambem quando um beacon conhecido deixa de
    // ser ouvido mesmo havendo anuncios de outros dispositivos.

    private const val PREFS_SCAN = "blugateway_scan"
    private const val CHAVE_ULTIMO_INICIO = "ultimo_inicio_scan"
    private const val RENOVACAO_MS = 20 * 60_000L
    private const val SILENCIO_BEACON_MS = 120_000L
    private const val SILENCIO_BEACON_MAX_MS = 15 * 60_000L

    private val ultimoAnuncioPorMac = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val reiniciadoPorSilencioEm = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Chamado a cada anuncio de um beacon associado (ProcessadorClique). */
    fun registaAnuncioDe(mac: String) {
        ultimoAnuncioPorMac[mac] = System.currentTimeMillis()
    }

    private fun guardaInicioDoScan(context: Context) {
        try {
            context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)
                .edit().putLong(CHAVE_ULTIMO_INICIO, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "falha ao guardar inicio do scan", e)
        }
    }

    private fun idadeDoScanMs(context: Context): Long {
        val t = try {
            context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE).getLong(CHAVE_ULTIMO_INICIO, 0L)
        } catch (e: Exception) { 0L }
        return if (t == 0L) Long.MAX_VALUE else System.currentTimeMillis() - t
    }

    /** Renova o scan se o ultimo arranque foi ha mais de RENOVACAO_MS.
     *  Devolve true se reiniciou. */
    fun renovaSeNecessario(context: Context): Boolean {
        val ctx = context.applicationContext
        val idade = idadeDoScanMs(ctx)
        if (idade < RENOVACAO_MS) return false
        if (!bluetoothLigado()) return false
        if (pt.blugateway.data.Repositorio(ctx).comandos.value.isEmpty()) return false
        val minutos = if (idade == Long.MAX_VALUE) -1L else idade / 60_000L
        reinicia(ctx, "renovacao preventiva (scan com $minutos min)")
        return true
    }

    /** Reinicia o scan (uma vez por perda) quando um beacon que ate ha
     *  pouco era ouvido deixa de aparecer ao seu ritmo normal. Nao
     *  repete para o mesmo silencio, para nao reiniciar em ciclo se o
     *  beacon simplesmente ficou longe. */
    private fun verificaBeaconsSilenciosos(context: Context) {
        val agora = System.currentTimeMillis()
        for ((mac, ultimo) in ultimoAnuncioPorMac) {
            val silencio = agora - ultimo
            if (silencio < SILENCIO_BEACON_MS || silencio > SILENCIO_BEACON_MAX_MS) continue
            if (reiniciadoPorSilencioEm[mac] == ultimo) continue
            reiniciadoPorSilencioEm[mac] = ultimo
            reinicia(context.applicationContext, "beacon $mac sem anuncios ha ${silencio / 1000}s")
            return
        }
    }

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
            guardaInicioDoScan(context)
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
