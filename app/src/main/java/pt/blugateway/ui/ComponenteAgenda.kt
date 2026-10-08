package pt.blugateway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.blugateway.R
import pt.blugateway.data.Comando
import pt.blugateway.data.PeriodoAgenda
import pt.blugateway.ui.theme.LocalCoresGateway

private val NOMES_DIAS = intArrayOf(
    R.string.dia_dom, R.string.dia_seg, R.string.dia_ter, R.string.dia_qua,
    R.string.dia_qui, R.string.dia_sex, R.string.dia_sab
)

/* Agenda semanal do alarme de fora-de-alcance de um comando,
   replicando a grelha horizontal de 7 dias do HTML: cada dia mostra
   um "+" ou um ponto azul (se ja tiver periodos), e tocar num dia
   seleciona-o e mostra o detalhe (periodos existentes + construtor
   de novo periodo) por baixo da grelha. So um dia selecionado de
   cada vez. */
@Composable
fun BlocoAgenda(
    comando: Comando,
    onAlternaSempreAtiva: (Boolean) -> Unit,
    onAdicionaPeriodo: (Int, String, String) -> Boolean,
    onRemovePeriodo: (Int, Int) -> Unit
) {
    val cores = LocalCoresGateway.current
    var diaSelecionado by remember(comando.mac) { mutableStateOf<Int?>(null) }
    var construtorAberto by remember(comando.mac) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ativo_24h),
                color = cores.suave,
                fontSize = 9.5.sp,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Switch(
                    checked = comando.agendaSempreAtiva,
                    onCheckedChange = onAlternaSempreAtiva,
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        if (!comando.agendaSempreAtiva) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (dia in 0..6) {
                    val periodos = comando.agendaDias[dia] ?: emptyList()
                    val selecionado = diaSelecionado == dia
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selecionado) cores.azulTenue else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (selecionado) cores.azul else cores.linha, RoundedCornerShape(8.dp))
                            .clickable {
                                diaSelecionado = if (selecionado) null else dia
                                construtorAberto = false
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(NOMES_DIAS[dia]),
                            color = if (selecionado) cores.azul else cores.suave,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        if (periodos.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(cores.azul)
                            )
                        } else {
                            Text("+", color = cores.suave, fontSize = 11.sp)
                        }
                    }
                }
            }

            diaSelecionado?.let { dia ->
                DetalheDiaAgenda(
                    dia = dia,
                    periodos = comando.agendaDias[dia] ?: emptyList(),
                    construtorAberto = construtorAberto,
                    onAbreConstrutor = { construtorAberto = true },
                    onFechaConstrutor = { construtorAberto = false },
                    onAdicionaPeriodo = { inicio, fim -> onAdicionaPeriodo(dia, inicio, fim) },
                    onRemovePeriodo = { indice -> onRemovePeriodo(dia, indice) }
                )
            }
        }
    }
}

@Composable
private fun DetalheDiaAgenda(
    dia: Int,
    periodos: List<PeriodoAgenda>,
    construtorAberto: Boolean,
    onAbreConstrutor: () -> Unit,
    onFechaConstrutor: () -> Unit,
    onAdicionaPeriodo: (String, String) -> Boolean,
    onRemovePeriodo: (Int) -> Unit
) {
    val cores = LocalCoresGateway.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cores.elevado)
            .padding(10.dp)
    ) {
        if (periodos.isEmpty()) {
            pt.blugateway.ui.theme.TextoEstadoVazio(stringResource(R.string.sem_periodos_neste_dia))
        } else {
            periodos.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${p.inicio} \u2013 ${p.fim}",
                        color = cores.tinta,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onRemovePeriodo(i) }) {
                        Text("\u00d7", color = cores.suave, fontSize = 14.sp)
                    }
                }
            }
        }

        if (!construtorAberto) {
            TextButton(onClick = onAbreConstrutor, modifier = Modifier.padding(top = 4.dp)) {
                Text("+ " + stringResource(R.string.novo_periodo), color = cores.azul, fontSize = 10.5.sp)
            }
        } else {
            // chave = dia, para o construtor reiniciar (inicio/fim/erro) sempre
            // que o utilizador troca de dia selecionado, em vez de arrastar o
            // estado do dia anterior por a posicao na composicao ser a mesma
            var inicio by remember(dia) { mutableStateOf("09:00") }
            var fim by remember(dia) { mutableStateOf("18:00") }
            var erro by remember(dia) { mutableStateOf(false) }

            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CampoHora(valor = inicio, onValor = { inicio = it; erro = false })
                Spacer(Modifier.width(6.dp))
                Text("\u2013", color = cores.suave)
                Spacer(Modifier.width(6.dp))
                CampoHora(valor = fim, onValor = { fim = it; erro = false })
            }

            if (erro) {
                Text(
                    stringResource(R.string.periodo_sobreposto),
                    color = cores.avisoTinta,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFechaConstrutor) {
                    Text(stringResource(R.string.cancelar), color = cores.suave, fontSize = 10.sp)
                }
                TextButton(onClick = {
                    val ok = onAdicionaPeriodo(inicio, fim)
                    if (ok) onFechaConstrutor() else erro = true
                }) {
                    Text(stringResource(R.string.guardar_combinacao), color = cores.azul, fontSize = 10.sp)
                }
            }
        }
    }
}

/* Campo de hora simples, formato HH:MM, validado enquanto o
   utilizador escreve -- nao usa o seletor nativo do Android para
   manter consistencia visual com o resto da app (o mesmo motivo por
   que o HTML usa <input type=time> sem estilizacao extra la, mas
   aqui preferimos um campo de texto compacto e previsivel). */
@Composable
private fun CampoHora(valor: String, onValor: (String) -> Unit) {
    val cores = LocalCoresGateway.current
    androidx.compose.material3.OutlinedTextField(
        value = valor,
        onValueChange = { novo -> if (novo.length <= 5) onValor(novo) },
        modifier = Modifier.width(72.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = cores.tinta),
        singleLine = true,
        placeholder = { Text("00:00", fontSize = 11.sp) }
    )
}
