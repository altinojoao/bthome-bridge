package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.blugateway.R
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.data.Comando
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Ecra proprio para gerir TODOS os cenarios de trajeto, de TODOS os
 * comandos -- substitui o antigo fluxo em que "Cenarios" era um
 * botao dentro do card de cada comando no mapa (ver EcraMapa.kt,
 * historico anterior a esta versao). Os cenarios continuam a
 * pertencer a um unico comando vigiado (CenarioTrajeto.macComando
 * nao mudou), mas a apresentacao agora e' centralizada: uma lista
 * agrupada por comando, cada cenario com o seu proprio card.
 *
 * Ao criar um cenario novo, o utilizador ve-se primeiro com um mapa
 * de TODAS as viagens gravadas de TODOS os comandos sobrepostas
 * (SeletorTrajetoMapa), escolhe visualmente a viagem que quer usar
 * como template, e so depois escolhe qual comando vai ser vigiado.
 * Isto inverte a ordem do fluxo antigo (que exigia estar dentro do
 * comando vigiado para sequer abrir o formulario).
 *
 * Usa Dialog() fullscreen, como EcraMapa -- consistente com o resto
 * da app para ecras que abrem por cima de tudo.
 */
@Composable
fun EcraCenarios(
    comandos: List<Comando>,
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
                        stringResource(R.string.ecra_cenarios_titulo),
                        color = cores.tinta,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                if (mostraCriacao || cenarioEmEdicao != null) {
                    val cenario = cenarioEmEdicao
                    CriadorOuEditorCenario(
                        comandos = comandos,
                        comandosComHistorico = comandosComHistorico,
                        cenarioExistente = cenario,
                        comandoVigiadoInicial = cenario?.let { c -> comandos.firstOrNull { it.mac == c.macComando } },
                        onGrava = { novoCenario ->
                            if (cenario != null) onAtualiza(novoCenario) else onCria(novoCenario)
                            mostraCriacao = false
                            cenarioEmEdicao = null
                        },
                        onCancela = {
                            mostraCriacao = false
                            cenarioEmEdicao = null
                        }
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        TextButton(onClick = { mostraCriacao = true }) {
                            Text("+ " + stringResource(R.string.novo_cenario_trajeto), color = cores.azul, fontSize = 12.sp)
                        }

                        if (cenarios.isEmpty()) {
                            Text(
                                stringResource(R.string.sem_cenarios_nenhum_comando),
                                color = cores.suave,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // Agrupados por comando vigiado, na mesma ordem
                        // em que os comandos aparecem na lista principal
                        // -- comandos sem nenhum cenario ainda nao
                        // aparecem (nada para mostrar).
                        comandos.forEach { comando ->
                            val cenariosDoComando = cenarios.filter { it.macComando == comando.mac }
                            if (cenariosDoComando.isEmpty()) return@forEach

                            Text(
                                comando.nome,
                                color = cores.tinta,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp)
                            )

                            cenariosDoComando.forEach { cenario ->
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
            }
        }
    }
}
