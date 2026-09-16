package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Guia de configuração para o utilizador final -- um HTML estático
 * (assets/ajuda/guia_configuracao.html), sem JavaScript nem
 * comunicação com o Kotlin. Mesma estrutura de Dialog+WebView já
 * usada em DialogoSimulacaoCenario, mas muito mais simples: não há
 * estado para sincronizar, só uma página a carregar uma vez.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DialogoAjuda(onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(16.dp),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.guia_titulo),
                        color = cores.tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            // Sem javaScriptEnabled: a página de ajuda é
                            // texto estático puro, não precisa de JS.
                            loadUrl("file:///android_asset/ajuda/guia_configuracao.html")
                        }
                    }
                )
            }
        }
    }
}
