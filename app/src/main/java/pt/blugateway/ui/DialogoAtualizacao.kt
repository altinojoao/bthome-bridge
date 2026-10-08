package pt.blugateway.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Dialog simples que abre o mesmo CartaoAtualizacao() ja usado
 * dentro de Configuracao -- acessivel por um icone proprio na barra
 * de topo. Nao duplica nem partilha logica por extracao: apenas
 * invoca a funcao ja existente e testada, dentro de um invólucro
 * minimo (titulo + fechar), seguindo o mesmo padrao ja usado em
 * DialogoCardsVisiveis.
 */
@Composable
fun DialogoAtualizacao(onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        },
        text = {
            CartaoAtualizacao()
        }
    )
}
