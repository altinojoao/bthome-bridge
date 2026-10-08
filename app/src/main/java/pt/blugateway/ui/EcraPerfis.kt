package pt.blugateway.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.data.Acao
import pt.blugateway.data.Comando
import pt.blugateway.data.Perfil
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Ecra proprio para gerir perfis -- substitui CartaoPerfis (lista
 * compacta) e CartaoPerfilAtivo (editor de acoes) que antes viviam
 * dentro de Configuracao. Aqui, cada card e' clicavel na totalidade
 * (toca em qualquer parte para expandir/recolher as suas acoes
 * inline, sem navegar para outro ecra), suporta arrastar para
 * reordenar (long-press + arrastar), e duplicar um perfil existente
 * com um toque.
 *
 * Acessivel a partir do menu ⚙️ (ver DialogoMenuTopo), como Mapa e
 * Cenarios usam Dialog() fullscreen.
 */
@Composable
fun EcraPerfis(
    perfis: List<Perfil>,
    comandos: List<Comando>,
    notacaoPontos: Boolean,
    confirmaApagar: String?,
    perfilAtivoId: String?,
    construtorAberto: Boolean,
    construtorSequencia: List<Int>,
    onFecha: () -> Unit,
    onRenomeia: (String, String) -> Unit,
    onPedeApagar: (String) -> Unit,
    onConfirmaApagar: (String) -> Unit,
    onNovoPerfil: () -> Unit,
    onDuplicaPerfil: (String) -> Unit,
    onReordena: (Int, Int) -> Unit,
    onExpande: (String?) -> Unit,
    onTrocaNotacao: () -> Unit,
    onAdicionaAcao: (String, Int) -> Unit,
    onRemoveAcao: (String, Int, Int) -> Unit,
    onAtualizaAcao: (String, Int, Int, (Acao) -> Acao) -> Unit,
    onAlternaModoCombinacao: (String, Boolean) -> Unit,
    onAlteraJanelaCombinacao: (String, Float) -> Unit,
    onAbreConstrutor: () -> Unit,
    onFechaConstrutor: () -> Unit,
    onAdicionaAoConstrutor: (Int) -> Unit,
    onLimpaConstrutor: () -> Unit,
    onApagaUltimoConstrutor: () -> Unit,
    onGuardaCombinacao: (String, String) -> Boolean,
    onApagaCombinacao: (String, String) -> Unit,
    onAdicionaAcaoCombinacao: (String, String) -> Unit,
    onRemoveAcaoCombinacao: (String, String, Int) -> Unit,
    onAtualizaAcaoCombinacao: (String, String, Int, (Acao) -> Acao) -> Unit
) {
    val cores = LocalCoresGateway.current
    var expandidoId by remember { mutableStateOf(perfilAtivoId) }

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sec_perfis),
                        color = cores.tinta,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onTrocaNotacao, modifier = Modifier.size(30.dp)) {
                        Text(
                            if (notacaoPontos) "\u270C\uFE0F" else "\u2022\u2022\u2022",
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                Text(
                    stringResource(R.string.dica_perfis),
                    color = cores.suave,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    ListaPerfisArrastavel(
                        perfis = perfis,
                        comandos = comandos,
                        notacaoPontos = notacaoPontos,
                        confirmaApagar = confirmaApagar,
                        expandidoId = expandidoId,
                        construtorAberto = construtorAberto,
                        construtorSequencia = construtorSequencia,
                        onAlternaExpandido = { id ->
                            val novoExpandido = if (expandidoId == id) null else id
                            expandidoId = novoExpandido
                            onExpande(novoExpandido)
                        },
                        onRenomeia = onRenomeia,
                        onPedeApagar = onPedeApagar,
                        onConfirmaApagar = onConfirmaApagar,
                        onDuplicaPerfil = onDuplicaPerfil,
                        onReordena = onReordena,
                        onAdicionaAcao = onAdicionaAcao,
                        onRemoveAcao = onRemoveAcao,
                        onAtualizaAcao = onAtualizaAcao,
                        onAlternaModoCombinacao = onAlternaModoCombinacao,
                        onAlteraJanelaCombinacao = onAlteraJanelaCombinacao,
                        onAbreConstrutor = onAbreConstrutor,
                        onFechaConstrutor = onFechaConstrutor,
                        onAdicionaAoConstrutor = onAdicionaAoConstrutor,
                        onLimpaConstrutor = onLimpaConstrutor,
                        onApagaUltimoConstrutor = onApagaUltimoConstrutor,
                        onGuardaCombinacao = onGuardaCombinacao,
                        onApagaCombinacao = onApagaCombinacao,
                        onAdicionaAcaoCombinacao = onAdicionaAcaoCombinacao,
                        onRemoveAcaoCombinacao = onRemoveAcaoCombinacao,
                        onAtualizaAcaoCombinacao = onAtualizaAcaoCombinacao
                    )

                    TextButton(
                        onClick = onNovoPerfil,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.novo_perfil), color = cores.azul, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Lista de perfis com suporte a arrastar-para-reordenar, implementado
 * a mao com detectDragGesturesAfterLongPress (sem bibliotecas
 * externas, consistente com o resto do projeto). Cada item mede a
 * propria altura (onGloballyPositioned) para converter o
 * deslocamento vertical do arrasto num numero de posicoes a mover --
 * ver calculaIndiceDestino, testada isoladamente antes de integrar.
 */
@Composable
private fun ListaPerfisArrastavel(
    perfis: List<Perfil>,
    comandos: List<Comando>,
    notacaoPontos: Boolean,
    confirmaApagar: String?,
    expandidoId: String?,
    construtorAberto: Boolean,
    construtorSequencia: List<Int>,
    onAlternaExpandido: (String) -> Unit,
    onRenomeia: (String, String) -> Unit,
    onPedeApagar: (String) -> Unit,
    onConfirmaApagar: (String) -> Unit,
    onDuplicaPerfil: (String) -> Unit,
    onReordena: (Int, Int) -> Unit,
    onAdicionaAcao: (String, Int) -> Unit,
    onRemoveAcao: (String, Int, Int) -> Unit,
    onAtualizaAcao: (String, Int, Int, (Acao) -> Acao) -> Unit,
    onAlternaModoCombinacao: (String, Boolean) -> Unit,
    onAlteraJanelaCombinacao: (String, Float) -> Unit,
    onAbreConstrutor: () -> Unit,
    onFechaConstrutor: () -> Unit,
    onAdicionaAoConstrutor: (Int) -> Unit,
    onLimpaConstrutor: () -> Unit,
    onApagaUltimoConstrutor: () -> Unit,
    onGuardaCombinacao: (String, String) -> Boolean,
    onApagaCombinacao: (String, String) -> Unit,
    onAdicionaAcaoCombinacao: (String, String) -> Unit,
    onRemoveAcaoCombinacao: (String, String, Int) -> Unit,
    onAtualizaAcaoCombinacao: (String, String, Int, (Acao) -> Acao) -> Unit
) {
    var indiceArrastado by remember { mutableStateOf<Int?>(null) }
    var deslocamentoY by remember { mutableStateOf(0f) }
    var alturaItemPx by remember { mutableStateOf(1f) }

    Column {
        perfis.forEachIndexed { indice, perfil ->
            val estaArrastando = indiceArrastado == indice
            val nAcoes = perfil.eventos.sumOf { it.size }
            val nComandos = comandos.count { it.perfilId == perfil.id }

            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (estaArrastando) {
                            Modifier.graphicsLayer { translationY = deslocamentoY }
                        } else Modifier
                    )
                    .onGloballyPositioned { coords ->
                        if (indice == 0) alturaItemPx = coords.size.height.toFloat().coerceAtLeast(1f)
                    }
            ) {
                CardPerfilExpansivel(
                    perfil = perfil,
                    nAcoes = nAcoes,
                    nComandos = nComandos,
                    expandido = expandidoId == perfil.id,
                    podeApagar = perfis.size > 1,
                    confirmaAtivo = confirmaApagar == perfil.id,
                    notacaoPontos = notacaoPontos,
                    emDestaque = estaArrastando,
                    construtorAberto = construtorAberto,
                    construtorSequencia = construtorSequencia,
                    onToca = { onAlternaExpandido(perfil.id) },
                    onRenomeia = { novo -> onRenomeia(perfil.id, novo) },
                    onPedeApagar = { onPedeApagar(perfil.id) },
                    onConfirmaApagar = { onConfirmaApagar(perfil.id) },
                    onDuplica = { onDuplicaPerfil(perfil.id) },
                    onAdicionaAcao = { i -> onAdicionaAcao(perfil.id, i) },
                    onRemoveAcao = { i, j -> onRemoveAcao(perfil.id, i, j) },
                    onAtualizaAcao = { i, j, t -> onAtualizaAcao(perfil.id, i, j, t) },
                    onAlternaModoCombinacao = { ligado -> onAlternaModoCombinacao(perfil.id, ligado) },
                    onAlteraJanelaCombinacao = { seg -> onAlteraJanelaCombinacao(perfil.id, seg) },
                    onAbreConstrutor = onAbreConstrutor,
                    onFechaConstrutor = onFechaConstrutor,
                    onAdicionaAoConstrutor = onAdicionaAoConstrutor,
                    onLimpaConstrutor = onLimpaConstrutor,
                    onApagaUltimoConstrutor = onApagaUltimoConstrutor,
                    onGuardaCombinacao = { nome -> onGuardaCombinacao(perfil.id, nome) },
                    onApagaCombinacao = { combId -> onApagaCombinacao(perfil.id, combId) },
                    onAdicionaAcaoCombinacao = { combId -> onAdicionaAcaoCombinacao(perfil.id, combId) },
                    onRemoveAcaoCombinacao = { combId, j -> onRemoveAcaoCombinacao(perfil.id, combId, j) },
                    onAtualizaAcaoCombinacao = { combId, j, t -> onAtualizaAcaoCombinacao(perfil.id, combId, j, t) },
                    modifierAlca = Modifier.pointerInput(perfis.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                indiceArrastado = indice
                                deslocamentoY = 0f
                            },
                            onDrag = { change, arrasto ->
                                change.consume()
                                deslocamentoY += arrasto.y
                            },
                            onDragEnd = {
                                val origem = indiceArrastado
                                if (origem != null) {
                                    val destino = calculaIndiceDestino(
                                        origem, deslocamentoY, alturaItemPx, perfis.size
                                    )
                                    if (destino != origem) onReordena(origem, destino)
                                }
                                indiceArrastado = null
                                deslocamentoY = 0f
                            },
                            onDragCancel = {
                                indiceArrastado = null
                                deslocamentoY = 0f
                            }
                        )
                    }
                )
            }
        }
    }
}

