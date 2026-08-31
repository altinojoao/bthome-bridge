package pt.blugateway

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

        // Animacao de saida personalizada: o icone recua (encolhe) e
        // desvanece, em vez do corte seco por omissao -- e o "ecrã mais
        // elaborado" pedido, sem precisar de video nem imagens extra.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val icone = splashScreenView.iconView
            val escalaX = ObjectAnimator.ofFloat(icone, View.SCALE_X, 1f, 0f)
            val escalaY = ObjectAnimator.ofFloat(icone, View.SCALE_Y, 1f, 0f)
            val alfa = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            escalaX.interpolator = AnticipateInterpolator()
            escalaY.interpolator = AnticipateInterpolator()
            escalaX.duration = 350L
            escalaY.duration = 350L
            alfa.duration = 350L
            alfa.startDelay = 150L

            escalaX.start()
            escalaY.start()
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
