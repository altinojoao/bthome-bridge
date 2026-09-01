package pt.blugateway.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pt.blugateway.R
import pt.blugateway.data.Comando
import pt.blugateway.data.OrigemPonto
import pt.blugateway.data.Repositorio
import pt.blugateway.ui.theme.LocalCoresGateway

/* Cores fixas por indice de comando (nao vem do tema -- sao cores
   de trajeto, distintas o suficiente para diferenciar linhas
   sobrepostas no mesmo mapa, independentes do tema claro/escuro). */
private val CORES_TRAJETO = listOf(
    AndroidColor.parseColor("#E24B4A"), // vermelho
    AndroidColor.parseColor("#378ADD"), // azul
    AndroidColor.parseColor("#639922"), // verde
    AndroidColor.parseColor("#BA7517"), // ambar
    AndroidColor.parseColor("#7F77DD"), // roxo
    AndroidColor.parseColor("#D4537E"), // rosa
    AndroidColor.parseColor("#1D9E75"), // teal
    AndroidColor.parseColor("#D85A30")  // coral
)

private fun corParaComando(indice: Int): Int = CORES_TRAJETO[indice % CORES_TRAJETO.size]

/* Ecra de mapa com o historico de trajeto de TODOS os comandos
   sobrepostos, cada um com a sua cor (linha + pontos). Le o
   historico persistido em Repositorio.historicoTrajeto(mac) -- os
   pontos sao gravados por GestorTrajeto, tanto em cliques
   (incluirLocalizacao) como em anuncios beacon (modoBeaconTrajeto),
   dependendo do que cada comando tiver ativado. */
@Composable
fun EcraMapa(comandos: List<Comando>, onLimpaTrajeto: (String) -> Unit, onFecha: () -> Unit) {
    val cores = LocalCoresGateway.current
    val contexto = LocalContext.current
    var confirmaLimpar by remember { mutableStateOf<String?>(null) }
    var recarregar by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.mapa_trajeto),
                    color = cores.tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFecha) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                }
            }
        },
        text = {
            if (comandos.isEmpty()) {
                Text(
                    stringResource(R.string.nenhum_comando),
                    color = cores.suave, fontSize = 12.sp
                )
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Legenda(comandos, onPedeLimpar = { mac -> confirmaLimpar = mac })
                    Spacer(Modifier.height(8.dp))
                    // a chave "recarregar" forca o AndroidView a reler o
                    // historico apos uma limpeza -- osmdroid nao observa
                    // StateFlow, so recompoe quando esta chave muda
                    key(recarregar) {
                        MapaComTrajetos(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            comandos = comandos,
                            contexto = contexto
                        )
                    }
                }
            }
        }
    )

    confirmaLimpar?.let { mac ->
        val nomeComando = comandos.firstOrNull { it.mac == mac }?.nome ?: mac
        AlertDialog(
            onDismissRequest = { confirmaLimpar = null },
            containerColor = cores.cartao,
            title = { Text(stringResource(R.string.limpar_trajeto_q), color = cores.tinta, fontSize = 15.sp) },
            text = { Text(nomeComando, color = cores.suave, fontSize = 12.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onLimpaTrajeto(mac)
                    confirmaLimpar = null
                    recarregar++
                }) {
                    Text(stringResource(R.string.limpar), color = cores.avisoTinta)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmaLimpar = null }) {
                    Text(stringResource(R.string.cancelar), color = cores.suave)
                }
            }
        )
    }
}

@Composable
private fun Legenda(comandos: List<Comando>, onPedeLimpar: (String) -> Unit) {
    val cores = LocalCoresGateway.current
    Column(Modifier.fillMaxWidth()) {
        comandos.forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "\u25CF ",
                    color = androidx.compose.ui.graphics.Color(corParaComando(i)),
                    fontSize = 12.sp
                )
                Text(c.nome, color = cores.suave, fontSize = 10.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onPedeLimpar(c.mac) }, modifier = Modifier.size(24.dp)) {
                    Text("\u00d7", color = cores.suave, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun MapaComTrajetos(modifier: Modifier, comandos: List<Comando>, contexto: Context) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            val repo = Repositorio(contexto)
            val todosOsPontos = mutableListOf<GeoPoint>()

            comandos.forEachIndexed { i, comando ->
                val historico = repo.historicoTrajeto(comando.mac).sortedBy { it.timestamp }
                if (historico.isEmpty()) return@forEachIndexed

                val cor = corParaComando(i)
                val pontosGeo = historico.map { GeoPoint(it.latitude, it.longitude) }
                todosOsPontos.addAll(pontosGeo)

                if (pontosGeo.size >= 2) {
                    val linha = Polyline(mapView).apply {
                        setPoints(pontosGeo)
                        outlinePaint.color = cor
                        outlinePaint.strokeWidth = 6f
                    }
                    mapView.overlays.add(linha)
                }

                // marcador so no ponto mais recente de cada comando, para
                // nao sobrecarregar o mapa com um icone por ponto -- o
                // trajeto completo ja fica visivel pela linha
                val ultimo = historico.last()
                val marcador = Marker(mapView).apply {
                    position = GeoPoint(ultimo.latitude, ultimo.longitude)
                    title = comando.nome
                    snippet = if (ultimo.origem == OrigemPonto.BEACON) "beacon" else "clique"
                }
                mapView.overlays.add(marcador)
            }

            if (todosOsPontos.isNotEmpty()) {
                val lat = todosOsPontos.map { it.latitude }.average()
                val lon = todosOsPontos.map { it.longitude }.average()
                mapView.controller.setCenter(GeoPoint(lat, lon))
            }

            mapView.invalidate()
        }
    )
}
