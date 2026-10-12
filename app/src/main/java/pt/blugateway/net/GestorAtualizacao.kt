package pt.blugateway.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pt.blugateway.BuildConfig
import java.io.File

/**
 * Resultado de uma verificacao de atualizacao -- ou ha uma versao
 * nova disponivel (com o URL direto do APK a descarregar), ou a
 * app ja esta na versao mais recente, ou algo correu mal (rede,
 * release sem APK anexado, resposta inesperada da API).
 */
sealed class ResultadoVerificacaoAtualizacao {
    data class Disponivel(val versao: String, val urlApk: String) : ResultadoVerificacaoAtualizacao()
    object JaAtualizado : ResultadoVerificacaoAtualizacao()
    data class Erro(val mensagem: String) : ResultadoVerificacaoAtualizacao()
}

/**
 * Verifica a ultima release publicada no GitHub, descarrega o APK
 * anexado e abre o instalador do sistema -- tudo disparado so por
 * toque explicito num botao "Verificar atualizacoes" (ver
 * CartaoAtualizacao.kt), nunca automaticamente em background. Nao
 * pede confirmacao extra antes de instalar: o proprio instalador do
 * Android e' quem pede essa confirmacao ao utilizador.
 *
 * O repositorio e' fixo por agora (bthome-bridge, branch main) --
 * como os dois repos (blugateway/bthome-bridge) sao mantidos em
 * sincronia byte-a-byte e a mesma tag e' criada nos dois, e
 * indiferente qual dos dois serve de fonte para o APK.
 */
object GestorAtualizacao {

    private const val REPO_OWNER = "altinojoao"
    private const val REPO_NOME = "bthome-bridge"
    private const val URL_ULTIMA_RELEASE =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NOME/releases/latest"

    private val cliente = OkHttpClient.Builder().build()

    /**
     * Compara duas versoes no formato "v1.0.24" ou "1.0.24" --
     * remove um eventual "v" inicial e compara cada segmento
     * numericamente (nao como string, para "1.0.9" < "1.0.10" dar
     * o resultado correto). Segmentos em falta contam como 0.
     */
    fun versaoMaisRecente(remota: String, local: String): Boolean {
        fun normaliza(v: String) = v.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val r = normaliza(remota)
        val l = normaliza(local)
        val tamanho = maxOf(r.size, l.size)
        for (i in 0 until tamanho) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Consulta a API do GitHub (endpoint publico, sem autenticacao
     * necessaria para releases publicas) e devolve se ha uma versao
     * mais recente que BuildConfig.VERSION_NAME, com o URL direto
     * do primeiro asset .apk anexado a essa release.
     */
    suspend fun verificaAtualizacao(): ResultadoVerificacaoAtualizacao = withContext(Dispatchers.IO) {
        try {
            val pedido = Request.Builder().url(URL_ULTIMA_RELEASE).build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    return@withContext ResultadoVerificacaoAtualizacao.Erro(
                        "GitHub respondeu ${resposta.code}"
                    )
                }
                val corpo = resposta.body?.string()
                    ?: return@withContext ResultadoVerificacaoAtualizacao.Erro("Resposta vazia")

                val json = JSONObject(corpo)
                val tagName = json.optString("tag_name", "")
                if (tagName.isBlank()) {
                    return@withContext ResultadoVerificacaoAtualizacao.Erro("Release sem tag_name")
                }

                if (!versaoMaisRecente(tagName, BuildConfig.VERSION_NAME)) {
                    return@withContext ResultadoVerificacaoAtualizacao.JaAtualizado
                }

                val assets = json.optJSONArray("assets")
                var urlApk: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val nome = asset.optString("name", "")
                        if (nome.endsWith(".apk", ignoreCase = true)) {
                            urlApk = asset.optString("browser_download_url", "").ifBlank { null }
                            break
                        }
                    }
                }

                if (urlApk == null) {
                    return@withContext ResultadoVerificacaoAtualizacao.Erro(
                        "Release $tagName não tem nenhum APK anexado"
                    )
                }

                ResultadoVerificacaoAtualizacao.Disponivel(versao = tagName, urlApk = urlApk)
            }
        } catch (e: Exception) {
            ResultadoVerificacaoAtualizacao.Erro(e.message ?: "Erro de rede desconhecido")
        }
    }

    /**
     * Descarrega o APK do URL indicado para o cache privado da app
     * (pasta exposta pelo FileProvider, ver file_paths.xml), e devolve
     * o ficheiro descarregado -- ou null se algo falhar. Sobrescreve
     * qualquer download anterior com o mesmo nome, para nao acumular
     * versoes antigas no cache.
     */
    suspend fun descarregaApk(context: Context, urlApk: String): File? = withContext(Dispatchers.IO) {
        try {
            val pedido = Request.Builder().url(urlApk).build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) return@withContext null
                val corpo = resposta.body ?: return@withContext null

                val pasta = File(context.cacheDir, "atualizacoes").apply { mkdirs() }
                val ficheiro = File(pasta, "atualizacao.apk")
                ficheiro.outputStream().use { saida ->
                    corpo.byteStream().copyTo(saida)
                }
                ficheiro
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Abre o instalador do sistema para o APK ja descarregado --
     * ACTION_VIEW com o Uri do FileProvider (content://), tal como
     * exigido a partir do Android 7 para partilhar um ficheiro entre
     * apps. Nao pede nenhuma confirmacao propria antes disto: o
     * instalador do Android e' quem trata da confirmacao final.
     */
    fun instalaApk(context: Context, ficheiro: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            ficheiro
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
