package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ble.LinhaRegisto
import pt.blugateway.ble.RegistoEventos
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.data.Comando
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Ecrã de resumo do estado actual da app: beacons e o seu sinal/
 * bateria, cenários de trajecto e a sua configuração, últimos
 * eventos registados. Pensado para o utilizador final ver tudo
 * num relance sem ter de abrir vários ecrãs -- não introduz
 * nenhuma lógica nova, só agrega dados já existentes (Comando,
 * CenarioTrajeto, RegistoEventos).
 */
@Composable
fun EcraDashboard(
    comandos: List<Comando>,
    cenarios: List<CenarioTrajeto>,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    val linhasRegisto by RegistoEventos.linhas.collectAsState()

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(16.dp),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dashboard_titulo),
                        color = cores.tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

                    SeccaoDashboard(titulo = stringResource(R.string.dashboard_seccao_beacons)) {
                        if (comandos.isEmpty()) {
                            TextoVazioDashboard(stringResource(R.string.dashboard_sem_beacons))
                        } else {
                            comandos.forEach { c -> LinhaBeaconDashboard(c) }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    SeccaoDashboard(titulo = stringResource(R.string.dashboard_seccao_cenarios)) {
                        if (cenarios.isEmpty()) {
                            TextoVazioDashboard(stringResource(R.string.dashboard_sem_cenarios))
                        } else {
                            cenarios.forEach { c -> LinhaCenarioDashboard(c) }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    SeccaoDashboard(titulo = stringResource(R.string.dashboard_seccao_atividade)) {
                        if (linhasRegisto.isEmpty()) {
                            TextoVazioDashboard(stringResource(R.string.dashboard_sem_atividade))
                        } else {
                            linhasRegisto.take(10).forEach { linha -> LinhaAtividadeDashboard(linha) }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SeccaoDashboard(titulo: String, conteudo: @Composable () -> Unit) {
    val cores = LocalCoresGateway.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cores.elevado)
            .padding(12.dp)
    ) {
        Text(titulo, color = cores.tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        conteudo()
    }
}

@Composable
private fun TextoVazioDashboard(texto: String) {
    val cores = LocalCoresGateway.current
    Text(texto, color = cores.suave, fontSize = 11.sp)
}

@Composable
private fun LinhaBeaconDashboard(comando: Comando) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(comando.nome, color = cores.tinta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(comando.mac, color = cores.suave, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            val corSinal = when {
                comando.foraDeAlcance -> cores.avisoTinta
                (comando.rssi ?: -999) >= -75 -> cores.ok
                else -> cores.suave
            }
            Text(
                if (comando.foraDeAlcance) stringResource(R.string.dashboard_fora_de_alcance)
                else "${comando.rssi ?: "--"} dBm",
                color = corSinal, fontSize = 11.sp
            )
            Text(
                "\uD83D\uDD0B ${comando.bateria?.let { "$it%" } ?: "--"}" +
                    if (comando.modoBeaconTrajeto) "  \uD83D\uDCCD" else "",
                color = cores.suave, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun LinhaCenarioDashboard(cenario: CenarioTrajeto) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(cenario.nome, color = cores.tinta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            val detalhe = buildString {
                append(stringResource(R.string.dashboard_limiar_em, cenario.limiarPercentagem))
                if (cenario.janelasHorarias.isNotEmpty()) {
                    append(" • ")
                    append(stringResource(R.string.dashboard_com_horario))
                }
            }
            Text(detalhe, color = cores.suave, fontSize = 10.sp)
        }
        Text(
            if (cenario.ativo) stringResource(R.string.dashboard_ativo) else stringResource(R.string.dashboard_inativo),
            color = if (cenario.ativo) cores.ok else cores.suave,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LinhaAtividadeDashboard(linha: LinhaRegisto) {
    val cores = LocalCoresGateway.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(linha.hora, color = cores.suave, fontSize = 10.sp, modifier = Modifier.width(52.dp))
        Text(
            linha.texto,
            color = if (linha.ok) cores.tinta else cores.avisoTinta,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
