package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.data.ModoRetencaoTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Card de Configuracao para a politica de retencao do historico de
 * trajeto -- global (aplica-se a todos os comandos), com duas
 * opcoes: manter os ultimos N dias, ou os ultimos N pontos
 * independentemente da idade. "Ultima viagem" (ver EcraMapa) e um
 * modo de VISUALIZACAO separado, calculado a partir do que ja esta
 * guardado -- nao aparece aqui, porque nao afeta o que se guarda.
 */
@Composable
fun CartaoRetencaoTrajeto(
    modoAtual: ModoRetencaoTrajeto,
    dias: Int,
    pontos: Int,
    onDefineModo: (ModoRetencaoTrajeto) -> Unit,
    onDefineDias: (Int) -> Unit,
    onDefinePontos: (Int) -> Unit
) {
    val cores = LocalCoresGateway.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
            .padding(13.dp)
    ) {
        Text(
            stringResource(pt.blugateway.R.string.retencao_titulo),
            color = cores.tinta,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = modoAtual == ModoRetencaoTrajeto.DIAS,
                onClick = { onDefineModo(ModoRetencaoTrajeto.DIAS) }
            )
            Text(
                stringResource(pt.blugateway.R.string.retencao_manter_dias),
                color = cores.tinta,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (modoAtual == ModoRetencaoTrajeto.DIAS) {
            var texto by remember(dias) { mutableStateOf(dias.toString()) }
            Row(Modifier.padding(start = 40.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(70.dp)) {
                    CampoTexto(
                        rotulo = stringResource(pt.blugateway.R.string.retencao_dias),
                        valor = texto,
                        placeholder = "30",
                        onValor = { novo ->
                            texto = novo
                            novo.toIntOrNull()?.let { if (it > 0) onDefineDias(it) }
                        }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = modoAtual == ModoRetencaoTrajeto.QUANTIDADE,
                onClick = { onDefineModo(ModoRetencaoTrajeto.QUANTIDADE) }
            )
            Text(
                stringResource(pt.blugateway.R.string.retencao_manter_pontos),
                color = cores.tinta,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (modoAtual == ModoRetencaoTrajeto.QUANTIDADE) {
            var texto by remember(pontos) { mutableStateOf(pontos.toString()) }
            Row(Modifier.padding(start = 40.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(70.dp)) {
                    CampoTexto(
                        rotulo = stringResource(pt.blugateway.R.string.retencao_pontos),
                        valor = texto,
                        placeholder = "500",
                        onValor = { novo ->
                            texto = novo
                            novo.toIntOrNull()?.let { if (it > 0) onDefinePontos(it) }
                        }
                    )
                }
            }
        }
    }
}
