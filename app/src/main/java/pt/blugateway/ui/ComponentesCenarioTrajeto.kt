package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import pt.blugateway.R
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

@Composable
fun LinhaCenarioTrajeto(
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
 * Formulario partilhado por criacao e edicao. Ao contrario da
 * versao anterior, o comando VIGIADO nao vem fixo de fora -- e'
 * escolhido aqui dentro, tal como o comando de ORIGEM DO TEMPLATE
 * (que pode ser o mesmo comando vigiado, ou qualquer outro com
 * historico). O fluxo agora e': primeiro ve-se visualmente TODAS as
 * viagens gravadas (de todos os comandos) sobrepostas num mapa,
 * escolhe-se a viagem certa tocando na linha, e so DEPOIS se
 * escolhe qual comando vai ser vigiado por este cenario.
 */
@Composable
fun CriadorOuEditorCenario(
    comandos: List<Comando>,
    comandosComHistorico: List<Pair<Comando, List<PontoTrajeto>>>,
    cenarioExistente: CenarioTrajeto?,
    comandoVigiadoInicial: Comando?,
    onGrava: (CenarioTrajeto) -> Unit,
    onCancela: () -> Unit
) {
    val cores = LocalCoresGateway.current

    // Viagem escolhida no mapa -- guarda o objeto completo (nao so o
    // id) porque e' dele que se retira o template a gravar.
    var viagemEscolhida by remember { mutableStateOf<ViagemSelecionavel?>(null) }
    var templateOriginalMantido by remember { mutableStateOf(cenarioExistente != null) }

    // Comando VIGIADO -- por omissao o da edicao existente, ou o
    // indicado ao abrir o formulario (ex: a partir do card de um
    // comando especifico), ou nenhum (obriga a escolher).
    var comandoVigiadoEscolhido by remember {
        mutableStateOf(
            comandoVigiadoInicial
                ?: comandos.firstOrNull { it.mac == cenarioExistente?.macComando }
        )
    }

    var nome by remember { mutableStateOf(cenarioExistente?.nome ?: "") }
    var limiarTexto by remember { mutableStateOf((cenarioExistente?.limiarPercentagem ?: 80).toString()) }
    var raioTexto by remember { mutableStateOf((cenarioExistente?.raioMetros ?: 40).toString()) }
    var acoes by remember { mutableStateOf(cenarioExistente?.acoes?.toList() ?: listOf(Acao())) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.escolher_viagem_no_mapa),
            color = cores.tinta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        if (templateOriginalMantido && cenarioExistente != null) {
            Text(
                stringResource(R.string.template_atual_mantido, cenarioExistente.template.size),
                color = cores.suave,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (comandosComHistorico.isEmpty()) {
            pt.blugateway.ui.theme.TextoEstadoVazio(
                stringResource(R.string.sem_viagens_gravadas_geral),
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            SeletorTrajetoMapa(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(9.dp)),
                comandosComHistorico = comandosComHistorico,
                viagemSelecionadaId = viagemEscolhida?.id,
                onSelecionaViagem = { viagem ->
                    viagemEscolhida = viagem
                    templateOriginalMantido = false
                }
            )

            val viagemAtual = viagemEscolhida
            if (viagemAtual != null) {
                Text(
                    stringResource(
                        R.string.viagem_selecionada_de,
                        viagemAtual.comandoOrigem.nome,
                        viagemAtual.pontos.size
                    ),
                    color = cores.suave,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (!templateOriginalMantido) {
                Text(
                    stringResource(R.string.toca_no_mapa_escolher_viagem),
                    color = cores.suave,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Text(
            stringResource(R.string.escolher_comando_vigiado),
            color = cores.tinta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp)
        )
        comandos.forEach { comando ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = comandoVigiadoEscolhido?.mac == comando.mac,
                    onClick = { comandoVigiadoEscolhido = comando }
                )
                Text(comando.nome, color = cores.suave, fontSize = 11.5.sp)
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
            val comandoVigiadoFinal = comandoVigiadoEscolhido
            val podeGravar = comandoVigiadoFinal != null && nome.isNotBlank() &&
                (templateOriginalMantido || viagemEscolhida != null)
            TextButton(
                enabled = podeGravar,
                onClick = {
                    val viagem = viagemEscolhida
                    val template = if (templateOriginalMantido && cenarioExistente != null) {
                        cenarioExistente.template
                    } else {
                        viagem!!.pontos.sortedBy { it.timestamp }.map { PontoTemplate(it.latitude, it.longitude) }
                    }
                    val limiar = limiarTexto.toIntOrNull()?.coerceIn(1, 100) ?: 80
                    val raio = raioTexto.toIntOrNull()?.coerceAtLeast(1) ?: 40
                    // macOrigemTemplate so' e' guardado se a origem for
                    // DIFERENTE do comando vigiado -- importar de si
                    // mesmo nao e' tecnicamente uma importacao
                    val macOrigem = viagem?.comandoOrigem?.mac
                        ?: cenarioExistente?.macOrigemTemplate
                        ?: cenarioExistente?.macComando
                    val origemParaGuardar = if (macOrigem == comandoVigiadoFinal!!.mac) null else macOrigem
                    onGrava(
                        CenarioTrajeto(
                            id = cenarioExistente?.id ?: UUID.randomUUID().toString(),
                            nome = nome,
                            macComando = comandoVigiadoFinal.mac,
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