/**
 * Converte um deslocamento vertical acumulado (em pixels) durante o
 * arrasto num indice de destino -- arredonda para a posicao mais
 * proxima (ex: arrastar mais de meia altura de item avanca uma
 * posicao), e nunca sai dos limites da lista. Testada isoladamente
 * em Python (mesma formula) antes desta integracao.
 */
private fun calculaIndiceDestino(indiceOrigem: Int, deslocamentoY: Float, alturaItemPx: Float, total: Int): Int {
    val deslocamentoEmItens = deslocamentoY / alturaItemPx
    val destino = Math.round(indiceOrigem + deslocamentoEmItens)
    return destino.coerceIn(0, total - 1)
}

@Composable
private fun CardPerfilExpansivel(
    perfil: Perfil,
    nAcoes: Int,
    nComandos: Int,
    expandido: Boolean,
    podeApagar: Boolean,
    confirmaAtivo: Boolean,
    notacaoPontos: Boolean,
    emDestaque: Boolean,
    construtorAberto: Boolean,
    construtorSequencia: List<Int>,
    onToca: () -> Unit,
    onRenomeia: (String) -> Unit,
    onPedeApagar: () -> Unit,
    onConfirmaApagar: () -> Unit,
    onDuplica: () -> Unit,
    onAdicionaAcao: (Int) -> Unit,
    onRemoveAcao: (Int, Int) -> Unit,
    onAtualizaAcao: (Int, Int, (Acao) -> Acao) -> Unit,
    onAlternaModoCombinacao: (Boolean) -> Unit,
    onAlteraJanelaCombinacao: (Float) -> Unit,
    onAbreConstrutor: () -> Unit,
    onFechaConstrutor: () -> Unit,
    onAdicionaAoConstrutor: (Int) -> Unit,
    onLimpaConstrutor: () -> Unit,
    onApagaUltimoConstrutor: () -> Unit,
    onGuardaCombinacao: (String) -> Boolean,
    onApagaCombinacao: (String) -> Unit,
    onAdicionaAcaoCombinacao: (String) -> Unit,
    onRemoveAcaoCombinacao: (String, Int) -> Unit,
    onAtualizaAcaoCombinacao: (String, Int, (Acao) -> Acao) -> Unit,
    modifierAlca: Modifier
) {
    val cores = LocalCoresGateway.current
    var texto by remember(perfil.id, perfil.nome) { mutableStateOf(perfil.nome) }
    var avancadosAbertos by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(cores.elevado)
            .then(if (emDestaque) Modifier.border(1.5.dp, cores.azul, RoundedCornerShape(11.dp)) else Modifier)
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToca)
                .padding(11.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Alca de arrastar -- icone que so' aceita o gesto de
            // long-press+arrastar, separado do toque normal de
            // expandir/recolher (que fica no resto da linha).
            Box(
                Modifier.size(24.dp).then(modifierAlca),
                contentAlignment = Alignment.Center
            ) {
                Text("\u2630", color = cores.suave, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    textStyle = TextStyle(color = cores.tinta, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {} // consome o toque para nao propagar ao Row pai (que expande/recolhe)
                        .onFocusChanged { foco ->
                            if (!foco.isFocused && texto != perfil.nome) onRenomeia(texto)
                        }
                )
                Text(
                    "$nAcoes \u00b7 $nComandos",
                    color = cores.suave,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            IconButton(onClick = onDuplica, modifier = Modifier.size(28.dp)) {
                Text("\u29C9", color = cores.suave, fontSize = 14.sp)
            }

            if (!podeApagar) {
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(28.dp)) {
                    Text("\u00d7", color = cores.suave.copy(alpha = 0.25f), fontSize = 15.sp)
                }
            } else if (confirmaAtivo) {
                TextButton(onClick = onConfirmaApagar) {
                    Text(stringResource(R.string.apagar_q), color = cores.avisoTinta, fontSize = 10.sp)
                }
            } else {
                IconButton(onClick = onPedeApagar, modifier = Modifier.size(28.dp)) {
                    Text("\u00d7", color = cores.suave, fontSize = 15.sp)
                }
            }
        }

        if (expandido) {
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
                perfil.eventos.forEachIndexed { i, acoes ->
                    val avancado = i >= 4
                    if (!avancado || avancadosAbertos) {
                        BlocoEvento(
                            indice = i,
                            acoes = acoes,
                            notacaoPontos = notacaoPontos,
                            onAdiciona = { onAdicionaAcao(i) },
                            onRemove = { j -> onRemoveAcao(i, j) },
                            onAtualiza = { j, t -> onAtualizaAcao(i, j, t) }
                        )
                    }
                }
                TextButton(
                    onClick = { avancadosAbertos = !avancadosAbertos },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        stringResource(
                            if (avancadosAbertos) R.string.menos_avancados else R.string.avancados
                        ),
                        color = cores.azul,
                        fontSize = 10.5.sp
                    )
                }

                CartaoCombinacoes(
                    perfil = perfil,
                    notacaoPontos = notacaoPontos,
                    construtorAberto = construtorAberto,
                    construtorSequencia = construtorSequencia,
                    onAlternaModo = onAlternaModoCombinacao,
                    onAlteraJanela = onAlteraJanelaCombinacao,
                    onAbreConstrutor = onAbreConstrutor,
                    onFechaConstrutor = onFechaConstrutor,
                    onAdicionaAoConstrutor = onAdicionaAoConstrutor,
                    onLimpaConstrutor = onLimpaConstrutor,
                    onApagaUltimoConstrutor = onApagaUltimoConstrutor,
                    onGuardaCombinacao = onGuardaCombinacao,
                    onApagaCombinacao = onApagaCombinacao,
                    onAdicionaAcao = onAdicionaAcaoCombinacao,
                    onRemoveAcao = onRemoveAcaoCombinacao,
                    onAtualizaAcao = onAtualizaAcaoCombinacao
                )
            }
        }
    }
}
