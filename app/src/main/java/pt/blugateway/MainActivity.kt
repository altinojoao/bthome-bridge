package pt.blugateway

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import pt.blugateway.ble.ServicoGateway
import pt.blugateway.ui.EcraPrincipal
import pt.blugateway.ui.theme.LocalCoresGateway
import pt.blugateway.ui.theme.TemaGateway

class MainActivity : ComponentActivity() {

    private fun permissoesNecessarias(): Array<String> {
        val lista = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lista.add(Manifest.permission.BLUETOOTH_SCAN)
            lista.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            lista.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return lista.toTypedArray()
    }

    private fun todasConcedidas(): Boolean = permissoesNecessarias().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Arranca o serviço em primeiro plano, que por sua vez inicia a
     *  escuta BLE. Chamado a partir de uma Activity visível, tal
     *  como o Android exige a partir da versão 12. */
    private fun arrancaServico() {
        val intent = Intent(this, ServicoGateway::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // instalar ANTES de super.onCreate(), como a API exige
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Animacao de saida: so um fade sobre splashScreenView.view (a
        // vista completa do splash) -- NUNCA usar splashScreenView.iconView
        // aqui. A biblioteca de compatibilidade (androidx.core.splashscreen)
        // exige que o drawable de windowSplashScreenAnimatedIcon siga a
        // especificacao de tamanho de "AVD" (432dp, 4x um icone adaptativo
        // normal de 108dp) para conseguir construir essa View com
        // seguranca; um drawable mais pequeno (o nosso, pensado para
        // lançador) faz a biblioteca rebentar interNAMENTE com
        // NullPointerException ao tentar obter iconView, ANTES mesmo do
        // nosso codigo correr -- e um bug conhecido da propria lib nesse
        // cenario, nao um erro de uso desta API. view.view continua
        // seguro porque nao depende dessa construcao especifica.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val alfa = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            alfa.duration = 300L
            alfa.start()
            alfa.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenView.remove()
                }
            })
        }

        val pedirPermissoes = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { resultados ->
            if (resultados.values.all { it }) {
                arrancaServico()
            }
        }

        setContent {
            val diagPrefs = getSharedPreferences("blugateway_diagnostico", MODE_PRIVATE)
            val ultimoCrash = diagPrefs.getString("ultimo_crash", null)

            if (ultimoCrash != null) {
                // Mostra o erro do crash anterior em vez do ecra normal,
                // ate o utilizador confirmar que leu (ver GatewayApp).
                TemaGateway(temaClaro = false) {
                    EcraDiagnosticoCrash(
                        erro = ultimoCrash,
                        onLimpar = { diagPrefs.edit().remove("ultimo_crash").apply() }
                    )
                }
                return@setContent
            }

            var concedidas by remember { mutableStateOf(todasConcedidas()) }

            if (concedidas) {
                EcraPrincipal()
            } else {
                TemaGateway(temaClaro = false) {
                    EcraPermissoes(
                        onPedir = {
                            pedirPermissoes.launch(permissoesNecessarias())
                            // reavalia otimisticamente; se o utilizador recusar,
                            // o launcher acima trata do resultado real
                            concedidas = todasConcedidas()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // se as permissões foram concedidas nas Definições do sistema
        // (fora do fluxo do launcher), o servico arranca ao voltar à app
        if (todasConcedidas()) {
            arrancaServico()
        }
    }
}

@androidx.compose.runtime.Composable
private fun EcraPermissoes(onPedir: () -> Unit) {
    val cores = LocalCoresGateway.current
    Column(
        Modifier.fillMaxSize().background(cores.fundo).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.perm_titulo),
            color = cores.tinta,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "\u2022 " + stringResource(R.string.perm_bluetooth),
            color = cores.suave, fontSize = 13.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "\u2022 " + stringResource(R.string.perm_notificacoes),
            color = cores.suave, fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPedir) {
            Text(stringResource(R.string.perm_pedir))
        }
    }
}

/* Mostra o texto completo do ultimo crash nao tratado, com um botao
   para copiar para a area de transferencia e outro para limpar e
   voltar ao ecra normal (ver GatewayApp.onCreate). */
@androidx.compose.runtime.Composable
private fun EcraDiagnosticoCrash(erro: String, onLimpar: () -> Unit) {
    val cores = LocalCoresGateway.current
    val contexto = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(cores.fundo)
            .padding(16.dp)
    ) {
        Text(
            "A app fechou-se sozinha da ultima vez",
            color = cores.tinta,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Copie o texto abaixo e envie-o.",
            color = cores.suave,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(cores.elevado)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                erro,
                color = cores.tinta,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = {
                val cm = contexto.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", erro))
            }) {
                Text("Copiar")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onLimpar) {
                Text("Continuar")
            }
        }
    }
}
