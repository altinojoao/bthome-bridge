package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Menu unico (engrenagem) que agrupa os botoes da barra de topo com
 * uso menos frequente no dia-a-dia -- Configuracoes, Comandos
 * especiais, Som, Idioma, Tema e Blocos visiveis -- numa lista
 * vertical com icone, nome e descricao de cada opcao. Mapa e
 * Cenarios continuam com botao proprio na barra (ver BarraTopo),
 * por serem ecras completos usados com mais frequencia que uma
 * simples alternancia de definicao.
 *
 * Cada item fecha este menu ao ser tocado e dispara a mesma
 * callback que o botao individual disparava antes -- nenhum
 * comportamento novo, so' a forma de aceder mudou.
 */
@Composable
fun DialogoMenuTopo(
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.menu_topo_titulo),
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
            Column(Modifier.fillMaxWidth()) {
                ItemMenuTopo(
                    emoji = "\uD83D\uDEE0\uFE0F",
                    nome = stringResource(R.string.tt_config),
                    descricao = stringResource(R.string.menu_topo_desc_config),
                    ativo = configAberto,
                    onClick = { onAlternaConfig(); onFecha() }
                )
                ItemMenuTopo(
                    emoji = "\uD83D\uDD17",
                    nome = stringResource(R.string.comandos_especiais),
                    descricao = stringResource(R.string.menu_topo_desc_comandos_especiais),
                    ativo = modoEspecialAtivo,
                    onClick = { onAlternaModoEspecial(); onFecha() }
                )
                ItemMenuTopo(
                    emoji = if (somAtivo) "\uD83D\uDD0A" else "\uD83D\uDD07",
                    nome = stringResource(R.string.tt_som),
                    descricao = if (somAtivo) stringResource(R.string.menu_topo_desc_som_ativo)
                                else stringResource(R.string.menu_topo_desc_som_inativo),
                    ativo = false,
                    onClick = { onAlternaSom() }
                )
                ItemMenuTopo(
                    emoji = "\uD83C\uDF10",
                    nome = stringResource(R.string.tt_idioma),
                    descricao = stringResource(R.string.menu_topo_desc_idioma),
                    ativo = false,
                    onClick = { onEscolheIdioma(); onFecha() }
                )
                ItemMenuTopo(
                    emoji = if (temaClaro) "\u2600\uFE0F" else "\uD83C\uDF19",
                    nome = stringResource(R.string.tt_tema),
                    descricao = if (temaClaro) stringResource(R.string.menu_topo_desc_tema_claro)
                                else stringResource(R.string.menu_topo_desc_tema_escuro),
                    ativo = false,
                    onClick = { onAlternaTema() }
                )
                ItemMenuTopo(
                    emoji = "\uD83D\uDCD1",
                    nome = stringResource(R.string.blocos_visiveis),
                    descricao = stringResource(R.string.menu_topo_desc_blocos_visiveis),
                    ativo = false,
                    onClick = { onAbreCardsVisiveis(); onFecha() }
                )
            }
        }
    )
}

@Composable
private fun ItemMenuTopo(
    emoji: String,
    nome: String,
    descricao: String,
    ativo: Boolean,
    onClick: () -> Unit
) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (ativo) cores.azulTenue else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nome, color = cores.tinta, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(
                descricao,
                color = cores.suave,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
