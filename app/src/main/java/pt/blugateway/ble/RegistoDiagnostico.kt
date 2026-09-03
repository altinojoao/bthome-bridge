package pt.blugateway.ble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registo de diagnostico do fluxo de trajeto por beacon em segundo
 * plano -- temporario, so para investigar porque alguns fabricantes
 * (ex: OnePlus) parecem perder pontos mesmo com todas as permissoes
 * e otimizacoes de bateria ja corretas.
 *
 * Grava cada evento relevante com timestamp em SharedPreferences
 * (sobrevive ao processo morrer, ao contrario de uma lista em
 * memoria), para o utilizador poder ver o historico completo mesmo
 * depois de reabrir a app. Mostra ate MAX_ENTRADAS mais recentes,
 * como uma fila circular simples.
 */
object RegistoDiagnostico {

    private const val PREFS = "blugateway_diagnostico_trajeto"
    private const val CHAVE_ENTRADAS = "entradas"
    private const val MAX_ENTRADAS = 200

    private val formato = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun regista(context: Context, evento: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(CHAVE_ENTRADAS, null)
            val arr = if (raw != null) JSONArray(raw) else JSONArray()

            val linha = "${formato.format(Date())}  $evento"
            arr.put(linha)

            // fila circular: descarta as mais antigas quando excede o limite
            val novoArr = if (arr.length() > MAX_ENTRADAS) {
                val recortado = JSONArray()
                for (i in (arr.length() - MAX_ENTRADAS) until arr.length()) {
                    recortado.put(arr.getString(i))
                }
                recortado
            } else arr

            prefs.edit().putString(CHAVE_ENTRADAS, novoArr.toString()).apply()
        } catch (e: Exception) {
            // o diagnostico nunca deve interromper o fluxo real da app
        }
    }

    fun leTudo(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(CHAVE_ENTRADAS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun limpa(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
