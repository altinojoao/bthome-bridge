package pt.blugateway.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.blugateway.ui.theme.LocalCoresGateway
import pt.blugateway.ui.theme.TemaGateway
import java.util.Locale

/* Identifica qual balao de ajuda esta aberto neste momento -- so um
   de cada vez, tal como na interface HTML (balaoAjudaAberto). */
private enum class BalaoAtivo { NENHUM, ESCUTA, DIAGNOSTICO, COMANDOS, REGISTO }

@Composable
fun EcraPrincipal(vm: GatewayViewModel = viewModel()) {
    val comandos by vm.comandos.collectAsState()
    val perfis by vm.perfis.collectAsState()
    val conta by vm.conta.collectAsState()
    val candidatos by vm.candidatos.collectAsState()
    val registo by vm.registo.collectAsState()
    val diagnostico by vm.diagnostico.collectAsState()
    val perfilAtivoId by vm.perfilAtivoId.collectAsState()
    val emparelhamentoAberto by vm.emparelhamentoAberto.collectAsState()
    val notacaoPontos by vm.notacaoPontos.collectAsState()
    val confirmaApagarPerfil by vm.confirmaApagarPerfil.collectAsState()
    val confirmaEsquecerComando by vm.confirmaEsquecerComando.collectAsState()
    val idiomaEscolhido by vm.idioma.collectAsState()
    val temaClaro by vm.temaClaro.collectAsState()
    val somAtivo by vm.somAtivo.collectAsState()
    val menuPerfisEspeciais by vm.menuPerfisEspeciais.collectAsState()
    val construtorAberto by vm.construtorAberto.collectAsState()
    val construtorSequencia by vm.construtorSequencia.collectAsState()
    val cardsDesativados by vm.cardsDesativados.collectAsState()

    var mostraConfiguracao by remember { mutableStateOf(false) }
    var mostraScan by remember { mutableStateOf(false) }
    var mostraSeletorIdioma by remember { mutableStateOf(false) }
    var mostraCardsVisiveis by remember { mutableStateOf(false) }
    var balaoAtivo by remember { mutableStateOf(BalaoAtivo.NENHUM) }

    val contextoBase = LocalContext.current
    val idiomaAtual = idiomaEscolhido ?: Locale.getDefault().language

    // Aplica o idioma escolhido substituindo o Context por um com a
    // Configuration alterada -- stringResource() dentro deste bloco
    // le a partir do Context fornecido em LocalContext, por isso e
    // este que tem de mudar, nao uma variavel Compose isolada.
    val contextoLocalizado = remember(idiomaAtual) {
        val locale = Locale(idiomaAtual)
        val config = android.content.res.Configuration(contextoBase.resources.configuration)
        config.setLocale(locale)
        contextoBase.createConfigurationContext(config)
    }

    // O Context localizado acima NAO e a Activity (e um wrapper de
    // configuracao), por isso deixa de implementar as interfaces que a
    // ComponentActivity fornece -- ActivityResultRegistryOwner,
    // OnBackPressedDispatcherOwner, LifecycleOwner. Sem fornecer estes
    // explicitamente, qualquer rememberLauncherForActivityResult() (ex:
    // o pedido de permissao de localizacao) dentro desta arvore rebenta
    // com "No ActivityResultRegistryOwner was provided", porque esses
    // CompositionLocal tentam inferir o owner a partir do LocalContext
    // atual, que agora e so o wrapper de configuracao. Capturam-se os
    // valores REAIS (da Activity) aqui, antes da troca, e fornecem-se
    // ao lado do novo LocalContext.
    val registryOwnerReal = requireNotNull(LocalActivityResultRegistryOwner.current) {
        "EcraPrincipal so deve correr dentro de uma ComponentActivity"
    }
    val dispatcherOwnerReal = requireNotNull(LocalOnBackPressedDispatcherOwner.current) {
        "EcraPrincipal so deve correr dentro de uma ComponentActivity"
    }
    val lifecycleOwnerReal = LocalLifecycleOwner.current

    androidx.compose.runtime.CompositionLocalProvider(
        LocalContext provides contextoLocalizado,
        LocalActivityResultRegistryOwner provides registryOwnerReal,
        LocalOnBackPressedDispatcherOwner provides dispatcherOwnerReal,
        LocalLifecycleOwner provides lifecycleOwnerReal
    ) {
        TemaGateway(temaClaro = temaClaro) {
            val cores = LocalCoresGateway.current
            val perfilAtivo = vm.acharPerfil(perfilAtivoId)

            Column(Modifier.fillMaxSize().background(cores.fundo)) {
                BarraTopo(
                    configAberto = mostraConfiguracao,
                    temaClaro = temaClaro,
                    somAtivo = somAtivo,
                    modoEspecialAtivo = perfilAtivo?.modoCombinacao == true,
                    onAlternaConfig = { mostraConfiguracao = !mostraConfiguracao },
                    onAlternaModoEspecial = { vm.alternaModoEspecialTopo() },
                    onAlternaTema = { vm.trocaTema() },
                    onAlternaSom = { vm.trocaSom() },
                    onEscolheIdioma = { mostraSeletorIdioma = true },
                    onAbreCardsVisiveis = { mostraCardsVisiveis = true }
                )

                if ("hero" !in cardsDesativados) {
                    CartaoHero(
                        comandos = comandos,
                        emparelhamentoAberto = emparelhamentoAberto,
                        ajudaAtiva = balaoAtivo == BalaoAtivo.ESCUTA,
                        onAlternaAjuda = {
                            balaoAtivo = if (balaoAtivo == BalaoAtivo.ESCUTA) BalaoAtivo.NENHUM else BalaoAtivo.ESCUTA
                        },
                        onDetetar = { mostraScan = true; vm.alternaDetecao() }
                    )
                }

                if ("diag" !in cardsDesativados) {
                    CartaoDiagnostico(
                        diagnostico = diagnostico,
                        ajudaAtiva = balaoAtivo == BalaoAtivo.DIAGNOSTICO,
                        onAlternaAjuda = {
                            balaoAtivo = if (balaoAtivo == BalaoAtivo.DIAGNOSTICO) BalaoAtivo.NENHUM else BalaoAtivo.DIAGNOSTICO
                        }
                    )
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if ("comandos" !in cardsDesativados) {
                        CartaoComandos(
                            comandos = comandos,
                            candidatos = candidatos,
                            perfis = perfis,
                            confirmaEsquecer = confirmaEsquecerComando,
                            ajudaAtiva = balaoAtivo == BalaoAtivo.COMANDOS,
                            onAlternaAjuda = {
                                balaoAtivo = if (balaoAtivo == BalaoAtivo.COMANDOS) BalaoAtivo.NENHUM else BalaoAtivo.COMANDOS
                            },
                            onPedeEsquecer = vm::pedeEsquecer,
                            onConfirmaEsquecer = vm::confirmaEsquecer,
                            onMudaPerfil = vm::mudaPerfilComando,
                            onAssocia = vm::associaCandidato,
                            onAlternaAlertaAlcance = vm::alternaAlertaAlcance,
                            onDefineTempoLimiteAlcance = vm::defineTempoLimiteAlcance,
                            onDefineRssiLimiteAlcance = vm::defineRssiLimiteAlcance,
                            onAlternaAgendaSempreAtiva = vm::alternaAgendaSempreAtiva,
                            onAdicionaPeriodoAgenda = vm::adicionaPeriodoAgenda,
                            onRemovePeriodoAgenda = vm::removePeriodoAgenda,
                            onDefineChave = vm::defineChaveEncriptacao,
                            onAssociaManual = vm::associaComandoManual,
                            onAlternaIncluirLocalizacao = vm::defineIncluirLocalizacao
                        )
                    }

                    if ("reg" !in cardsDesativados) {
                        CartaoRegisto(
                            linhas = registo,
                            ajudaAtiva = balaoAtivo == BalaoAtivo.REGISTO,
                            onAlternaAjuda = {
                                balaoAtivo = if (balaoAtivo == BalaoAtivo.REGISTO) BalaoAtivo.NENHUM else BalaoAtivo.REGISTO
                            }
                        )
                    }
                }
            }

            if (mostraConfiguracao && "configPainel" !in cardsDesativados) {
                DialogoConfiguracao(onFecha = { mostraConfiguracao = false }) {
                    CartaoConta(conta = conta, onGuarda = vm::guardaConta)

                    CartaoPerfis(
                        perfis = perfis,
                        comandos = comandos,
                        perfilAtivoId = perfilAtivoId,
                        confirmaApagar = confirmaApagarPerfil,
                        onAbre = vm::abrePerfil,
                        onRenomeia = vm::renomeiaPerfil,
                        onPedeApagar = vm::pedeApagarPerfil,
                        onConfirmaApagar = vm::confirmaApagar,
                        onNovoPerfil = { vm.novoPerfil("Novo perfil") }
                    )

                    if (perfilAtivo != null) {
                        CartaoPerfilAtivo(
                            perfil = perfilAtivo,
                            notacaoPontos = notacaoPontos,
                            onTrocaNotacao = vm::trocaNotacao,
                            onFecha = vm::fechaPerfil,
                            onAdicionaAcao = { indice -> vm.adicionaAcao(perfilAtivo.id, indice) },
                            onRemoveAcao = { indice, j -> vm.removeAcao(perfilAtivo.id, indice, j) },
                            onAtualizaAcao = { indice, j, transformacao ->
                                vm.atualizaAcao(perfilAtivo.id, indice, j, transformacao)
                            }
                        )

                        CartaoCombinacoes(
                            perfil = perfilAtivo,
                            notacaoPontos = notacaoPontos,
                            construtorAberto = construtorAberto,
                            construtorSequencia = construtorSequencia,
                            onAlternaModo = { ligado -> vm.alternaModoCombinacao(perfilAtivo.id, ligado) },
                            onAlteraJanela = { seg -> vm.defineJanelaCombinacao(perfilAtivo.id, seg) },
                            onAbreConstrutor = { vm.abreConstrutorCombinacao() },
                            onFechaConstrutor = { vm.fechaConstrutorCombinacao() },
                            onAdicionaAoConstrutor = { idx -> vm.adicionaAoConstrutor(idx) },
                            onLimpaConstrutor = { vm.limpaConstrutor() },
                            onApagaUltimoConstrutor = { vm.apagaUltimoDoConstrutor() },
                            onGuardaCombinacao = { nome -> vm.guardaCombinacao(perfilAtivo.id, nome) },
                            onApagaCombinacao = { id -> vm.apagaCombinacao(perfilAtivo.id, id) },
                            onAdicionaAcao = { combId -> vm.adicionaAcaoCombinacao(perfilAtivo.id, combId) },
                            onRemoveAcao = { combId, j -> vm.removeAcaoCombinacao(perfilAtivo.id, combId, j) },
                            onAtualizaAcao = { combId, j, t -> vm.atualizaAcaoCombinacao(perfilAtivo.id, combId, j, t) }
                        )
                    }
                }
            }

            if (mostraScan) {
                DialogoScan(
                    emparelhamentoAberto = emparelhamentoAberto,
                    candidatos = candidatos,
                    onDetetar = { vm.alternaDetecao() },
                    onAssocia = { mac, nome -> vm.associaCandidato(mac, nome) },
                    onFecha = { mostraScan = false }
                )
            }

            if (mostraSeletorIdioma) {
                DialogoIdioma(
                    idiomaAtual = idiomaAtual,
                    onEscolhe = { cod -> vm.escolheIdioma(cod); mostraSeletorIdioma = false },
                    onFecha = { mostraSeletorIdioma = false }
                )
            }

            menuPerfisEspeciais?.let { lista ->
                DialogoPerfisEspeciais(
                    perfis = lista,
                    onEscolhe = { id -> vm.escolhePerfilEspecial(id); mostraConfiguracao = true },
                    onFecha = { vm.fechaMenuPerfisEspeciais() }
                )
            }

            if (mostraCardsVisiveis) {
                DialogoCardsVisiveis(
                    cardsDesativados = cardsDesativados,
                    onAlternaCard = vm::defineCardAtivo,
                    onFecha = { mostraCardsVisiveis = false }
                )
            }
        }
    }
}
