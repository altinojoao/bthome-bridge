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

/* Ecra de gestao de cenarios de trajeto DE UM COMANDO ESPECIFICO --
   o comando a partir do qual o dialogo foi aberto e' o comando
   VIGIADO por todos os cenarios geridos aqui, tal como o alarme de
   alcance ou o modo trajeto ja pertencem a um comando especifico.

   O que E configuravel por cenario e' de ONDE IMPORTAR o template:
   ao criar ou editar, escolhe de qual comando quer usar uma viagem
   ja gravada como forma de referencia -- pode ser o proprio comando
   vigiado (uma viagem antiga dele mesmo) ou QUALQUER outro comando
   com historico. O cenario continua sempre a vigiar e disparar
   sobre o comando onde foi criado, nunca sobre o comando de onde
   importou o template (ver CenarioTrajeto.macOrigemTemplate, so
   informativo).

   O modo de desenhar o template a mao no mapa fica para uma
   iteracao futura. */
@Composable
fun DialogoCenariosTrajeto(
    comandoVigiado: Comando,
    comandosComHistorico: List<Pair<Comando, List<PontoTrajeto>>>,
    cenarios: List<CenarioTrajeto>,
    onCria: (CenarioTrajeto) -> Unit,
    onAtualiza: (CenarioTrajeto) -> Unit,
    onRemove: (String) -> Unit,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var mostraCriacao by remember { mutableStateOf(false) }
    var cenarioEmEdicao by remember { mutableStateOf<CenarioTrajeto?>(null) }

    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.cenarios_trajeto_titulo, comandoVigiado.nome),
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
            if (mostraCriacao || cenarioEmEdicao != null) {
                CriadorOuEditorCenario(
                    comandoVigiado = comandoVigiado,
                    comandosComHistorico = comandosComHistorico,
                    cenarioExistente = cenarioEmEdicao,
                    onGrava = { cenario ->
                        if (cenarioEmEdicao != null) onAtualiza(cenario) else onCria(cenario)
                        mostraCriacao = false
                        cenarioEmEdicao = null
                    },
                    onCancela = {
                        mostraCriacao = false
                        cenarioEmEdicao = null
                    }
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
                        val nomeOrigem = cenario.macOrigemTemplate?.let { mac ->
                            comandosComHistorico.firstOrNull { it.first.mac == mac }?.first?.nome ?: mac
                        }
                        LinhaCenarioTrajeto(
                            cenario = cenario,
                            nomeOrigemTemplate = nomeOrigem,
                            onAlterna = { ativo -> onAtualiza(cenario.copy(ativo = ativo)) },
                            onEditar = { cenarioEmEdicao = cenario },
                            onRemove = { onRemove(cenario.id) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun LinhaCenarioTrajeto(
    cenario: CenarioTrajeto,
    nomeOrigemTemplate: String?,
    onAlterna: (Boolean) -> Unit,
    onEditar: () -> Unit,
    onRemove: () -> Unit
) {
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
        if (nomeOrigemTemplate != null) {
            Text(
                stringResource(R.string.template_importado_de, nomeOrigemTemplate),
                color = cores.suave,
                fontSize = 10.sp
            )
        }
        cenario.ultimoDisparoEm?.let { ts ->
            Text(
                stringResource(R.string.ultimo_disparo, formato.format(Date(ts))),
                color = cores.suave,
                fontSize = 10.sp
            )
        }
        TextButton(onClick = onEditar, modifier = Modifier.padding(top = 2.dp)) {
            Text(stringResource(R.string.editar_cenario), color = cores.azul, fontSize = 10.5.sp)
        }
    }
}

/**
 * Formulario partilhado por criacao e edicao. O comando VIGIADO
 * (comandoVigiado) e' sempre fixo -- e' o comando dentro do qual
 * este dialogo foi aberto, tal como o alarme de alcance ou o modo
 * trajeto pertencem a um comando especifico. O que se escolhe aqui
 * e' de QUAL comando importar a forma do trajeto (o template): pode
 * ser uma viagem do proprio comandoVigiado, ou de qualquer outro
 * comando com historico.
 */
@Composable
private fun CriadorOuEditorCenario(
    comandoVigiado: Comando,
    comandosComHistorico: List<Pair<Comando, List<PontoTrajeto>>>,
    cenarioExistente: CenarioTrajeto?,
    onGrava: (CenarioTrajeto) -> Unit,
    onCancela: () -> Unit
) {
    val cores = LocalCoresGateway.current
    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }

    // indice do comando de ORIGEM DO TEMPLATE dentro de
    // comandosComHistorico -- por omissao, o comando de onde o
    // cenario existente importou o template (se estiver a editar),
    // ou o proprio comandoVigiado (import "de si mesmo"), se ainda
    // tiver historico; senao o primeiro da lista
    var indiceOrigemEscolhido by remember {
        mutableStateOf(
            comandosComHistorico.indexOfFirst {
                it.first.mac == (cenarioExistente?.macOrigemTemplate ?: cenarioExistente?.macComando ?: comandoVigiado.mac)
            }.let { if (it >= 0) it else if (comandosComHistorico.isNotEmpty()) 0 else null }
        )
    }

    val historicoOrigem = indiceOrigemEscolhido?.let { comandosComHistorico.getOrNull(it)?.second } ?: emptyList()
    val viagens = remember(historicoOrigem) { GestorSemelhancaTrajeto.separaEmViagens(historicoOrigem).asReversed() }

    var viagemEscolhida by remember(indiceOrigemEscolhido) { mutableStateOf<Int?>(if (viagens.isNotEmpty()) 0 else null) }
    var nome by remember { mutableStateOf(cenarioExistente?.nome ?: "") }
    var limiarTexto by remember { mutableStateOf((cenarioExistente?.limiarPercentagem ?: 80).toString()) }
    var raioTexto by remember { mutableStateOf((cenarioExistente?.raioMetros ?: 40).toString()) }
    var acoes by remember { mutableStateOf(cenarioExistente?.acoes?.toList() ?: listOf(Acao())) }
    // ao editar, mantem o template original ate o utilizador escolher
    // explicitamente uma viagem nova para o substituir
    var templateOriginalMantido by remember { mutableStateOf(cenarioExistente != null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.cenario_vigia_comando, comandoVigiado.nome),
            color = cores.suave,
            fontSize = 11.sp
        )

        Text(
            stringResource(R.string.importar_template_de),
            color = cores.tinta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp)
        )

        if (comandosComHistorico.isEmpty()) {
            Text(
                stringResource(R.string.sem_viagens_gravadas),
                color = cores.suave,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        comandosComHistorico.forEachIndexed { indice, par ->
            val (comandoOrigem, _) = par
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = indiceOrigemEscolhido == indice,
                    onClick = {
                        // so marca o template como "a substituir" se de
                        // facto mudou a origem -- voltar a tocar na
                        // mesma origem ja selecionada nao deve fazer
                        // perder o template original ao editar
                        if (indiceOrigemEscolhido != indice) {
                            indiceOrigemEscolhido = indice
                            templateOriginalMantido = false
                        }
                    }
                )
                Text(
                    if (comandoOrigem.mac == comandoVigiado.mac) {
                        stringResource(R.string.origem_proprio_comando, comandoOrigem.nome)
                    } else comandoOrigem.nome,
                    color = cores.suave,
                    fontSize = 11.5.sp
                )
            }
        }

        Text(
            stringResource(R.string.escolher_viagem_template),
            color = cores.tinta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (templateOriginalMantido && cenarioExistente != null) {
            Text(
                stringResource(R.string.template_atual_mantido, cenarioExistente.template.size),
                color = cores.suave,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (viagens.isEmpty() && !templateOriginalMantido) {
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
                RadioButton(
                    selected = !templateOriginalMantido && viagemEscolhida == indice,
                    onClick = {
                        viagemEscolhida = indice
                        templateOriginalMantido = false
                    }
                )
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
            val macOrigemEscolhido = indiceOrigemEscolhido?.let { comandosComHistorico.getOrNull(it)?.first?.mac }
            val podeGravar = macOrigemEscolhido != null && nome.isNotBlank() &&
                (templateOriginalMantido || viagemEscolhida != null)
            TextButton(
                enabled = podeGravar,
                onClick = {
                    val template = if (templateOriginalMantido && cenarioExistente != null) {
                        cenarioExistente.template
                    } else {
                        viagens[viagemEscolhida!!].map { PontoTemplate(it.latitude, it.longitude) }
                    }
                    val limiar = limiarTexto.toIntOrNull()?.coerceIn(1, 100) ?: 80
                    val raio = raioTexto.toIntOrNull()?.coerceAtLeast(1) ?: 40
                    // macOrigemTemplate so' e' guardado se a origem for
                    // DIFERENTE do proprio comando vigiado -- importar
                    // de si mesmo nao e' tecnicamente uma importacao
                    val origemParaGuardar = if (macOrigemEscolhido == comandoVigiado.mac) null else macOrigemEscolhido
                    onGrava(
                        CenarioTrajeto(
                            id = cenarioExistente?.id ?: UUID.randomUUID().toString(),
                            nome = nome,
                            macComando = comandoVigiado.mac,
                            template = template,
                            macOrigemTemplate = origemParaGuardar,
                            limiarPercentagem = limiar,
                            raioMetros = raio,
                            ativo = cenarioExistente?.ativo ?: true,
                            acoes = acoes.toMutableList(),
                            ultimoDisparoEm = cenarioExistente?.ultimoDisparoEm
                        )
                    )
                }
            ) {
                Text(
                    if (cenarioExistente != null) stringResource(R.string.guardar_cenario) else stringResource(R.string.criar_cenario),
                    color = cores.azul,
                    fontSize = 12.sp
                )
            }
        }
    }
}
