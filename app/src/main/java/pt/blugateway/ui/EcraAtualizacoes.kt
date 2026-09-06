package pt.blugateway.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import pt.blugateway.BuildConfig
import pt.blugateway.R
import pt.blugateway.net.GestorAtualizacao
import pt.blugateway.net.ResultadoVerificacaoAtualizacao
import pt.blugateway.ui.theme.LocalCoresGateway

/* Estados possiveis do ecra, do ponto de vista do utilizador --
   copia independente da mesma logica ja usada e testada em
   CartaoAtualizacao.kt (nome com sufixo Ecra para nao colidir com o
   tipo homonimo la definido). Nenhuma verificacao acontece sozinha:
   so ao tocar em "Verificar atualizacoes", e so ao tocar em
   "Descarregar e instalar" quando ha uma versao nova. */
private sealed class EstadoEcraAtualizacoes {
    object Inativo : EstadoEcraAtualizacoes()
    object AVerificar : EstadoEcraAtualizacoes()
    object JaAtualizado : EstadoEcraAtualizacoes()
    data class Disponivel(val versao: String, val urlApk: String) : EstadoEcraAtualizacoes()
    object ADescarregar : EstadoEcraAtualizacoes()
    data class Erro(val mensagem: String) : EstadoEcraAtualizacoes()
}

/**
 * Ecra proprio para verificar e instalar atualizacoes, acessivel a
 * partir de um botao dedicado na barra de topo (ver BarraTopo) --
 * tal como Mapa, Cenarios e Perfis, usa Dialog() fullscreen. O card
 * equivalente dentro de Configuracao (CartaoAtualizacao) continua a
 * existir tambem, por pedido explicito.
 */
@Composable
fun EcraAtualizacoes(onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var estado by remember { mutableStateOf<EstadoEcraAtualizacoes>(EstadoEcraAtualizacoes.Inativo) }

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
                        stringResource(R.string.atualizacao_titulo),
                        color = cores.tinta,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                Text(
                    stringResource(R.string.atualizacao_versao_atual, BuildConfig.VERSION_NAME),
                    color = cores.suave,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )

                when (val e = estado) {
                    is EstadoEcraAtualizacoes.Inativo -> {
                        TextButton(
                            onClick = {
                                estado = EstadoEcraAtualizacoes.AVerificar
                                escopo.launch {
                                    estado = when (val resultado = GestorAtualizacao.verificaAtualizacao()) {
                                        is ResultadoVerificacaoAtualizacao.Disponivel ->
                                            EstadoEcraAtualizacoes.Disponivel(resultado.versao, resultado.urlApk)
                                        is ResultadoVerificacaoAtualizacao.JaAtualizado ->
                                            EstadoEcraAtualizacoes.JaAtualizado
                                        is ResultadoVerificacaoAtualizacao.Erro ->
                                            EstadoEcraAtualizacoes.Erro(resultado.mensagem)
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(stringResource(R.string.atualizacao_verificar), color = cores.azul, fontSize = 13.sp)
                        }
                    }

                    is EstadoEcraAtualizacoes.AVerificar -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.atualizacao_a_verificar), color = cores.suave, fontSize = 12.sp)
                        }
                    }

                    is EstadoEcraAtualizacoes.JaAtualizado -> {
                        Text(
                            "\u2713 " + stringResource(R.string.atualizacao_ja_atualizado),
                            color = cores.ok,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    is EstadoEcraAtualizacoes.Disponivel -> {
                        val textoFalhaDownload = stringResource(R.string.atualizacao_falha_download)
                        Text(
                            stringResource(R.string.atualizacao_disponivel, e.versao),
                            color = cores.tinta,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        TextButton(
                            onClick = {
                                estado = EstadoEcraAtualizacoes.ADescarregar
                                escopo.launch {
                                    val ficheiro = GestorAtualizacao.descarregaApk(contexto, e.urlApk)
                                    if (ficheiro != null) {
                                        GestorAtualizacao.instalaApk(contexto, ficheiro)
                                        estado = EstadoEcraAtualizacoes.Inativo
                                    } else {
                                        estado = EstadoEcraAtualizacoes.Erro(textoFalhaDownload)
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(stringResource(R.string.atualizacao_descarregar_instalar), color = cores.azul, fontSize = 13.sp)
                        }
                    }

                    is EstadoEcraAtualizacoes.ADescarregar -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.atualizacao_a_descarregar), color = cores.suave, fontSize = 12.sp)
                        }
                    }

                    is EstadoEcraAtualizacoes.Erro -> {
                        Text(
                            stringResource(R.string.atualizacao_erro, e.mensagem),
                            color = cores.avisoTinta,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        TextButton(
                            onClick = { estado = EstadoEcraAtualizacoes.Inativo },
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(stringResource(R.string.atualizacao_verificar), color = cores.azul, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
