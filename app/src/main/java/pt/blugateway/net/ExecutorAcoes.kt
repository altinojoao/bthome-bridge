package pt.blugateway.net

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import pt.blugateway.R
import pt.blugateway.ble.RegistoEventos
import pt.blugateway.data.Comando
import pt.blugateway.data.Metodo
import pt.blugateway.data.Repositorio
import pt.blugateway.data.TipoAcao
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Executa as ações de um perfil quando um clique é reconhecido.
 * Porta direta de acoes.js, com uma diferença importante: aqui temos
 * acesso ao código HTTP real da resposta (a versão web usa
 * mode:"no-cors" e só sabe dizer "enviado" ou "falhou"; a app nativa
 * não tem essa limitação de CORS e mostra o código verdadeiro).
 */
object ExecutorAcoes {

    private val cliente = OkHttpClient.Builder().build()

    suspend fun executa(
        context: Context,
        comando: Comando,
        indiceEvento: Int,
        evento: String,
        codigo: Int,
        mac: String,
        bateria: Int?,
        rssi: Int?
    ) {
        val repo = Repositorio(context)
        val perfil = repo.acharPerfil(comando.perfilId) ?: return
        val acoes = perfil.eventos.getOrNull(indiceEvento) ?: return
        if (acoes.isEmpty()) return

        val ctx = ContextoClique(evento, codigo, mac, bateria, rssi, timestampIso())
        for (acao in acoes) executaAcao(context, acao, ctx)
    }

    /** Corre uma lista de ações arbitrária com o mesmo contexto de
     *  clique — usada pelas combinações, que não têm um único índice
     *  de evento nem dependem diretamente do perfil do comando. */
    suspend fun executaLista(
        context: Context,
        acoes: List<pt.blugateway.data.Acao>,
        evento: String,
        codigo: Int,
        mac: String,
        bateria: Int?,
        rssi: Int?
    ) {
        if (acoes.isEmpty()) return
        val ctx = ContextoClique(evento, codigo, mac, bateria, rssi, timestampIso())
        for (acao in acoes) executaAcao(context, acao, ctx)
    }

    private fun executaAcao(context: Context, acao: pt.blugateway.data.Acao, ctx: ContextoClique) {
        val valor = acao.valor.trim()
        if (valor.isEmpty()) return

        when (acao.tipo) {
            TipoAcao.CENARIO -> executaCenario(context, valor)
            TipoAcao.URL -> executaUrlLivre(context, valor, acao.metodo, ctx)
            TipoAcao.NTFY -> executaNtfy(context, valor, acao.metodo, acao.mensagem, ctx)
        }
    }

    data class ContextoClique(
        val evento: String, val codigo: Int, val mac: String,
        val bateria: Int?, val rssi: Int?, val timestamp: String
    )

    private fun timestampIso(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date())
    }

    private fun ctxParaJson(ctx: ContextoClique): String = JSONObject().apply {
        put("evento", ctx.evento)
        put("codigo", ctx.codigo)
        put("mac", ctx.mac)
        put("bateria", ctx.bateria ?: JSONObject.NULL)
        put("rssi", ctx.rssi ?: JSONObject.NULL)
        put("timestamp", ctx.timestamp)
    }.toString()

    // URLEncoder.encode() do Java codifica espaco como '+', nao '%20' --
    // diferente do encodeURIComponent() do JavaScript usado na versao web.
    // Esta funcao central garante que ambas produzem o mesmo resultado.
    private fun encURL(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun preencheUrl(txt: String, ctx: ContextoClique): String {
        return txt
            .replace("{evento}", encURL(ctx.evento))
            .replace("{codigo}", encURL(ctx.codigo.toString()))
            .replace("{mac}", encURL(ctx.mac))
            .replace("{bateria}", encURL(ctx.bateria?.toString() ?: ""))
            .replace("{rssi}", encURL(ctx.rssi?.toString() ?: ""))
            .replace("{timestamp}", encURL(ctx.timestamp))
    }

    private fun preencheTexto(txt: String, ctx: ContextoClique): String = txt
        .replace("{evento}", ctx.evento)
        .replace("{codigo}", ctx.codigo.toString())
        .replace("{mac}", ctx.mac)
        .replace("{bateria}", ctx.bateria?.toString() ?: "")
        .replace("{rssi}", ctx.rssi?.toString() ?: "")
        .replace("{timestamp}", ctx.timestamp)

    private fun executaCenario(context: Context, sceneId: String) {
        val repo = Repositorio(context)
        val conta = repo.conta.value
        val servidor = conta.servidorUrl()
        if (servidor == null || conta.authKey.isBlank()) {
            RegistoEventos.adicionaResultado(
                context.getString(R.string.cenario), false,
                context.getString(R.string.falta_conta)
            )
            return
        }
        val url = "$servidor/scene/manual_run?id=${encURL(sceneId)}" +
            "&auth_key=${encURL(conta.authKey)}"

        enviaGet(url, context.getString(R.string.cenario))
    }

    private fun executaUrlLivre(context: Context, valorBruto: String, metodo: Metodo, ctx: ContextoClique) {
        val url = preencheUrl(valorBruto, ctx)
        val etiqueta = context.getString(R.string.url)
        if (metodo == Metodo.POST) {
            val corpo = ctxParaJson(ctx)
            enviaPost(url, corpo, "application/json", etiqueta)
        } else {
            enviaGet(url, etiqueta)
        }
    }

    private fun executaNtfy(context: Context, valorBruto: String, metodo: Metodo, mensagemBruta: String, ctx: ContextoClique) {
        val topico = valorBruto.trim().removePrefix("https://ntfy.sh/").removePrefix("http://ntfy.sh/")
        if (topico.isEmpty()) return
        val alvo = "https://ntfy.sh/${encURL(topico)}"
        val etiqueta = context.getString(R.string.ntfy)
        val msg = mensagemBruta.trim()

        if (metodo == Metodo.POST) {
            val corpo = if (msg.isNotEmpty()) preencheTexto(msg, ctx) else ctxParaJson(ctx)
            enviaPost(alvo, corpo, "text/plain; charset=utf-8", etiqueta)
        } else {
            val texto = if (msg.isNotEmpty()) preencheTexto(msg, ctx) else ctx.evento
            enviaGet("$alvo/publish?message=${encURL(texto)}", etiqueta)
        }
    }

    private fun enviaGet(url: String, etiqueta: String) {
        val pedido = Request.Builder().url(url).get().build()
        despacha(pedido, etiqueta)
    }

    private fun enviaPost(url: String, corpo: String, mediaType: String, etiqueta: String) {
        val body = corpo.toRequestBody(mediaType.toMediaTypeOrNull())
        val pedido = Request.Builder().url(url).post(body).build()
        despacha(pedido, etiqueta)
    }

    private fun despacha(pedido: Request, etiqueta: String) {
        cliente.newCall(pedido).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                RegistoEventos.adicionaResultado(etiqueta, false, e.message ?: "erro de rede")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val codigo = response.code
                RegistoEventos.adicionaResultado(etiqueta, response.isSuccessful, "HTTP $codigo")
                response.close()
            }
        })
    }
}
