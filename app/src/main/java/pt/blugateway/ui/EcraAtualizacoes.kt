package pt.blugateway.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Ecra proprio para verificar e instalar atualizacoes, acessivel a
 * partir de um botao dedicado na barra de topo (ver BarraTopo) --
 * tal como Mapa, Cenarios e Perfis, usa Dialog() fullscreen. O card
 * equivalente dentro de Configuracao (CartaoAtualizacao) continua a
 * existir tambem, por pedido explicito -- os dois pontos de acesso
 * partilham a mesma logica de estados atraves de ConteudoAtualizacao,
 * para nunca poderem divergir em comportamento.
 */
@Composable
fun EcraAtualizacoes(onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.atualizacao_titulo),
                        color = cores.tinta,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                ConteudoAtualizacao()
            }
        }
    }
}
