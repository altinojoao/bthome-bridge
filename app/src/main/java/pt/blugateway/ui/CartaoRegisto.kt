package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.ble.LinhaRegisto
import pt.blugateway.ui.theme.LocalCoresGateway

@Composable
fun CartaoRegisto(linhas: List<LinhaRegisto>, ajudaAtiva: Boolean, onAlternaAjuda: () -> Unit) {
    val cores = LocalCoresGateway.current
    var aberto by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { aberto = !aberto }.padding(13.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83D\uDCCB", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            BotaoAjuda(ativo = ajudaAtiva, onClick = onAlternaAjuda)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sec_registo), color = cores.tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(linhas.firstOrNull()?.hora ?: "", color = cores.suave, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        BalaoAjuda(
            texto = stringResource(R.string.ajuda_registo),
            visivel = ajudaAtiva,
            modifier = Modifier.padding(horizontal = 13.dp)
        )

        if (aberto) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp)
            ) {
                items(linhas) { l ->
                    Text(
                        "${l.hora}  ${l.texto}",
                        color = if (l.ok) cores.suave else cores.avisoTinta,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
