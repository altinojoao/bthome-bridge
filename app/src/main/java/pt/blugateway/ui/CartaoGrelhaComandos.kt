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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
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
    onDefineImagemUrl: (String, String?) -> Unit
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
                                        onEditarImagem = { comandoEditandoImagem = c }
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

@Composable
private fun BotaoGrelhaComando(
    comando: Comando,
    nomePerfil: String?,
    onEditarImagem: () -> Unit
) {
    val cores = LocalCoresGateway.current

    val fundo = if (comando.foraDeAlcance) cores.avisoFundo else cores.elevado

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(fundo)
            .clickable(onClick = onEditarImagem)
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
