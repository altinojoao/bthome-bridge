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
import pt.blugateway.data.Perfil
import pt.blugateway.ui.theme.LocalCoresGateway

/** Mostrado quando o botão do topo é tocado sem nenhum perfil aberto,
 *  e vários perfis têm o Modo Combinação ligado — o utilizador
 *  escolhe qual quer abrir/desligar. */
@Composable
fun DialogoPerfisEspeciais(perfis: List<Perfil>, onEscolhe: (String) -> Unit, onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        text = {
            LazyColumn {
                items(perfis) { perfil ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEscolhe(perfil.id) }
                            .background(cores.azulTenue)
                            .padding(12.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDD17", fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(perfil.nome, color = cores.azul, fontSize = 14.sp)
                    }
                }
            }
        }
    )
}
