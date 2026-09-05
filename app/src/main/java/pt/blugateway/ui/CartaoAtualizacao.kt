package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pt.blugateway.BuildConfig
import pt.blugateway.R
import pt.blugateway.net.GestorAtualizacao
import pt.blugateway.net.ResultadoVerificacaoAtualizacao
import pt.blugateway.ui.theme.LocalCoresGateway

/* Estados possiveis do card, do ponto de vista do utilizador --
   cada um mostra um texto/botao diferente. Nenhuma verificacao
   acontece sozinha: so ao tocar em "Verificar atualizacoes"
   (INATIVO -> A_VERIFICAR), e so ao tocar em "Descarregar e
   instalar" quando ha uma versao nova (DISPONIVEL -> A_DESCARREGAR
   -> instalador do sistema abre sozinho, sem dialogo de confirmacao
   proprio desta app). */
private sealed class EstadoCartaoAtualizacao {
    object Inativo : EstadoCartaoAtualizacao()
    object AVerificar : EstadoCartaoAtualizacao()
    object JaAtualizado : EstadoCartaoAtualizacao()
    data class Disponivel(val versao: String, val urlApk: String) : EstadoCartaoAtualizacao()
    object ADescarregar : EstadoCartaoAtualizacao()
    data class Erro(val mensagem: String) : EstadoCartaoAtualizacao()
}

@Composable
fun CartaoAtualizacao() {
    val cores = LocalCoresGateway.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var estado by remember { mutableStateOf<EstadoCartaoAtualizacao>(EstadoCartaoAtualizacao.Inativo) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\u2B06\uFE0F", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.atualizacao_titulo),
                color = cores.tinta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            stringResource(R.string.atualizacao_versao_atual, BuildConfig.VERSION_NAME),
            color = cores.suave,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        when (val e = estado) {
            is EstadoCartaoAtualizacao.Inativo -> {
                TextButton(
                    onClick = {
                        estado = EstadoCartaoAtualizacao.AVerificar
                        escopo.launch {
                            estado = when (val resultado = GestorAtualizacao.verificaAtualizacao()) {
                                is ResultadoVerificacaoAtualizacao.Disponivel ->
                                    EstadoCartaoAtualizacao.Disponivel(resultado.versao, resultado.urlApk)
                                is ResultadoVerificacaoAtualizacao.JaAtualizado ->
                                    EstadoCartaoAtualizacao.JaAtualizado
                                is ResultadoVerificacaoAtualizacao.Erro ->
                                    EstadoCartaoAtualizacao.Erro(resultado.mensagem)
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.atualizacao_verificar), color = cores.azul, fontSize = 11.5.sp)
                }
            }

            is EstadoCartaoAtualizacao.AVerificar -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.atualizacao_a_verificar), color = cores.suave, fontSize = 11.sp)
                }
            }

            is EstadoCartaoAtualizacao.JaAtualizado -> {
                Text(
                    "\u2713 " + stringResource(R.string.atualizacao_ja_atualizado),
                    color = cores.ok,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            is EstadoCartaoAtualizacao.Disponivel -> {
                val textoFalhaDownload = stringResource(R.string.atualizacao_falha_download)
                Text(
                    stringResource(R.string.atualizacao_disponivel, e.versao),
                    color = cores.tinta,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(
                    onClick = {
                        estado = EstadoCartaoAtualizacao.ADescarregar
                        escopo.launch {
                            val ficheiro = GestorAtualizacao.descarregaApk(contexto, e.urlApk)
                            if (ficheiro != null) {
                                GestorAtualizacao.instalaApk(contexto, ficheiro)
                                estado = EstadoCartaoAtualizacao.Inativo
                            } else {
                                estado = EstadoCartaoAtualizacao.Erro(textoFalhaDownload)
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.atualizacao_descarregar_instalar), color = cores.azul, fontSize = 11.5.sp)
                }
            }

            is EstadoCartaoAtualizacao.ADescarregar -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.atualizacao_a_descarregar), color = cores.suave, fontSize = 11.sp)
                }
            }

            is EstadoCartaoAtualizacao.Erro -> {
                Text(
                    stringResource(R.string.atualizacao_erro, e.mensagem),
                    color = cores.avisoTinta,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(
                    onClick = { estado = EstadoCartaoAtualizacao.Inativo },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.atualizacao_verificar), color = cores.azul, fontSize = 11.5.sp)
                }
            }
        }
    }
}
