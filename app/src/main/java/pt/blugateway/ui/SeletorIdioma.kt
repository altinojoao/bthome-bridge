package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.ui.theme.LocalCoresGateway

data class Idioma(val codigo: String, val bandeira: String, val nome: String)

val IDIOMAS_SUPORTADOS = listOf(
    Idioma("pt", "\uD83C\uDDF5\uD83C\uDDF9", "Português"),
    Idioma("en", "\uD83C\uDDEC\uD83C\uDDE7", "English"),
    Idioma("es", "\uD83C\uDDEA\uD83C\uDDF8", "Español"),
    Idioma("fr", "\uD83C\uDDEB\uD83C\uDDF7", "Français"),
    Idioma("de", "\uD83C\uDDE9\uD83C\uDDEA", "Deutsch"),
    Idioma("it", "\uD83C\uDDEE\uD83C\uDDF9", "Italiano"),
    Idioma("nl", "\uD83C\uDDF3\uD83C\uDDF1", "Nederlands"),
    Idioma("pl", "\uD83C\uDDF5\uD83C\uDDF1", "Polski")
)

@Composable
fun DialogoIdioma(idiomaAtual: String, onEscolhe: (String) -> Unit, onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        text = {
            LazyColumn {
                items(IDIOMAS_SUPORTADOS) { idioma ->
                    val sel = idioma.codigo == idiomaAtual
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEscolhe(idioma.codigo) }
                            .background(if (sel) cores.azulTenue else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(12.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(idioma.bandeira, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(idioma.nome, color = if (sel) cores.azul else cores.tinta, fontSize = 14.sp)
                    }
                }
            }
        }
    )
}
