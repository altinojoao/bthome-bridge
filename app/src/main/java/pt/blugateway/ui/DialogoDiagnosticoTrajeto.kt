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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ble.RegistoDiagnostico
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Ecra temporario para diagnosticar o fluxo de trajeto por beacon em
 * segundo plano -- mostra, em ordem cronologica, cada anuncio
 * Bluetooth recebido e o resultado de cada tentativa de obter
 * localizacao (sucesso, timeout, sem permissao, etc). So para
 * investigacao; remover depois de encontrada a causa definitiva do
 * comportamento visto em certos fabricantes (ex: OnePlus).
 */
@Composable
fun DialogoDiagnosticoTrajeto(onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    val contexto = LocalContext.current
    val linhas = RegistoDiagnostico.leTudo(contexto)

    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.diagnostico_trajeto_titulo),
                    color = cores.tinta,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFecha) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        val cm = contexto.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnostico", linhas.joinToString("\n")))
                    }) {
                        Text(stringResource(R.string.copiar_tudo), color = cores.azul, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        RegistoDiagnostico.limpa(contexto)
                        onFecha()
                    }) {
                        Text(stringResource(R.string.limpar), color = cores.avisoTinta, fontSize = 11.sp)
                    }
                }

                Text(
                    stringResource(R.string.diagnostico_trajeto_legenda),
                    color = cores.suave,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (linhas.isEmpty()) {
                    Text(
                        stringResource(R.string.diagnostico_trajeto_sem_registos),
                        color = cores.suave,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .background(cores.elevado)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // mais recente primeiro, mais facil de ler sem
                        // ter de rolar ate ao fim de cada vez
                        linhas.asReversed().forEach { linha ->
                            Text(
                                linha,
                                color = cores.tinta,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}
