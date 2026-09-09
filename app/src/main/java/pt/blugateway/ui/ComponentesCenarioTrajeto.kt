package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    onSimular: () -> Unit,
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
            // Botao simular -- abre DialogoSimulacaoCenario
            TextButton(onClick = onSimular, modifier = Modifier.padding(end = 2.dp)) {
                Text("\u25B6 " + stringResource(R.string.sim_botao), color = cores.azul, fontSize = 10.sp)
            }
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
    cenarioExistente: CenarioTrajeto?,
    comandoVigiadoInicial: Comando?,
    onGrava: (CenarioTrajeto) -> Unit,
    onCancela: () -> Unit
) {
    val cores = LocalCoresGateway.current

    // Pausa a gravacao de trajeto em fundo (beacon e clique)
    // enquanto este formulario estiver aberto -- retomada
    // automaticamente ao fechar (onDispose corre sempre, mesmo se o
    // ecra for fechado de forma inesperada). Evita que pontos novos
    // gravados durante a escolha do template se misturem com o
    // historico que o utilizador esta a rever nesse momento.
    DisposableEffect(Unit) {
        pt.blugateway.ble.GestorTrajeto.pausaGravacao()
        onDispose {
            pt.blugateway.ble.GestorTrajeto.retomaGravacao()
        }
    }

    var templateOriginalMantido by remember { mutableStateOf(cenarioExistente != null) }
    // template -- lista de pontos
    // lat/lon pura, sem PontoTrajeto (nao vem de nenhum comando).
    // Inicializado com o template ja gravado quando se esta a EDITAR
    // um cenario, para o mapa mostrar o que estava definido em vez
    // de abrir vazio.
    var templateDesenhado by remember {
        mutableStateOf<List<PontoTemplate>?>(cenarioExistente?.template?.toList())
    }
    // pontos onde o utilizador tocou ao definir a rota -- distintos
    // dos extremos da geometria OSRM (que podem estar na estrada mais
    // proxima, nao no ponto exato do toque). Inicializados a partir
    // do cenario existente ao editar.
    var origemRotaExata by remember {
        mutableStateOf(
            if (cenarioExistente?.origemExataLat != null && cenarioExistente.origemExataLon != null)
                PontoTemplate(cenarioExistente.origemExataLat!!, cenarioExistente.origemExataLon!!)
            else null
        )
    }
    var destinoRotaExato by remember {
        mutableStateOf(
            if (cenarioExistente?.destinoExatoLat != null && cenarioExistente.destinoExatoLon != null)
                PontoTemplate(cenarioExistente.destinoExatoLat!!, cenarioExistente.destinoExatoLon!!)
            else null
        )
    }

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
    // MACs adicionais selecionados (checkboxes) -- qualquer beacon
    // aqui tambem contribui com pontos GPS para o historio do cenario
    var macsAdicionais by remember {
        mutableStateOf(cenarioExistente?.macsAdicionais?.toSet() ?: emptySet<String>())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MapaRotaTrajeto(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(9.dp)),
            templateExistente = templateDesenhado ?: emptyList(),
            origemExistente = origemRotaExata,
            destinoExistente = destinoRotaExato,
            onRotaEscolhida = { rota ->
                templateDesenhado = rota.pontos
                origemRotaExata = rota.origemExata
                destinoRotaExato = rota.destinoExato
                templateOriginalMantido = false
            }
        )

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
                    onClick = {
                        // ao mudar o principal, remove-o dos adicionais
                        // (um beacon nao pode ser principal e adicional)
                        macsAdicionais = macsAdicionais - comando.mac
                        comandoVigiadoEscolhido = comando
                    }
                )
                Text(comando.nome, color = cores.suave, fontSize = 11.5.sp)
            }
        }

        // Beacons adicionais -- todos os beacons exceto o principal
        // podem ser adicionados como fontes extra de pontos GPS
        val beaconsDisponiveis = comandos.filter { it.mac != comandoVigiadoEscolhido?.mac }
        if (beaconsDisponiveis.isNotEmpty()) {
            Text(
                stringResource(R.string.beacons_adicionais),
                color = cores.tinta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(R.string.beacons_adicionais_dica),
                color = cores.suave,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
            beaconsDisponiveis.forEach { beacon ->
                val selecionado = beacon.mac in macsAdicionais
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selecionado,
                        onCheckedChange = { ativo ->
                            macsAdicionais = if (ativo) macsAdicionais + beacon.mac
                            else macsAdicionais - beacon.mac
                        }
                    )
                    Text(beacon.nome, color = cores.suave, fontSize = 11.5.sp)
                }
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
            // ha template pronto se: o original foi mantido, ou (no
            // modo viagem gravada) uma viagem foi escolhida, ou (nos
            // modos desenhar/rota) ha pelo menos 2 pontos capturados
            // -- um trajeto de 1 ponto nao tem forma nenhuma para
            // comparar semelhanca.
            val temTemplatePronto = templateOriginalMantido || (templateDesenhado?.size ?: 0) >= 2
            val podeGravar = comandoVigiadoFinal != null && nome.isNotBlank() && temTemplatePronto
            TextButton(
                enabled = podeGravar,
                onClick = {
                    val template = if (templateOriginalMantido && cenarioExistente != null) {
                        cenarioExistente.template
                    } else {
                        templateDesenhado!!
                    }
                    val limiar = limiarTexto.toIntOrNull()?.coerceIn(1, 100) ?: 80
                    val raio = raioTexto.toIntOrNull()?.coerceAtLeast(1) ?: 40
                    val origemParaGuardar: String? = null
                    onGrava(
                        CenarioTrajeto(
                            id = cenarioExistente?.id ?: UUID.randomUUID().toString(),
                            nome = nome,
                            macComando = comandoVigiadoFinal!!.mac,
                            macsAdicionais = macsAdicionais.filter { it != comandoVigiadoFinal.mac },
                            template = template,
                            macOrigemTemplate = origemParaGuardar,
                            limiarPercentagem = limiar,
                            raioMetros = raio,
                            ativo = cenarioExistente?.ativo ?: true,
                            acoes = acoes.toMutableList(),
                            modoTemplate = "rota",
                            origemExataLat = origemRotaExata?.lat ?: cenarioExistente?.origemExataLat,
                            origemExataLon = origemRotaExata?.lon ?: cenarioExistente?.origemExataLon,
                            destinoExatoLat = destinoRotaExato?.lat ?: cenarioExistente?.destinoExatoLat,
                            destinoExatoLon = destinoRotaExato?.lon ?: cenarioExistente?.destinoExatoLon,
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
