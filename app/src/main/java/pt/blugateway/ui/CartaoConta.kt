package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.data.ContaShelly
import pt.blugateway.ui.theme.LocalCoresGateway

@Composable
fun CartaoConta(conta: ContaShelly, onGuarda: (ContaShelly) -> Unit) {
    val cores = LocalCoresGateway.current
    var aberto by remember { mutableStateOf(false) }
    var chaveVisivel by remember { mutableStateOf(false) }
    var numServidor by remember(conta) { mutableStateOf(conta.servidorNum) }
    var regiao by remember(conta) { mutableStateOf(conta.regiao) }
    var authKey by remember(conta) { mutableStateOf(conta.authKey) }

    fun persiste() {
        onGuarda(ContaShelly(numServidor, regiao, authKey))
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
            Modifier.fillMaxWidth().clickable { aberto = !aberto }.padding(13.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83D\uDD11", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sec_conta), color = cores.tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.definida), color = cores.suave, fontSize = 9.sp)
        }

        if (aberto) {
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                Text(stringResource(R.string.servidor), color = cores.suave, fontSize = 9.5.sp, letterSpacing = 0.6.sp)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.width(66.dp)) {
                        CampoNumServidor(numServidor) { v -> numServidor = v; persiste() }
                    }
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(cores.elevado).padding(2.dp)) {
                        listOf("eu", "us", "ap").forEach { r ->
                            val sel = r == regiao
                            Text(
                                r,
                                color = if (sel) cores.azul else cores.suave,
                                fontSize = 10.sp,
                                fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) cores.azulTenue else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { regiao = r; persiste() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                val n = numServidor.ifBlank { "XX" }
                Text(
                    "shelly-$n-$regiao.shelly.cloud",
                    color = cores.suave, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 5.dp)
                )

                Text(
                    stringResource(R.string.auth_key), color = cores.suave, fontSize = 9.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = authKey,
                        onValueChange = { authKey = it; persiste() },
                        textStyle = TextStyle(color = cores.tinta, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        visualTransformation = if (chaveVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cores.elevado)
                            .padding(11.dp, 9.dp)
                    )
                    TextButton(onClick = { chaveVisivel = !chaveVisivel }) {
                        Text(
                            if (chaveVisivel) stringResource(R.string.ocultar) else stringResource(R.string.mostrar),
                            color = cores.azul, fontSize = 10.sp
                        )
                    }
                }

                Text(
                    stringResource(R.string.aviso_conta),
                    color = cores.avisoTinta, fontSize = 10.5.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(cores.avisoFundo)
                        .padding(10.dp, 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CampoNumServidor(valor: String, onValor: (String) -> Unit) {
    val cores = LocalCoresGateway.current
    BasicTextField(
        value = valor,
        onValueChange = { if (it.length <= 4) onValor(it) },
        textStyle = TextStyle(color = cores.tinta, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(cores.elevado)
            .padding(11.dp, 9.dp),
        decorationBox = { inner ->
            if (valor.isEmpty()) Text("00", color = cores.suave.copy(alpha = 0.5f), fontSize = 12.sp)
            inner()
        }
    )
}
