package pt.blugateway.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Marcas conhecidas por gestao de bateria mais agressiva que o
 * Android padrao -- ate isentar a app da otimizacao padrao (o botao
 * abaixo), estas continuam a poder suspender a app periodicamente em
 * segundo plano (a app nao esta na Play Store, por isso este pedido
 * direto e apropriado aqui -- ver comentario em
 * ACAO_IGNORAR_OTIMIZACAO abaixo). Lista nao exaustiva, so as mais
 * conhecidas/documentadas em dontkillmyapp.com.
 */
private val MARCAS_AGRESSIVAS = setOf("samsung", "xiaomi", "huawei", "oneplus", "oppo", "vivo", "realme", "meizu", "asus")

private fun ehMarcaAgressiva(): Boolean {
    return android.os.Build.MANUFACTURER.lowercase() in MARCAS_AGRESSIVAS
}

private fun estaIsentaDeOtimizacao(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Pede diretamente a isencao da otimizacao de bateria padrao do
 * Android (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), em vez de
 * so abrir as Definicoes -- a Google restringe este pedido direto a
 * apps com necessidade genuina de execucao continua em segundo
 * plano (o que esta app tem: deteccao de anuncios Bluetooth) e a
 * apps fora da Play Store esta restricao nao se aplica de todo, ja
 * que nao ha revisao da Play Store a fazer. NAO resolve sozinho as
 * camadas extra de gestao de bateria de fabricantes como Samsung,
 * Xiaomi, OnePlus (essas exigem configuracao manual adicional, fora
 * do alcance de qualquer API do Android -- ver aviso mostrado
 * quando ehMarcaAgressiva()).
 */
private fun pedeIsencaoOtimizacao(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun CartaoBateria() {
    val cores = LocalCoresGateway.current
    val contexto = LocalContext.current
    var isenta by remember { mutableStateOf(estaIsentaDeOtimizacao(contexto)) }

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
            Text("\uD83D\uDD0B", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.bateria_titulo),
                color = cores.tinta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            stringResource(R.string.bateria_texto),
            color = cores.suave,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (isenta) {
            Text(
                "\u2713 " + stringResource(R.string.bateria_ja_isenta),
                color = cores.ok,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            TextButton(
                onClick = {
                    pedeIsencaoOtimizacao(contexto)
                    // o resultado real so se sabe ao voltar a app --
                    // reavalia otimisticamente, sem lancador dedicado
                    // (nao ha valor pratico em bloquear a UI a espera,
                    // o utilizador ve o proprio estado do sistema)
                    isenta = estaIsentaDeOtimizacao(contexto)
                },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(stringResource(R.string.bateria_pedir_isencao), color = cores.azul, fontSize = 11.5.sp)
            }
        }

        if (ehMarcaAgressiva()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(cores.avisoFundo)
                    .padding(10.dp)
            ) {
                Text(
                    stringResource(R.string.bateria_aviso_marca, android.os.Build.MANUFACTURER),
                    color = cores.avisoTinta,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }

        var mostraDiagnostico by remember { mutableStateOf(false) }
        TextButton(onClick = { mostraDiagnostico = true }, modifier = Modifier.padding(top = 4.dp)) {
            Text("Ver diagnóstico do trajeto (temporário)", color = cores.suave, fontSize = 10.sp)
        }
        if (mostraDiagnostico) {
            DialogoDiagnosticoTrajeto(onFecha = { mostraDiagnostico = false })
        }
    }
}
