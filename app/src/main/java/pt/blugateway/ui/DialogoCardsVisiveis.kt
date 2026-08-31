package pt.blugateway.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
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

/* Ordem e nomes dos 5 blocos que podem ser ativados/desativados,
   replicando ORDEM_CARDS/MAPA_NOMES_CARDS do HTML. */
private val BLOCOS_CONFIGURAVEIS = listOf(
    "hero" to R.string.sec_escuta,
    "diag" to R.string.sec_diag,
    "comandos" to R.string.sec_comandos,
    "configPainel" to R.string.sec_config,
    "reg" to R.string.sec_registo
)

/* Ecra proprio "Blocos visiveis" (ecraCardsDidaticos no HTML): um
   interruptor por bloco, todos ativos por omissao. Fecha so ao tocar
   no "x" ou fora -- desligar um interruptor nao fecha o ecra, para
   o utilizador poder desligar varios seguidos. */
@Composable
fun DialogoCardsVisiveis(
    cardsDesativados: Set<String>,
    onAlternaCard: (String, Boolean) -> Unit,
    onFecha: () -> Unit
) {
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
                    stringResource(R.string.blocos_visiveis),
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
            Column(Modifier.fillMaxWidth()) {
                BLOCOS_CONFIGURAVEIS.forEach { (idBloco, nomeRes) ->
                    val ativo = idBloco !in cardsDesativados
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(nomeRes),
                            color = cores.tinta,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ativo,
                            onCheckedChange = { novo -> onAlternaCard(idBloco, novo) }
                        )
                    }
                }
            }
        }
    )
}
