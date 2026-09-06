package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import pt.blugateway.ble.GestorSons
import pt.blugateway.R
import pt.blugateway.data.Comando
import pt.blugateway.data.Perfil
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Card com uma grelha visual de comandos -- um botao grande por
 * comando (imagem + nome + badges de estado), em vez da lista
 * vertical compacta de CartaoComandos. Puramente organizacional:
 * tocar num comando NAO simula nenhum clique nem dispara acoes, so
 * abre um pequeno editor para definir o link da imagem.
 *
 * O numero de colunas adapta-se a quantidade de comandos: 1 comando
 * = 1 coluna, 2-3 = 2 colunas, 4+ = ate 4 colunas (testado
 * isoladamente antes de integrar, ver calculaColunas).
 */
@Composable
fun CartaoGrelhaComandos(
    comandos: List<Comando>,
    perfis: List<Perfil>,
    ajudaAtiva: Boolean,
    onAlternaAjuda: () -> Unit,
    onDefineImagemUrl: (String, String?) -> Unit,
    onAlternaBloqueioImagem: (String, Boolean) -> Unit
) {
    val cores = LocalCoresGateway.current
    var aberto by remember { mutableStateOf(true) }
    var comandoEditandoImagem by remember { mutableStateOf<Comando?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { aberto = !aberto }.padding(13.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83D\uDD32", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            BotaoAjuda(ativo = ajudaAtiva, onClick = onAlternaAjuda)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.grelha_comandos_titulo),
                color = cores.tinta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            val resumo = if (comandos.size == 1) stringResource(R.string.cmd_1)
                         else stringResource(R.string.cmd_n, comandos.size)
            Text(resumo, color = cores.suave, fontSize = 9.sp)
        }

        BalaoAjuda(
            texto = stringResource(R.string.ajuda_grelha_comandos),
            visivel = ajudaAtiva,
            modifier = Modifier.padding(horizontal = 13.dp)
        )

        if (aberto) {
            if (comandos.isEmpty()) {
                pt.blugateway.ui.theme.TextoEstadoVazio(
                    stringResource(R.string.nenhum_comando_grelha),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            } else {
                val colunas = calculaColunas(comandos.size)
                val linhas = comandos.chunked(colunas)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 10.dp)
                ) {
                    linhas.forEach { linha ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            linha.forEach { c ->
                                val perfilAtual = perfis.firstOrNull { it.id == c.perfilId }
                                Box(Modifier.weight(1f)) {
                                    BotaoGrelhaComando(
                                        comando = c,
                                        nomePerfil = perfilAtual?.nome,
                                        onEditarImagem = { comandoEditandoImagem = c },
                                        onAlternaBloqueio = { onAlternaBloqueioImagem(c.mac, !c.imagemBloqueada) }
                                    )
                                }
                            }
                            // preenche os espacos vazios da ultima linha
                            // incompleta, para os cards nao esticarem a
                            // largura toda quando sao menos que 'colunas'
                            repeat(colunas - linha.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    comandoEditandoImagem?.let { c ->
        DialogoEditarImagemComando(
            comando = c,
            onGrava = { url ->
                onDefineImagemUrl(c.mac, url)
                comandoEditandoImagem = null
            },
            onFecha = { comandoEditandoImagem = null }
        )
    }
}

/**
 * Numero de colunas da grelha, adaptado a quantidade de comandos --
 * 1 comando fica sozinho numa coluna (nao faz sentido esticar um
 * card unico a largura toda em "grelha"), 2-3 usam 2 colunas, 4 ou
 * mais usam ate 4 colunas (as linhas seguintes sao geradas por
 * chunked() acima). Testado isoladamente em Python antes de portar.
 */
private fun calculaColunas(nComandos: Int): Int = when {
    nComandos <= 1 -> 1
    nComandos <= 3 -> 2
    else -> 4
}

/**
 * Anima N pulsos distintos e sequenciais (pontos que acendem e
 * apagam um a seguir ao outro) sempre que 'cliqueEm' muda para um
 * valor novo -- replica exatamente o numero de pulsos e a duracao
 * (curta/longa) que GestorSons usa para o som do mesmo tipo de
 * clique (ver GestorSons.PADRAO_POR_INDICE), para o feedback visual
 * e sonoro nunca desalinharem. Cor verde se o clique disparou uma
 * acao real, azul se foi um clique "vazio" (evento sem nenhuma acao
 * configurada nesse perfil, ou uma combinacao ainda em espera).
 *
 * Nao mostra nada enquanto nao houver nenhum clique registado
 * (tipoIndice ou cliqueEm nulos) nem depois de a animacao terminar
 * -- so' fica visivel durante a janela do ultimo clique recebido.
 * Timing validado isoladamente em Python antes desta integracao.
 */
@Composable
private fun PulsosClique(
    tipoIndice: Int?,
    cliqueEm: Long?,
    disparouAcao: Boolean,
    modifier: Modifier = Modifier
) {
    if (tipoIndice == null || cliqueEm == null) return
    val (contagem, longo) = GestorSons.PADRAO_POR_INDICE.getOrNull(tipoIndice) ?: return
    val duracaoPulsoMs = if (longo) GestorSons.DURACAO_BIP_LONGO_MS else GestorSons.DURACAO_BIP_CURTO_MS
    val cor = if (disparouAcao) Color(0xFF3FB950) else Color(0xFF2BA6E0)

    // um estado de "aceso" por pulso -- cada um liga/desliga na vez
    // certa, seguindo o timing exato de GestorSons
    val acesos = remember(cliqueEm) { List(contagem) { mutableStateOf(false) } }
    var visivel by remember(cliqueEm) { mutableStateOf(true) }

    LaunchedEffect(cliqueEm) {
        for (i in 0 until contagem) {
            acesos[i].value = true
            delay(duracaoPulsoMs.toLong())
            acesos[i].value = false
            if (i < contagem - 1) delay(GestorSons.PAUSA_ENTRE_BIPS_MS)
        }
        visivel = false
    }

    if (visivel) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            acesos.forEach { aceso ->
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (aceso.value) cor else cor.copy(alpha = 0.15f))
                )
            }
        }
    }
}

@Composable
private fun BotaoGrelhaComando(
    comando: Comando,
    nomePerfil: String?,
    onEditarImagem: () -> Unit,
    onAlternaBloqueio: () -> Unit
) {
    val cores = LocalCoresGateway.current

    val fundo = if (comando.foraDeAlcance) cores.avisoFundo else cores.elevado
    val temImagem = comando.imagemUrl != null
    // so' bloqueia de facto o toque quando ha imagem E esta marcada
    // como bloqueada -- sem imagem definida, tocar continua sempre a
    // abrir o editor, mesmo que o campo imagemBloqueada tenha ficado
    // true de uma imagem anterior removida
    val tocarAbreEditor = !temImagem || !comando.imagemBloqueada

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(fundo)
            .then(if (tocarAbreEditor) Modifier.clickable(onClick = onEditarImagem) else Modifier)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(cores.cartao),
            contentAlignment = Alignment.Center
        ) {
            if (comando.imagemUrl != null) {
                AsyncImage(
                    model = comando.imagemUrl,
                    contentDescription = comando.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))
                )
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(2.dp, cores.suave, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(cores.suave))
                }
            }

            if (comando.foraDeAlcance) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(cores.avisoTinta)
                )
            }

            // Cadeado -- so' aparece quando ha imagem definida (nao
            // faz sentido bloquear/desbloquear "nada"). Tocar nele
            // alterna o bloqueio sem abrir o editor -- tem o seu
            // proprio clickable, que intercepta o toque antes deste
            // chegar ao Column pai.
            if (temImagem) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            onClick = onAlternaBloqueio
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (comando.imagemBloqueada) "\uD83D\uDD12" else "\uD83D\uDD13",
                        fontSize = 11.sp
                    )
                }
            }

            PulsosClique(
                tipoIndice = comando.ultimoCliqueTipo,
                cliqueEm = comando.ultimoCliqueEm,
                disparouAcao = comando.ultimoCliqueDisparouAcao,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
            )
        }

        Text(
            comando.nome,
            color = cores.tinta,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (nomePerfil != null) {
            Text(
                "\uD83C\uDFAF $nomePerfil",
                color = cores.suave,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Row(Modifier.padding(top = 2.dp)) {
            comando.rssi?.let {
                Text("\uD83D\uDCF6$it", color = cores.suave, fontSize = 8.5.sp)
                Spacer(Modifier.width(6.dp))
            }
            comando.bateria?.let {
                Text("\uD83D\uDD0B$it%", color = cores.suave, fontSize = 8.5.sp)
            }
        }
    }
}

@Composable
private fun DialogoEditarImagemComando(
    comando: Comando,
    onGrava: (String?) -> Unit,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var url by remember(comando.mac) { mutableStateOf(comando.imagemUrl ?: "") }

    Dialog(onDismissRequest = onFecha, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(12.dp)),
            color = cores.cartao
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.grelha_editar_imagem_titulo, comando.nome),
                    color = cores.tinta,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.grelha_editar_imagem_dica),
                    color = cores.suave,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                if (url.isNotBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(cores.elevado),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                CampoTexto(
                    rotulo = stringResource(R.string.grelha_editar_imagem_campo),
                    valor = url,
                    placeholder = "https://...",
                    onValor = { url = it }
                )

                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    if (comando.imagemUrl != null) {
                        TextButton(onClick = { onGrava(null) }) {
                            Text(stringResource(R.string.grelha_remover_imagem), color = cores.avisoTinta, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onFecha) {
                        Text(stringResource(R.string.cancelar), color = cores.suave, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onGrava(url.trim().ifBlank { null }) }) {
                        Text(stringResource(R.string.guardar), color = cores.azul, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
