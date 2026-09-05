package pt.blugateway.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

@Composable
fun BarraTopo(
    configAberto: Boolean,
    temaClaro: Boolean,
    somAtivo: Boolean,
    modoEspecialAtivo: Boolean,
    onAlternaConfig: () -> Unit,
    onAlternaModoEspecial: () -> Unit,
    onAlternaTema: () -> Unit,
    onAlternaSom: () -> Unit,
    onEscolheIdioma: () -> Unit,
    onAbreCardsVisiveis: () -> Unit,
    onAbreMapa: () -> Unit,
    onAbreCenarios: () -> Unit
) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier.fillMaxWidth().padding(14.dp, 10.dp, 14.dp, 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconeBluetooth(modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(stringResource(R.string.app_name), color = cores.suave, fontSize = 13.sp, modifier = Modifier.weight(1f))

        BotaoTopo(
            emoji = "\uD83D\uDEE0\uFE0F",
            ativo = configAberto,
            descricao = stringResource(R.string.tt_config),
            onClick = onAlternaConfig
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = "\uD83D\uDD17",
            ativo = modoEspecialAtivo,
            descricao = stringResource(R.string.comandos_especiais),
            onClick = onAlternaModoEspecial
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = if (somAtivo) "\uD83D\uDD0A" else "\uD83D\uDD07",
            ativo = false,
            descricao = stringResource(R.string.tt_som),
            onClick = onAlternaSom
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = "\uD83C\uDF10",
            ativo = false,
            descricao = stringResource(R.string.tt_idioma),
            onClick = onEscolheIdioma
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = if (temaClaro) "\u2600\uFE0F" else "\uD83C\uDF19",
            ativo = false,
            descricao = stringResource(R.string.tt_tema),
            onClick = onAlternaTema
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = "\uD83D\uDCD1",
            ativo = false,
            descricao = stringResource(R.string.blocos_visiveis),
            onClick = onAbreCardsVisiveis
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = "\uD83D\uDDFA\uFE0F",
            ativo = false,
            descricao = stringResource(R.string.mapa_trajeto),
            onClick = onAbreMapa
        )
        Spacer(Modifier.width(7.dp))
        BotaoTopo(
            emoji = "\uD83C\uDFAF",
            ativo = false,
            descricao = stringResource(R.string.tt_cenarios),
            onClick = onAbreCenarios
        )
    }
}

@Composable
private fun BotaoTopo(emoji: String, ativo: Boolean, descricao: String, onClick: () -> Unit) {
    val cores = LocalCoresGateway.current
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (ativo) cores.azulTenue else cores.elevado)
            .then(Modifier),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
            Text(emoji, fontSize = 14.sp)
        }
    }
}

/* Icone de Bluetooth desenhado a vetor, replicando exatamente o SVG
   usado na interface HTML de pre-visualizacao (viewBox 24x24):
   M12,2 L17,7 L12,12 L17,17 L12,22 L12,2 M7,7 L12,12 M7,17 L12,12
   -- o "B" estilizado do Bluetooth, com o traco central em zigue-zague. */
@Composable
private fun IconeBluetooth(modifier: Modifier = Modifier) {
    val cores = LocalCoresGateway.current
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun p(fx: Float, fy: Float) = Offset(fx * w, fy * h)

        val caminho = Path().apply {
            moveTo(p(0.5000f, 0.0833f).x, p(0.5000f, 0.0833f).y)
            lineTo(p(0.7083f, 0.2917f).x, p(0.7083f, 0.2917f).y)
            lineTo(p(0.5000f, 0.5000f).x, p(0.5000f, 0.5000f).y)
            lineTo(p(0.7083f, 0.7083f).x, p(0.7083f, 0.7083f).y)
            lineTo(p(0.5000f, 0.9167f).x, p(0.5000f, 0.9167f).y)
            lineTo(p(0.5000f, 0.0833f).x, p(0.5000f, 0.0833f).y)
            moveTo(p(0.2917f, 0.2917f).x, p(0.2917f, 0.2917f).y)
            lineTo(p(0.5000f, 0.5000f).x, p(0.5000f, 0.5000f).y)
            moveTo(p(0.2917f, 0.7083f).x, p(0.2917f, 0.7083f).y)
            lineTo(p(0.5000f, 0.5000f).x, p(0.5000f, 0.5000f).y)
        }
        drawPath(
            path = caminho,
            color = cores.azul,
            style = Stroke(width = size.minDimension * 0.0833f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
    }
}
