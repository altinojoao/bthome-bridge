package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

/* Ecra proprio de Configuracao, como no HTML (ecraConfigDidatica) --
   ocupa quase toda a largura do ecra, com titulo fixo e "x" no topo,
   e o conteudo real (Conta, Perfis, Perfil ativo, Combinacoes) a
   rolar por baixo. Substitui o antigo comportamento de expandir
   inline dentro da coluna principal. */
@Composable
fun DialogoConfiguracao(
    onFecha: () -> Unit,
    conteudo: @Composable () -> Unit
) {
    val cores = LocalCoresGateway.current
    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.sec_config),
                    color = cores.tinta,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFecha) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.fechar),
                        tint = cores.suave
                    )
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                conteudo()
            }
        }
    )
}
