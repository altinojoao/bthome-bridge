package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.ble.Diagnostico
import pt.blugateway.ui.theme.LocalCoresGateway

private val NOMES_EVENTO = intArrayOf(
    R.string.ev0, R.string.ev1, R.string.ev2, R.string.ev3,
    R.string.ev4, R.string.ev5, R.string.ev6
)

@Composable
fun CartaoDiagnostico(diagnostico: Diagnostico?, ajudaAtiva: Boolean, onAlternaAjuda: () -> Unit) {
    val cores = LocalCoresGateway.current

    Column(
        androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
    ) {
        Row(
            Modifier.padding(11.dp, 11.dp, 13.dp, 11.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("\uD83D\uDD2C", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            BotaoAjuda(ativo = ajudaAtiva, onClick = onAlternaAjuda)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.sec_diag),
                color = cores.tinta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                diagnostico?.hora ?: stringResource(R.string.diag_sem_dados),
                color = cores.suave,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        BalaoAjuda(
            texto = stringResource(R.string.ajuda_diag),
            visivel = ajudaAtiva,
            modifier = Modifier.padding(horizontal = 13.dp)
        )

        Column(Modifier.padding(horizontal = 14.dp, vertical = 0.dp).padding(bottom = 10.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(cores.elevado)
                    .padding(14.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    diagnostico?.let { d -> d.nome + (d.rssi?.let { "  \u00b7  \uD83D\uDCF6 $it dBm" } ?: "") } ?: "",
                    color = cores.suave,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    diagnostico?.let {
                        when {
                            it.combinacao != null -> "\uD83D\uDD17 " + it.combinacao
                            it.emEspera -> stringResource(R.string.aguardando_sequencia)
                            it.indiceEvento in NOMES_EVENTO.indices -> stringResource(NOMES_EVENTO[it.indiceEvento])
                            else -> "\u2014"
                        }
                    } ?: stringResource(R.string.diag_espera),
                    color = when {
                        diagnostico?.combinacao != null -> cores.ok
                        diagnostico?.emEspera == true -> cores.avisoTinta
                        diagnostico != null -> cores.azul
                        else -> cores.tinta
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    diagnostico?.hora ?: "",
                    color = cores.suave,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (diagnostico != null && diagnostico.bytesHex.isNotEmpty()) {
                Text(
                    stringResource(R.string.lab_trama),
                    color = cores.suave,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                TramaHex(diagnostico.bytesHex, diagnostico.eventoPos)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TramaHex(hex: String, posicaoDestaque: Int) {
    val cores = LocalCoresGateway.current
    val bytes = hex.trim().split(" ").filter { it.isNotEmpty() }

    androidx.compose.foundation.layout.FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(top = 5.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(cores.elevado)
            .padding(9.dp, 9.dp, 11.dp, 9.dp)
    ) {
        bytes.forEachIndexed { i, b ->
            val destacado = i == posicaoDestaque
            Text(
                b,
                color = if (destacado) cores.azul else cores.suave,
                fontWeight = if (destacado) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .let {
                        if (destacado) it
                            .clip(RoundedCornerShape(3.dp))
                            .background(cores.azulTenue)
                            .padding(horizontal = 2.dp)
                        else it
                    }
            )
        }
    }
}
