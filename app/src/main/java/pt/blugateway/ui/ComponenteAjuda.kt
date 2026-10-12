package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.ui.theme.LocalCoresGateway

/* Botao "?" de ajuda, replicando o .did-ajuda do HTML: um pequeno
   circulo clicavel, colocado logo apos o icone/emoji de cada card
   (antes do titulo). O clique nao deve propagar para o onClick do
   card em si -- por isso este composable e sempre usado dentro de
   um Modifier.clickable proprio, separado do Row do cabecalho. */
@Composable
fun BotaoAjuda(ativo: Boolean, onClick: () -> Unit) {
    val cores = LocalCoresGateway.current
    Box(
        Modifier
            .size(15.dp)
            .clip(CircleShape)
            .background(if (ativo) cores.azulTenue else cores.elevado)
            .border(1.dp, if (ativo) cores.azul else cores.linha, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "?",
            fontSize = 9.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (ativo) cores.azul else cores.suave
        )
    }
}

/* Balao de texto explicativo, aparece por baixo do titulo quando o
   BotaoAjuda correspondente esta ativo. Replica o .did-balao do
   HTML: fundo elevado, borda subtil, texto pequeno. */
@Composable
fun BalaoAjuda(texto: String, visivel: Boolean, modifier: Modifier = Modifier) {
    if (!visivel) return
    val cores = LocalCoresGateway.current
    Text(
        texto,
        color = cores.tinta,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(cores.cartao)
            .border(1.dp, cores.linha, RoundedCornerShape(9.dp))
            .padding(11.dp, 9.dp)
    )
}
