package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import pt.blugateway.ui.theme.LocalCoresGateway

private data class PassoGuia(val emoji: String, val tituloRes: Int, val textoRes: Int)

private val PASSOS_GUIA = listOf(
    PassoGuia("\uD83D\uDD35", R.string.guia_passo1_titulo, R.string.guia_passo1_texto),
    PassoGuia("\uD83D\uDC46", R.string.guia_passo2_titulo, R.string.guia_passo2_texto),
    PassoGuia("\uD83D\uDCCD", R.string.guia_passo3_titulo, R.string.guia_passo3_texto),
    PassoGuia("\uD83D\uDDFA\uFE0F", R.string.guia_passo4_titulo, R.string.guia_passo4_texto),
    PassoGuia("\uD83D\uDEA7", R.string.guia_passo5_titulo, R.string.guia_passo5_texto),
    PassoGuia("\uD83D\uDCCA", R.string.guia_passo6_titulo, R.string.guia_passo6_texto),
    PassoGuia("\uD83D\uDD52", R.string.guia_passo7_titulo, R.string.guia_passo7_texto),
    PassoGuia("\uD83D\uDE97", R.string.guia_passo8_titulo, R.string.guia_passo8_texto)
)

/**
 * Guia de configuração passo a passo, acedido manualmente através de
 * um botão "?" -- cobre desde parear o beacon até checkpoints,
 * percentagem de disparo e janelas horárias. Sem persistência de
 * progresso nem abertura automática: fica disponível sempre que o
 * utilizador precisar de o consultar de novo.
 */
@Composable
fun EcraGuiaConfiguracao(onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    var passoAtual by remember { mutableIntStateOf(0) }
    val passo = PASSOS_GUIA[passoAtual]

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.72f),
            shape = RoundedCornerShape(16.dp),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.guia_titulo),
                        color = cores.tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Bolinhas de progresso
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PASSOS_GUIA.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(if (i == passoAtual) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (i == passoAtual) cores.azul else cores.linha)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(passo.emoji, fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.guia_passo_contador, passoAtual + 1, PASSOS_GUIA.size),
                        color = cores.suave, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(passo.tituloRes),
                        color = cores.tinta, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(passo.textoRes),
                        color = cores.tinta, fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { if (passoAtual > 0) passoAtual-- },
                        enabled = passoAtual > 0
                    ) {
                        Text(stringResource(R.string.guia_anterior), color = if (passoAtual > 0) cores.azul else cores.linha, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (passoAtual < PASSOS_GUIA.size - 1) {
                        TextButton(onClick = onFecha) {
                            Text(stringResource(R.string.guia_saltar), color = cores.suave, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { passoAtual++ }) {
                            Text(stringResource(R.string.guia_seguinte), color = cores.azul, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        TextButton(onClick = onFecha) {
                            Text(stringResource(R.string.guia_concluir), color = cores.azul, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
