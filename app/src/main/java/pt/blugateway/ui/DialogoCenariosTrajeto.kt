package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.ble.GestorSemelhancaTrajeto
import pt.blugateway.data.Acao
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.data.Comando
import pt.blugateway.data.PontoTemplate
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/* Ecra de gestao de cenarios de trajeto para UM comando: lista os ja
   criados (ativar/desativar, apagar), e permite criar um novo a
   partir de uma viagem ja gravada no historico (ver
   GestorSemelhancaTrajeto.separaEmViagens). O modo de desenhar o
   template a mao no mapa fica para uma iteracao futura. */
@Composable
fun DialogoCenariosTrajeto(
    comando: Comando,
    cenarios: List<CenarioTrajeto>,
    historico: List<PontoTrajeto>,
    onCria: (CenarioTrajeto) -> Unit,
    onAtualiza: (CenarioTrajeto) -> Unit,
    onRemove: (String) -> Unit,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var mostraCriacao by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.cenarios_trajeto_titulo, comando.nome),
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
            if (mostraCriacao) {
                CriadorCenarioTrajeto(
                    macComando = comando.mac,
                    historico = historico,
                    onCria = { cenario ->
                        onCria(cenario)
                        mostraCriacao = false
                    },
                    onCancela = { mostraCriacao = false }
                )
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { mostraCriacao = true }) {
                        Text("+ " + stringResource(R.string.novo_cenario_trajeto), color = cores.azul, fontSize = 12.sp)
                    }

                    if (cenarios.isEmpty()) {
                        Text(
                            stringResource(R.string.sem_cenarios_trajeto),
                            color = cores.suave,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    cenarios.forEach { cenario ->
                        LinhaCenarioTrajeto(
                            cenario = cenario,
                            onAlterna = { ativo -> onAtualiza(cenario.copy(ativo = ativo)) },
                            onRemove = { onRemove(cenario.id) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun LinhaCenarioTrajeto(cenario: CenarioTrajeto, onAlterna: (Boolean) -> Unit, onRemove: () -> Unit) {
    val cores = LocalCoresGateway.current
    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(cores.elevado)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cenario.nome, color = cores.tinta, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Switch(checked = cenario.ativo, onCheckedChange = onAlterna, modifier = Modifier.scale(0.7f))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Text("\u00d7", color = cores.avisoTinta, fontSize = 14.sp)
            }
        }
        Text(
            stringResource(R.string.cenario_trajeto_detalhe, cenario.limiarPercentagem, cenario.raioMetros, cenario.template.size),
            color = cores.suave,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        cenario.ultimoDisparoEm?.let { ts ->
            Text(
                stringResource(R.string.ultimo_disparo, formato.format(Date(ts))),
                color = cores.suave,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun CriadorCenarioTrajeto(
    macComando: String,
    historico: List<PontoTrajeto>,
    onCria: (CenarioTrajeto) -> Unit,
    onCancela: () -> Unit
) {
    val cores = LocalCoresGateway.current
    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }

    // viagens mais recentes primeiro, para o utilizador escolher mais
    // facilmente a que acabou de fazer
    val viagens = remember(historico) { GestorSemelhancaTrajeto.separaEmViagens(historico).asReversed() }

    var viagemEscolhida by remember { mutableStateOf<Int?>(if (viagens.isNotEmpty()) 0 else null) }
    var nome by remember { mutableStateOf("") }
    var limiarTexto by remember { mutableStateOf("80") }
    var raioTexto by remember { mutableStateOf("40") }
    var acoes by remember { mutableStateOf(listOf(Acao())) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.escolher_viagem_template), color = cores.tinta, fontSize = 12.sp, fontWeight = FontWeight.Medium)

        if (viagens.isEmpty()) {
            Text(
                stringResource(R.string.sem_viagens_gravadas),
                color = cores.suave,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        viagens.forEachIndexed { indice, viagem ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = viagemEscolhida == indice, onClick = { viagemEscolhida = indice })
                Text(
                    stringResource(
                        R.string.viagem_resumo,
                        formato.format(Date(viagem.first().timestamp)),
                        formato.format(Date(viagem.last().timestamp)),
                        viagem.size
                    ),
                    color = cores.suave,
                    fontSize = 11.sp
                )
            }
        }

        Box(Modifier.padding(top = 10.dp)) {
            CampoTexto(
                rotulo = stringResource(R.string.nome_cenario),
                valor = nome,
                placeholder = stringResource(R.string.nome_cenario_exemplo),
                onValor = { nome = it }
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Box(Modifier.weight(1f)) {
                CampoTexto(
                    rotulo = stringResource(R.string.limiar_semelhanca),
                    valor = limiarTexto,
                    placeholder = "80",
                    onValor = { limiarTexto = it }
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                CampoTexto(
                    rotulo = stringResource(R.string.raio_correspondencia),
                    valor = raioTexto,
                    placeholder = "40",
                    onValor = { raioTexto = it }
                )
            }
        }

        Text(
            stringResource(R.string.acoes_do_cenario),
            color = cores.tinta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 12.dp)
        )
        acoes.forEachIndexed { indice, acao ->
            LinhaAcao(
                a = acao,
                onRemove = { acoes = acoes.toMutableList().apply { removeAt(indice) } },
                onAtualiza = { transforma ->
                    acoes = acoes.toMutableList().apply { this[indice] = transforma(acao) }
                }
            )
        }
        TextButton(onClick = { acoes = acoes + Acao() }, modifier = Modifier.padding(top = 4.dp)) {
            Text("+ " + stringResource(R.string.adicionar_acao), color = cores.azul, fontSize = 11.sp)
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            TextButton(onClick = onCancela) {
                Text(stringResource(R.string.cancelar), color = cores.suave, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = viagemEscolhida != null && nome.isNotBlank(),
                onClick = {
                    val viagem = viagens[viagemEscolhida!!]
                    val template = viagem.map { PontoTemplate(it.latitude, it.longitude) }
                    val limiar = limiarTexto.toIntOrNull()?.coerceIn(1, 100) ?: 80
                    val raio = raioTexto.toIntOrNull()?.coerceAtLeast(1) ?: 40
                    onCria(
                        CenarioTrajeto(
                            id = UUID.randomUUID().toString(),
                            nome = nome,
                            macComando = macComando,
                            template = template,
                            limiarPercentagem = limiar,
                            raioMetros = raio,
                            acoes = acoes.toMutableList()
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.criar_cenario), color = cores.azul, fontSize = 12.sp)
            }
        }
    }
}
