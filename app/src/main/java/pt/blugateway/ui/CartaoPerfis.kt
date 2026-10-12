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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.data.Perfil
import pt.blugateway.ui.theme.LocalCoresGateway

@Composable
fun CartaoPerfis(
    perfis: List<Perfil>,
    comandos: List<pt.blugateway.data.Comando>,
    perfilAtivoId: String?,
    confirmaApagar: String?,
    onAbre: (String) -> Unit,
    onRenomeia: (String, String) -> Unit,
    onPedeApagar: (String) -> Unit,
    onConfirmaApagar: (String) -> Unit,
    onNovoPerfil: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var aberto by remember { mutableStateOf(false) }

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
            Text("\u2699\uFE0F", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.sec_perfis), color = cores.tinta,
                fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
            )
            val resumo = if (perfis.size == 1) stringResource(R.string.perfil_1)
                         else stringResource(R.string.perfil_n, perfis.size)
            Text(resumo, color = cores.suave, fontSize = 9.sp)
        }

        if (aberto) {
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                Text(
                    stringResource(R.string.dica_perfis),
                    color = cores.suave, fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                perfis.forEachIndexed { i, p ->
                    val nAcoes = p.eventos.sumOf { it.size }
                    val nComandos = comandos.count { it.perfilId == p.id }
                    LinhaPerfil(
                        perfil = p,
                        nAcoes = nAcoes,
                        nComandos = nComandos,
                        ativo = p.id == perfilAtivoId,
                        podeApagar = perfis.size > 1,
                        confirmaAtivo = confirmaApagar == p.id,
                        onAbre = { onAbre(p.id) },
                        onRenomeia = { novo -> onRenomeia(p.id, novo) },
                        onPedeApagar = { onPedeApagar(p.id) },
                        onConfirmaApagar = { onConfirmaApagar(p.id) }
                    )
                }

                TextButton(
                    onClick = onNovoPerfil,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.novo_perfil), color = cores.azul, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LinhaPerfil(
    perfil: Perfil,
    nAcoes: Int,
    nComandos: Int,
    ativo: Boolean,
    podeApagar: Boolean,
    confirmaAtivo: Boolean,
    onAbre: () -> Unit,
    onRenomeia: (String) -> Unit,
    onPedeApagar: () -> Unit,
    onConfirmaApagar: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var texto by remember(perfil.id, perfil.nome) { mutableStateOf(perfil.nome) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 7.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(cores.elevado)
            .then(if (ativo) Modifier.border(1.dp, cores.azul, RoundedCornerShape(11.dp)) else Modifier)
            .padding(11.dp, 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = texto,
                onValueChange = { texto = it },
                textStyle = TextStyle(color = cores.tinta, fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.onFocusChanged { foco ->
                    if (!foco.isFocused && texto != perfil.nome) onRenomeia(texto)
                }
            )
            val resumo = "$nAcoes \u00b7 $nComandos"
            Text(resumo, color = cores.suave, fontSize = 9.sp, modifier = Modifier.padding(top = 1.dp))
        }

        TextButton(onClick = onAbre, enabled = !ativo) {
            Text(
                if (ativo) stringResource(R.string.aberto) else stringResource(R.string.abrir),
                color = if (ativo) cores.suave else cores.azul,
                fontSize = 10.sp
            )
        }

        if (!podeApagar) {
            IconButton(onClick = {}, enabled = false, modifier = Modifier.size(28.dp)) {
                Text("\u00d7", color = cores.suave.copy(alpha = 0.25f), fontSize = 15.sp)
            }
        } else if (confirmaAtivo) {
            TextButton(onClick = onConfirmaApagar) {
                Text(stringResource(R.string.apagar_q), color = cores.avisoTinta, fontSize = 10.sp)
            }
        } else {
            IconButton(onClick = onPedeApagar, modifier = Modifier.size(28.dp)) {
                Text("\u00d7", color = cores.suave, fontSize = 15.sp)
            }
        }
    }
}
