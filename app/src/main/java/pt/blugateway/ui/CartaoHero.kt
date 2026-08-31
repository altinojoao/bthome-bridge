package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.data.Comando
import pt.blugateway.ui.theme.LocalCoresGateway

/* Card "Escuta" comprimido para uma unica linha, replicando o
   #slotHero .hero do HTML: ponto de estado, titulo, resumo e o
   botao de procurar todos lado a lado, com o "?" de ajuda entre o
   ponto de estado e o titulo. */
@Composable
fun CartaoHero(
    comandos: List<Comando>,
    emparelhamentoAberto: Boolean,
    ajudaAtiva: Boolean,
    onAlternaAjuda: () -> Unit,
    onDetetar: () -> Unit
) {
    val cores = LocalCoresGateway.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(4.dp))
            .padding(10.dp, 10.dp, 12.dp, 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(cores.ok)
            )
            Spacer(Modifier.width(6.dp))
            BotaoAjuda(ativo = ajudaAtiva, onClick = onAlternaAjuda)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.ecra_apagado),
                color = cores.tinta,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))

            val textoResumo = if (comandos.size == 1) {
                stringResource(R.string.cmd_assoc_1)
            } else {
                stringResource(R.string.cmd_assoc_n, comandos.size)
            }
            Text(
                textoResumo,
                color = cores.suave,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))

            IconButton(
                onClick = onDetetar,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (emparelhamentoAberto) cores.azul else cores.elevado)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(
                        if (emparelhamentoAberto) R.string.tt_parar else R.string.tt_detetar
                    ),
                    tint = if (emparelhamentoAberto) androidx.compose.ui.graphics.Color.White else cores.azul,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        BalaoAjuda(
            texto = stringResource(R.string.ajuda_escuta),
            visivel = ajudaAtiva
        )
    }
}
