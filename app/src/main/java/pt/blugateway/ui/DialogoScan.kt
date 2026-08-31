package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ble.Candidato
import pt.blugateway.ui.theme.LocalCoresGateway

/* Ecra proprio de procura (ecraScanDidatico no HTML): mostra o
   estado da escuta, um botao para iniciar/parar, e a lista de
   candidatos encontrados em tempo real, cada um com um botao de
   adicionar -- em vez do botao silencioso da lupa que so abria/
   fechava o modo de emparelhamento sem mais feedback. */
@Composable
fun DialogoScan(
    emparelhamentoAberto: Boolean,
    candidatos: List<Candidato>,
    onDetetar: () -> Unit,
    onAssocia: (String, String) -> Unit,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.7f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.candidatos),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (emparelhamentoAberto) cores.ok else cores.suave)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (emparelhamentoAberto) R.string.a_pesquisar else R.string.sem_escuta),
                        color = cores.tinta,
                        fontSize = 12.5.sp
                    )
                }

                Button(
                    onClick = onDetetar,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (emparelhamentoAberto) R.string.tt_parar else R.string.tt_detetar))
                }

                if (candidatos.isEmpty()) {
                    Text(
                        stringResource(R.string.bt_nenhum),
                        color = cores.suave,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                } else {
                    LazyColumn(Modifier.padding(top = 10.dp)) {
                        items(candidatos, key = { it.mac }) { candidato ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(cores.elevado)
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidato.nome, color = cores.tinta, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                    val detalhe = buildString {
                                        append(candidato.mac)
                                        candidato.rssi?.let { append("  ·  \uD83D\uDCF6 $it dBm") }
                                        candidato.bateria?.let { append("  ·  \uD83D\uDD0B $it%") }
                                    }
                                    Text(detalhe, color = cores.suave, fontSize = 10.5.sp)
                                }
                                Button(onClick = { onAssocia(candidato.mac, candidato.nome) }) {
                                    Text(stringResource(R.string.adicionar_cmd))
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
