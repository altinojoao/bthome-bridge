package pt.blugateway.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.ble.Candidato
import pt.blugateway.data.Comando
import pt.blugateway.data.Perfil
import pt.blugateway.net.GestorLocalizacao
import pt.blugateway.ui.theme.LocalCoresGateway

@Composable
fun CartaoComandos(
    comandos: List<Comando>,
    candidatos: List<Candidato>,
    perfis: List<Perfil>,
    confirmaEsquecer: String?,
    ajudaAtiva: Boolean,
    onAlternaAjuda: () -> Unit,
    onPedeEsquecer: (String) -> Unit,
    onConfirmaEsquecer: (String) -> Unit,
    onMudaPerfil: (String, String) -> Unit,
    onAssocia: (String, String) -> Unit,
    onAlternaAlertaAlcance: (String, Boolean) -> Unit,
    onDefineTempoLimiteAlcance: (String, Int) -> Unit,
    onDefineRssiLimiteAlcance: (String, Int) -> Unit,
    onAlternaAgendaSempreAtiva: (String, Boolean) -> Unit,
    onAdicionaPeriodoAgenda: (String, Int, String, String) -> Boolean,
    onRemovePeriodoAgenda: (String, Int, Int) -> Unit,
    onDefineChave: (String, String?) -> Unit,
    onAssociaManual: (String, String, String?) -> Boolean,
    onAlternaIncluirLocalizacao: (String, Boolean) -> Unit,
    onAlternaModoBeaconTrajeto: (String, Boolean) -> Unit,
    onDefineIntervaloBeaconTrajeto: (String, Int) -> Unit
) {
    val cores = LocalCoresGateway.current
    val contexto = LocalContext.current
    var aberto by remember { mutableStateOf(true) }
    var mostraDialogoManual by remember { mutableStateOf(false) }
    var macPendenteLocalizacao by remember { mutableStateOf<String?>(null) }
    // guarda qual dos dois interruptores originou o pedido de
    // permissao em curso -- "localizacao" (incluir localizacao nas
    // acoes) ou "trajeto" (modo beacon trajeto). O resultado do
    // lancador so deve ativar o interruptor que de facto pediu.
    var tipoPendenteLocalizacao by remember { mutableStateOf<String?>(null) }
    var mostraExplicacaoSegundoPlano by remember { mutableStateOf<String?>(null) }

    val lancadorPermissaoLocalizacao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        // so ativa de facto se pelo menos uma das duas permissoes (fina
        // ou aproximada) foi concedida -- se o utilizador recusou as
        // duas, o interruptor desse comando fica desligado
        val mac = macPendenteLocalizacao
        val tipo = tipoPendenteLocalizacao
        if (mac != null && resultados.values.any { it }) {
            when (tipo) {
                "trajeto" -> {
                    onAlternaModoBeaconTrajeto(mac, true)
                    // ativado com sucesso -- se ainda faltar a permissao
                    // de segundo plano, explica e oferece o atalho para
                    // Definicoes (nao bloqueia, so informa)
                    if (!GestorLocalizacao.temPermissaoSegundoPlano(contexto)) {
                        mostraExplicacaoSegundoPlano = mac
                    }
                }
                else -> onAlternaIncluirLocalizacao(mac, true)
            }
        }
        macPendenteLocalizacao = null
        tipoPendenteLocalizacao = null
    }

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
            Modifier
                .fillMaxWidth()
                .clickable { aberto = !aberto }
                .padding(13.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83D\uDCE1", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            BotaoAjuda(ativo = ajudaAtiva, onClick = onAlternaAjuda)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.sec_comandos),
                color = cores.tinta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            val resumo = if (comandos.size == 1) stringResource(R.string.cmd_1)
                         else stringResource(R.string.cmd_n, comandos.size)
            Text(resumo, color = cores.suave, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        BalaoAjuda(
            texto = stringResource(R.string.ajuda_comandos),
            visivel = ajudaAtiva,
            modifier = Modifier.padding(horizontal = 13.dp)
        )

        if (aberto) {
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                if (comandos.isEmpty() && candidatos.isEmpty()) {
                    Text(
                        stringResource(R.string.nenhum_comando),
                        color = cores.suave,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                comandos.forEach { c ->
                    LinhaComando(
                        c, perfis,
                        confirmaEsquecer == c.mac,
                        onPedeEsquecer = { onPedeEsquecer(c.mac) },
                        onConfirma = { onConfirmaEsquecer(c.mac) },
                        onMudaPerfil = { pid -> onMudaPerfil(c.mac, pid) },
                        onAlternaAlertaAlcance = { ativo -> onAlternaAlertaAlcance(c.mac, ativo) },
                        onDefineTempoLimiteAlcance = { seg -> onDefineTempoLimiteAlcance(c.mac, seg) },
                        onDefineRssiLimiteAlcance = { rssi -> onDefineRssiLimiteAlcance(c.mac, rssi) },
                        onAlternaAgendaSempreAtiva = { sa -> onAlternaAgendaSempreAtiva(c.mac, sa) },
                        onAdicionaPeriodoAgenda = { dia, ini, fim -> onAdicionaPeriodoAgenda(c.mac, dia, ini, fim) },
                        onRemovePeriodoAgenda = { dia, idx -> onRemovePeriodoAgenda(c.mac, dia, idx) },
                        onDefineChave = { chave -> onDefineChave(c.mac, chave) },
                        onAlternaIncluirLocalizacao = { incluir -> onAlternaIncluirLocalizacao(c.mac, incluir) },
                        onAlternaModoBeaconTrajeto = { ativo -> onAlternaModoBeaconTrajeto(c.mac, ativo) },
                        onDefineIntervaloBeaconTrajeto = { seg -> onDefineIntervaloBeaconTrajeto(c.mac, seg) },
                        onPedePermissaoLocalizacao = { tipo ->
                            macPendenteLocalizacao = c.mac
                            tipoPendenteLocalizacao = tipo
                            lancadorPermissaoLocalizacao.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onPedeExplicacaoSegundoPlano = { mostraExplicacaoSegundoPlano = c.mac }
                    )
                }

                if (candidatos.isNotEmpty()) {
                    Text(
                        stringResource(R.string.candidatos),
                        color = cores.suave,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    candidatos.forEach { cand ->
                        LinhaCandidato(cand, onAssocia = { onAssocia(cand.mac, cand.nome) })
                    }
                }

                TextButton(
                    onClick = { mostraDialogoManual = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("\u2795 " + stringResource(R.string.associar_manual), color = cores.azul, fontSize = 10.5.sp)
                }
            }
        }
    }

    if (mostraDialogoManual) {
        DialogoAssociarManual(
            onFecha = { mostraDialogoManual = false },
            onAssocia = { mac, nome, chave -> onAssociaManual(mac, nome, chave) }
        )
    }

    mostraExplicacaoSegundoPlano?.let {
        DialogoLocalizacaoSegundoPlano(
            onAbreDefinicoes = {
                GestorLocalizacao.abreDefinicoesApp(contexto)
                mostraExplicacaoSegundoPlano = null
            },
            onFecha = { mostraExplicacaoSegundoPlano = null }
        )
    }
}

/* Explica, apos ativar o modo trajeto por beacon, que sem "Permitir
   sempre" nas Definicoes da app o trajeto so grava pontos com o ecra
   ligado -- puramente informativo, nao bloqueia nada: o utilizador
   pode fechar sem ir a Definicoes e continuar a usar o modo trajeto
   com essa limitacao. */
@Composable
private fun DialogoLocalizacaoSegundoPlano(onAbreDefinicoes: () -> Unit, onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    AlertDialog(
        onDismissRequest = onFecha,
        title = { Text(stringResource(R.string.trajeto_ecra_desligado_titulo)) },
        text = { Text(stringResource(R.string.trajeto_ecra_desligado_texto), color = cores.suave, fontSize = 12.sp) },
        confirmButton = {
            TextButton(onClick = onAbreDefinicoes) { Text(stringResource(R.string.abrir_definicoes)) }
        },
        dismissButton = {
            TextButton(onClick = onFecha) { Text(stringResource(R.string.agora_nao)) }
        }
    )
}

@Composable
private fun LinhaComando(
    c: Comando,
    perfis: List<Perfil>,
    confirmaAtivo: Boolean,
    onPedeEsquecer: () -> Unit,
    onConfirma: () -> Unit,
    onMudaPerfil: (String) -> Unit,
    onAlternaAlertaAlcance: (Boolean) -> Unit,
    onDefineTempoLimiteAlcance: (Int) -> Unit,
    onDefineRssiLimiteAlcance: (Int) -> Unit,
    onAlternaAgendaSempreAtiva: (Boolean) -> Unit,
    onAdicionaPeriodoAgenda: (Int, String, String) -> Boolean,
    onRemovePeriodoAgenda: (Int, Int) -> Unit,
    onDefineChave: (String?) -> Unit,
    onAlternaIncluirLocalizacao: (Boolean) -> Unit,
    onAlternaModoBeaconTrajeto: (Boolean) -> Unit,
    onDefineIntervaloBeaconTrajeto: (Int) -> Unit,
    onPedePermissaoLocalizacao: (String) -> Unit,
    onPedeExplicacaoSegundoPlano: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var menuAberto by remember { mutableStateOf(false) }
    var chaveAberta by remember { mutableStateOf(false) }
    val perfilAtual = perfis.firstOrNull { it.id == c.perfilId }

    // destaque temporario quando uma combinacao acabou de disparar
    // neste comando -- recompoe-se sozinho ao fim da janela, sem
    // precisar de um novo clique para desaparecer
    var agora by remember { mutableStateOf(System.currentTimeMillis()) }
    val emDestaque = c.ultimaCombinacao != null && c.ultimaCombinacaoEm != null &&
        (agora - c.ultimaCombinacaoEm!!) < pt.blugateway.data.Repositorio.TEMPO_DESTAQUE_COMBINACAO_MS

    LaunchedEffect(c.ultimaCombinacaoEm) {
        if (c.ultimaCombinacaoEm != null) {
            while (true) {
                kotlinx.coroutines.delay(500)
                agora = System.currentTimeMillis()
                if (agora - c.ultimaCombinacaoEm!! >= pt.blugateway.data.Repositorio.TEMPO_DESTAQUE_COMBINACAO_MS) break
            }
        }
    }

    val fundo = when {
        c.foraDeAlcance -> cores.avisoFundo
        emDestaque -> cores.okTenue
        else -> cores.elevado
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 7.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(fundo)
            .padding(11.dp, 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.nome, color = cores.tinta, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.mac, color = cores.suave, fontSize = 9.sp)
                    c.rssi?.let {
                        Text("  \u00b7  \uD83D\uDCF6 $it dBm", color = cores.suave, fontSize = 9.sp)
                    }
                    c.bateria?.let {
                        Text("  \u00b7  \uD83D\uDD0B $it%", color = cores.suave, fontSize = 9.sp)
                    }
                }
            }

            Box {
                TextButton(onClick = { menuAberto = true }) {
                    Text("\uD83C\uDFAF " + (perfilAtual?.nome ?: "\u2014"), color = cores.azul, fontSize = 10.5.sp)
                }
                DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                    perfis.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.nome) },
                            onClick = { onMudaPerfil(p.id); menuAberto = false }
                        )
                    }
                }
            }

            if (confirmaAtivo) {
                TextButton(onClick = onConfirma) {
                    Text(stringResource(R.string.esquecer_q), color = cores.avisoTinta, fontSize = 10.sp)
                }
            } else {
                IconButton(onClick = onPedeEsquecer, modifier = Modifier.size(28.dp)) {
                    Text("\u00d7", color = cores.suave, fontSize = 16.sp)
                }
            }
        }

        if (emDestaque) {
            Text(
                "\uD83D\uDD17 ${c.ultimaCombinacao}",
                color = cores.ok,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (c.foraDeAlcance) {
            Text(
                "\uD83D\uDCE1 " + stringResource(R.string.fora_de_alcance),
                color = cores.avisoTinta,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.alertar_fora_alcance),
                color = cores.suave,
                fontSize = 9.5.sp,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Switch(
                    checked = c.alertaAlcance,
                    onCheckedChange = onAlternaAlertaAlcance,
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        if (c.alertaAlcance) {
            var tempoTexto by remember(c.mac) { mutableStateOf((c.tempoLimiteMs / 1000).toString()) }
            var rssiTexto by remember(c.mac) { mutableStateOf(c.rssiLimite.toString()) }

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    CampoTexto(
                        rotulo = stringResource(R.string.tempo_limite),
                        valor = tempoTexto,
                        placeholder = "60",
                        onValor = { novo ->
                            tempoTexto = novo
                            novo.toIntOrNull()?.let { onDefineTempoLimiteAlcance(it) }
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    CampoTexto(
                        rotulo = stringResource(R.string.rssi_limite),
                        valor = rssiTexto,
                        placeholder = "-80",
                        onValor = { novo ->
                            rssiTexto = novo
                            novo.toIntOrNull()?.let { onDefineRssiLimiteAlcance(it) }
                        }
                    )
                }
            }

            BlocoAgenda(
                comando = c,
                onAlternaSempreAtiva = onAlternaAgendaSempreAtiva,
                onAdicionaPeriodo = onAdicionaPeriodoAgenda,
                onRemovePeriodo = onRemovePeriodoAgenda
            )
        }

        val contexto = LocalContext.current

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.incluir_localizacao),
                color = cores.suave,
                fontSize = 9.5.sp,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Switch(
                    checked = c.incluirLocalizacao,
                    onCheckedChange = { ligar ->
                        if (!ligar) {
                            // desligar nunca precisa de permissao
                            onAlternaIncluirLocalizacao(false)
                        } else if (GestorLocalizacao.temPermissao(contexto)) {
                            // permissao ja concedida (ex: em Android <=11, onde
                            // ja foi pedida no arranque para o Bluetooth) --
                            // liga diretamente, sem pedir de novo
                            onAlternaIncluirLocalizacao(true)
                        } else {
                            // primeira vez neste comando: pede a permissao em
                            // runtime, atraves do lancador partilhado do
                            // CartaoComandos pai (ver onPedePermissaoLocalizacao)
                            onPedePermissaoLocalizacao("localizacao")
                        }
                    },
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.modo_beacon_trajeto),
                color = cores.suave,
                fontSize = 9.5.sp,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Switch(
                    checked = c.modoBeaconTrajeto,
                    onCheckedChange = { ligar ->
                        if (!ligar) {
                            onAlternaModoBeaconTrajeto(false)
                        } else if (GestorLocalizacao.temPermissao(contexto)) {
                            onAlternaModoBeaconTrajeto(true)
                            if (!GestorLocalizacao.temPermissaoSegundoPlano(contexto)) {
                                onPedeExplicacaoSegundoPlano()
                            }
                        } else {
                            onPedePermissaoLocalizacao("trajeto")
                        }
                    },
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        if (c.modoBeaconTrajeto) {
            var intervaloTexto by remember(c.mac) { mutableStateOf((c.intervaloBeaconMs / 1000).toString()) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    CampoTexto(
                        rotulo = stringResource(R.string.intervalo_beacon),
                        valor = intervaloTexto,
                        placeholder = "60",
                        onValor = { novo ->
                            intervaloTexto = novo
                            novo.toIntOrNull()?.let { onDefineIntervaloBeaconTrajeto(it) }
                        }
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .then(clickableSemSplash { chaveAberta = !chaveAberta }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "\uD83D\uDD10 " + stringResource(if (c.chaveEncriptacao != null) R.string.chave_definida else R.string.definir_chave),
                color = if (c.chaveEncriptacao != null) cores.ok else cores.suave,
                fontSize = 9.5.sp,
                modifier = Modifier.weight(1f)
            )
        }

        if (chaveAberta) {
            var texto by remember(c.mac) { mutableStateOf(c.chaveEncriptacao ?: "") }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CampoTexto(
                    rotulo = stringResource(R.string.chave_encriptacao_32hex),
                    valor = texto,
                    placeholder = "0123456789abcdef0123456789abcdef",
                    onValor = { texto = it }
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    onDefineChave(texto.ifBlank { null })
                    chaveAberta = false
                }) {
                    Text(stringResource(R.string.guardar_combinacao), color = cores.azul, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun LinhaCandidato(cand: Candidato, onAssocia: () -> Unit) {
    val cores = LocalCoresGateway.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 7.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(cores.elevado)
            .padding(11.dp, 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(cand.nome, color = cores.tinta, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cand.mac, color = cores.suave, fontSize = 9.sp)
                cand.rssi?.let {
                    Text("  \u00b7  \uD83D\uDCF6 $it dBm", color = cores.suave, fontSize = 9.sp)
                }
                cand.bateria?.let {
                    Text("  \u00b7  \uD83D\uDD0B $it%", color = cores.suave, fontSize = 9.sp)
                }
            }
        }
        TextButton(onClick = onAssocia) {
            Text("\u2795 " + stringResource(R.string.adicionar_cmd), color = cores.azul, fontSize = 10.sp)
        }
    }
}
