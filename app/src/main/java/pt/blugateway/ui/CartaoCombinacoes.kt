package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import pt.blugateway.data.Combinacao
import pt.blugateway.data.Perfil
import pt.blugateway.ui.theme.LocalCoresGateway

private val SIMBOLOS_PONTOS_COMB = arrayOf("\u2022", "\u2022\u2022", "\u2022\u2022\u2022", "\u2500", "\u2500\u2500", "\u2500\u2500\u2500", "\u2500\u2026")
private val SIMBOLOS_MAO_COMB = arrayOf("\uD83D\uDC46", "\u270C\uFE0F", "\uD83E\uDD1F", "\u270A", "\u270A\u270A", "\u270A\u270A\u270A", "\u270A\u23F1\uFE0F")

@Composable
fun CartaoCombinacoes(
    perfil: Perfil,
    notacaoPontos: Boolean,
    construtorAberto: Boolean,
    construtorSequencia: List<Int>,
    onAlternaModo: (Boolean) -> Unit,
    onAlteraJanela: (Float) -> Unit,
    onAbreConstrutor: () -> Unit,
    onFechaConstrutor: () -> Unit,
    onAdicionaAoConstrutor: (Int) -> Unit,
    onLimpaConstrutor: () -> Unit,
    onApagaUltimoConstrutor: () -> Unit,
    onGuardaCombinacao: (String) -> Boolean,
    onApagaCombinacao: (String) -> Unit,
    onAdicionaAcao: (String) -> Unit,
    onRemoveAcao: (String, Int) -> Unit,
    onAtualizaAcao: (String, Int, (Acao) -> Acao) -> Unit
) {
    val cores = LocalCoresGateway.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
            .padding(13.dp)
    ) {
        LinhaInterruptor(
            titulo = stringResource(R.string.modo_combinacao),
            dica = stringResource(R.string.modo_combinacao_dica),
            ligado = perfil.modoCombinacao,
            onMuda = onAlternaModo
        )

        if (perfil.modoCombinacao) {
            CampoJanela(
                segundosAtual = perfil.janelaCombinacaoMs / 1000f,
                onAltera = onAlteraJanela
            )

            Column(Modifier.padding(top = 12.dp)) {
                if (perfil.combinacoes.isEmpty()) {
                    pt.blugateway.ui.theme.TextoEstadoVazio(stringResource(R.string.sem_combinacoes))
                } else {
                    perfil.combinacoes.forEach { comb ->
                        ItemCombinacao(
                            combinacao = comb,
                            notacaoPontos = notacaoPontos,
                            onApaga = { onApagaCombinacao(comb.id) },
                            onAdicionaAcao = { onAdicionaAcao(comb.id) },
                            onRemoveAcao = { j -> onRemoveAcao(comb.id, j) },
                            onAtualizaAcao = { j, t -> onAtualizaAcao(comb.id, j, t) }
                        )
                    }
                }

                if (construtorAberto) {
                    ConstrutorSequencia(
                        sequencia = construtorSequencia,
                        notacaoPontos = notacaoPontos,
                        onAdiciona = onAdicionaAoConstrutor,
                        onLimpa = onLimpaConstrutor,
                        onApagaUltimo = onApagaUltimoConstrutor,
                        onCancela = onFechaConstrutor,
                        onGuarda = onGuardaCombinacao
                    )
                } else {
                    TextButton(
                        onClick = onAbreConstrutor,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .background(cores.elevado, RoundedCornerShape(12.dp))
                    ) {
                        Text(stringResource(R.string.nova_combinacao), color = cores.azul, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaInterruptor(titulo: String, dica: String, ligado: Boolean, onMuda: (Boolean) -> Unit) {
    val cores = LocalCoresGateway.current
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(titulo, color = cores.tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(dica, color = cores.suave, fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = ligado, onCheckedChange = onMuda)
    }
}

@Composable
private fun CampoJanela(segundosAtual: Float, onAltera: (Float) -> Unit) {
    val cores = LocalCoresGateway.current
    var texto by remember(segundosAtual) { mutableStateOf(formataSegundos(segundosAtual)) }

    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.janela), color = cores.suave, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
        BasicTextField(
            value = texto,
            onValueChange = { novo ->
                texto = novo
                novo.toFloatOrNull()?.let(onAltera)
            },
            textStyle = TextStyle(color = cores.tinta, fontSize = 12.sp),
            modifier = Modifier
                .width(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(cores.elevado)
                .padding(8.dp, 6.dp)
        )
        Text(stringResource(R.string.segundos), color = cores.suave, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

private fun formataSegundos(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()

@Composable
private fun ItemCombinacao(
    combinacao: Combinacao,
    notacaoPontos: Boolean,
    onApaga: () -> Unit,
    onAdicionaAcao: () -> Unit,
    onRemoveAcao: (Int) -> Unit,
    onAtualizaAcao: (Int, (Acao) -> Acao) -> Unit
) {
    val cores = LocalCoresGateway.current
    var aberta by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(cores.elevado)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(clickableSemSplash { aberta = !aberta })
                .padding(11.dp, 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(combinacao.nome, color = cores.tinta, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    combinacao.sequencia.joinToString(" \u2192 ") { simboloTexto(it, notacaoPontos) },
                    color = cores.suave, fontSize = 10.sp,
                    fontFamily = if (notacaoPontos) FontFamily.Monospace else FontFamily.Default,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (combinacao.acoes.isNotEmpty()) {
                Text(
                    "${combinacao.acoes.size}",
                    color = cores.azul, fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(cores.azulTenue)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onApaga, modifier = Modifier.size(26.dp).padding(start = 4.dp)) {
                Text("\u00d7", color = cores.suave, fontSize = 14.sp)
            }
        }

        if (aberta) {
            Column(Modifier.padding(horizontal = 11.dp).padding(bottom = 10.dp)) {
                combinacao.acoes.forEachIndexed { j, a ->
                    LinhaAcao(a, onRemove = { onRemoveAcao(j) }, onAtualiza = { t -> onAtualizaAcao(j, t) })
                }
                TextButton(onClick = onAdicionaAcao, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(stringResource(R.string.add_acao), color = cores.azul, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ConstrutorSequencia(
    sequencia: List<Int>,
    notacaoPontos: Boolean,
    onAdiciona: (Int) -> Unit,
    onLimpa: () -> Unit,
    onApagaUltimo: () -> Unit,
    onCancela: () -> Unit,
    onGuarda: (String) -> Boolean
) {
    val cores = LocalCoresGateway.current
    var nome by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(cores.elevado)
            .padding(11.dp)
    ) {
        CampoTexto(
            rotulo = stringResource(R.string.nome_combinacao),
            valor = nome,
            placeholder = "",
            onValor = { nome = it }
        )

        Text(
            stringResource(R.string.sequencia), color = cores.suave, fontSize = 10.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )

        if (sequencia.isEmpty()) {
            Text(stringResource(R.string.tocar_para_adicionar), color = cores.suave, fontSize = 11.sp)
        } else {
            Row {
                sequencia.forEachIndexed { i, idx ->
                    Text(
                        simboloTexto(idx, notacaoPontos),
                        color = cores.azul, fontSize = 13.sp,
                        fontFamily = if (notacaoPontos) FontFamily.Monospace else FontFamily.Default,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(cores.azulTenue)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                    if (i < sequencia.lastIndex) {
                        Text("\u2192", color = cores.suave, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (e in 0..6) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cores.cartao)
                        .then(clickableSemSplash { onAdiciona(e) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        simboloTexto(e, notacaoPontos),
                        fontSize = if (notacaoPontos) 13.sp else 15.sp,
                        fontFamily = if (notacaoPontos) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }
        }

        if (aviso) {
            Text(
                stringResource(R.string.sequencia_vazia),
                color = cores.avisoTinta, fontSize = 10.5.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLimpa) { Text(stringResource(R.string.limpar_sequencia), color = cores.suave, fontSize = 11.sp) }
            TextButton(onClick = onApagaUltimo) { Text(stringResource(R.string.apagar_ultimo), color = cores.suave, fontSize = 11.sp) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar), color = cores.suave, fontSize = 11.sp) }
            TextButton(onClick = {
                val ok = onGuarda(nome)
                if (!ok) aviso = true else { nome = ""; aviso = false }
            }) { Text(stringResource(R.string.guardar_combinacao), color = cores.azul, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

private fun simboloTexto(indice: Int, notacaoPontos: Boolean): String =
    if (notacaoPontos) SIMBOLOS_PONTOS_COMB[indice] else SIMBOLOS_MAO_COMB[indice]
