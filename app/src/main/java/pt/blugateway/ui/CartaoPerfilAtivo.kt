package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.data.Acao
import pt.blugateway.data.Metodo
import pt.blugateway.data.Perfil
import pt.blugateway.data.TipoAcao
import pt.blugateway.ui.theme.LocalCoresGateway

private val NOMES_EVENTO = intArrayOf(
    R.string.ev0, R.string.ev1, R.string.ev2, R.string.ev3,
    R.string.ev4, R.string.ev5, R.string.ev6
)
private const val NUM_BASICOS = 4

private val SIMBOLOS_PONTOS = arrayOf("\u2022", "\u2022\u2022", "\u2022\u2022\u2022", "\u2500", "\u2500\u2500", "\u2500\u2500\u2500", "\u2500\u2026")
private val SIMBOLOS_MAO = arrayOf("\uD83D\uDC46", "\u270C\uFE0F", "\uD83E\uDD1F", "\u270A", "\u270A\u270A", "\u270A\u270A\u270A", "\u270A\u23F1\uFE0F")

@Composable
fun CartaoPerfilAtivo(
    perfil: Perfil,
    notacaoPontos: Boolean,
    onTrocaNotacao: () -> Unit,
    onFecha: () -> Unit,
    onAdicionaAcao: (Int) -> Unit,
    onRemoveAcao: (Int, Int) -> Unit,
    onAtualizaAcao: (Int, Int, (Acao) -> Acao) -> Unit
) {
    val cores = LocalCoresGateway.current
    var avancadosAbertos by remember { mutableStateOf(false) }
    val totalAcoes = perfil.eventos.sumOf { it.size }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
    ) {
        Row(Modifier.padding(13.dp, 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83C\uDFAF", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                perfil.nome, color = cores.tinta, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onTrocaNotacao, modifier = Modifier.size(26.dp)) {
                Text(
                    if (notacaoPontos) "\u270C\uFE0F" else "\u2022\u2022\u2022",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
            val resumo = if (totalAcoes == 1) stringResource(R.string.acao_1) else stringResource(R.string.acao_n, totalAcoes)
            Text(resumo, color = cores.suave, fontSize = 9.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp))
            IconButton(onClick = onFecha, modifier = Modifier.size(28.dp)) {
                Text("\u00d7", color = cores.suave, fontSize = 15.sp)
            }
        }

        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
            perfil.eventos.forEachIndexed { i, acoes ->
                val avancado = i >= NUM_BASICOS
                if (!avancado || avancadosAbertos) {
                    BlocoEvento(
                        indice = i,
                        acoes = acoes,
                        notacaoPontos = notacaoPontos,
                        onAdiciona = { onAdicionaAcao(i) },
                        onRemove = { j -> onRemoveAcao(i, j) },
                        onAtualiza = { j, transformacao -> onAtualizaAcao(i, j, transformacao) }
                    )
                }
            }

            TextButton(
                onClick = { avancadosAbertos = !avancadosAbertos },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .background(cores.elevado, RoundedCornerShape(12.dp))
            ) {
                Text(
                    if (avancadosAbertos) stringResource(R.string.menos_avancados) else stringResource(R.string.avancados),
                    color = cores.azul, fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
private fun BlocoEvento(
    indice: Int,
    acoes: List<Acao>,
    notacaoPontos: Boolean,
    onAdiciona: () -> Unit,
    onRemove: (Int) -> Unit,
    onAtualiza: (Int, (Acao) -> Acao) -> Unit
) {
    val cores = LocalCoresGateway.current

    Column(Modifier.padding(top = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (notacaoPontos) SIMBOLOS_PONTOS[indice] else SIMBOLOS_MAO[indice],
                color = if (acoes.isNotEmpty()) cores.azul else cores.suave.copy(alpha = 0.4f),
                fontSize = if (notacaoPontos) 15.sp else 17.sp,
                fontFamily = if (notacaoPontos) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.width(24.dp)
            )
            Text(
                stringResource(NOMES_EVENTO[indice]),
                color = cores.tinta, fontSize = 13.sp,
                fontWeight = if (acoes.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (acoes.isNotEmpty()) {
                Text(
                    "${acoes.size}",
                    color = cores.azul, fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(cores.azulTenue)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        acoes.forEachIndexed { j, a ->
            LinhaAcao(a, onRemove = { onRemove(j) }, onAtualiza = { transformacao -> onAtualiza(j, transformacao) })
        }

        TextButton(
            onClick = onAdiciona,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Text(stringResource(R.string.add_acao), color = cores.azul, fontSize = 11.sp)
        }
    }
}

@Composable
fun LinhaAcao(
    a: Acao,
    onRemove: () -> Unit,
    onAtualiza: ((Acao) -> Acao) -> Unit
) {
    val cores = LocalCoresGateway.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 5.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(cores.elevado)
            .padding(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeletorTipo(a.tipo) { novo -> onAtualiza { it.copy(tipo = novo) } }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Text("\u00d7", color = cores.suave, fontSize = 14.sp)
            }
        }

        when (a.tipo) {
            TipoAcao.CENARIO -> CampoTexto(
                rotulo = stringResource(R.string.id_cenario),
                valor = a.valor,
                placeholder = "000000000000",
                onValor = { v -> onAtualiza { it.copy(valor = v) } }
            )
            TipoAcao.URL, TipoAcao.NTFY -> {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) {
                        CampoTexto(
                            rotulo = if (a.tipo == TipoAcao.NTFY) stringResource(R.string.topico_ntfy) else stringResource(R.string.endereco),
                            valor = a.valor,
                            placeholder = if (a.tipo == TipoAcao.NTFY) "meu-topico" else "https://...",
                            onValor = { v -> onAtualiza { it.copy(valor = v) } }
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    SeletorMetodo(a.metodo) { novo -> onAtualiza { it.copy(metodo = novo) } }
                }
                if (a.tipo == TipoAcao.NTFY) {
                    CampoTexto(
                        rotulo = stringResource(R.string.msg_ntfy),
                        valor = a.mensagem,
                        placeholder = stringResource(R.string.msg_exemplo),
                        onValor = { v -> onAtualiza { it.copy(mensagem = v) } }
                    )
                    Text(
                        "{evento} {codigo} {mac} {bateria} {rssi} {timestamp}",
                        color = cores.suave, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SeletorTipo(tipo: TipoAcao, onMuda: (TipoAcao) -> Unit) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(cores.elevado.let { cores.cartao }).padding(2.dp)
    ) {
        listOf(
            TipoAcao.CENARIO to "\uD83C\uDFAC " + stringResource(R.string.cenario),
            TipoAcao.URL to "\uD83D\uDD17 " + stringResource(R.string.url),
            TipoAcao.NTFY to "\uD83D\uDCE2 " + stringResource(R.string.ntfy)
        ).forEach { (t, rotulo) ->
            val sel = t == tipo
            Text(
                rotulo,
                color = if (sel) cores.azul else cores.suave,
                fontSize = 10.sp,
                fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) cores.azulTenue else androidx.compose.ui.graphics.Color.Transparent)
                    .then(clickableSemSplash { onMuda(t) })
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun SeletorMetodo(metodo: Metodo, onMuda: (Metodo) -> Unit) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier.padding(top = 5.dp).clip(RoundedCornerShape(16.dp)).background(cores.elevado).padding(2.dp)
    ) {
        listOf(Metodo.GET, Metodo.POST).forEach { m ->
            val sel = m == metodo
            Text(
                m.name,
                color = if (sel) cores.azul else cores.suave,
                fontSize = 10.sp,
                fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) cores.azulTenue else androidx.compose.ui.graphics.Color.Transparent)
                    .then(clickableSemSplash { onMuda(m) })
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun CampoTexto(rotulo: String, valor: String, placeholder: String, onValor: (String) -> Unit) {
    val cores = LocalCoresGateway.current
    Column(Modifier.padding(top = 6.dp)) {
        Text(rotulo, color = cores.suave, fontSize = 9.sp, letterSpacing = 0.3.sp)
        BasicTextField(
            value = valor,
            onValueChange = onValor,
            textStyle = TextStyle(color = cores.tinta, fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(cores.cartao)
                .padding(11.dp, 9.dp),
            decorationBox = { inner ->
                if (valor.isEmpty()) {
                    Text(placeholder, color = cores.suave.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                inner()
            }
        )
    }
}

@Composable
fun clickableSemSplash(onClick: () -> Unit): Modifier {
    val fonteInteracao = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return Modifier.clickable(interactionSource = fonteInteracao, indication = null, onClick = onClick)
}
