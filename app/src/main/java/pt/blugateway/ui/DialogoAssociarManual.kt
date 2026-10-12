package pt.blugateway.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

/** Associar um comando sem passar pela procura BLE — necessário para
 *  botões que já têm "Segurança / conexão Bluetooth segura" ativada
 *  no firmware: nunca emitem um clique legível para a app os detetar
 *  como candidatos, por isso o MAC (e opcionalmente a chave, se já a
 *  tiver) têm de ser introduzidos à mão. */
@Composable
fun DialogoAssociarManual(onFecha: () -> Unit, onAssocia: (String, String, String?) -> Boolean) {
    val cores = LocalCoresGateway.current
    var mac by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var chave by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFecha,
        containerColor = cores.cartao,
        title = { Text(stringResource(R.string.associar_manual), color = cores.tinta, fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    stringResource(R.string.associar_manual_dica),
                    color = cores.suave, fontSize = 10.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                CampoTexto(
                    rotulo = stringResource(R.string.mac_dispositivo),
                    valor = mac,
                    placeholder = "AA:BB:CC:DD:EE:FF",
                    onValor = { mac = it; erro = false }
                )
                Box(Modifier.padding(top = 8.dp)) {
                    CampoTexto(
                        rotulo = stringResource(R.string.nome_comando),
                        valor = nome,
                        placeholder = "SBBT-002C",
                        onValor = { nome = it }
                    )
                }
                Box(Modifier.padding(top = 8.dp)) {
                    CampoTexto(
                        rotulo = stringResource(R.string.chave_encriptacao_opcional),
                        valor = chave,
                        placeholder = "0123456789abcdef0123456789abcdef",
                        onValor = { chave = it }
                    )
                }
                if (erro) {
                    Text(
                        stringResource(R.string.mac_invalido_ou_existente),
                        color = cores.avisoTinta, fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nomeFinal = nome.ifBlank { "BTHome" }
                val ok = onAssocia(mac.trim(), nomeFinal, chave.trim().ifBlank { null })
                if (ok) onFecha() else erro = true
            }) {
                Text(stringResource(R.string.guardar_combinacao), color = cores.azul)
            }
        },
        dismissButton = {
            TextButton(onClick = onFecha) {
                Text(stringResource(R.string.cancelar), color = cores.suave)
            }
        }
    )
}
