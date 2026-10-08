package pt.blugateway.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Paleta idêntica às variáveis CSS :root / body.claro da versão web,
// para que a app nativa pareça a mesma interface já validada.

data class CoresGateway(
    val fundo: Color,
    val cartao: Color,
    val elevado: Color,
    val linha: Color,
    val tinta: Color,
    val suave: Color,
    val azul: Color,
    val azulTenue: Color,
    val ok: Color,
    val okTenue: Color,
    val avisoTinta: Color,
    val avisoFundo: Color
)

val CoresEscuro = CoresGateway(
    fundo = Color(0xFF0D1117),
    cartao = Color(0xFF161C24),
    elevado = Color(0xFF1E2732),
    linha = Color(0xFF252E3A),
    tinta = Color(0xFFE6EDF3),
    suave = Color(0xFF8B98A5),
    azul = Color(0xFF2BA6E0),
    azulTenue = Color(0x242BA6E0),
    ok = Color(0xFF3FB950),
    okTenue = Color(0x213FB950),
    avisoTinta = Color(0xFFD9A441),
    avisoFundo = Color(0x1AD9A441)
)

val CoresClaro = CoresGateway(
    fundo = Color(0xFFF2F5F8),
    cartao = Color(0xFFFFFFFF),
    elevado = Color(0xFFEDF1F5),
    linha = Color(0xFFE1E7ED),
    tinta = Color(0xFF16202B),
    suave = Color(0xFF64707C),
    azul = Color(0xFF0086C9),
    azulTenue = Color(0x1A0086C9),
    ok = Color(0xFF2E9E5B),
    okTenue = Color(0x1C2E9E5B),
    avisoTinta = Color(0xFF8A6100),
    avisoFundo = Color(0xFFFFF6DF)
)

val LocalCoresGateway = androidx.compose.runtime.staticCompositionLocalOf { CoresEscuro }

@Composable
fun TemaGateway(temaClaro: Boolean, conteudo: @Composable () -> Unit) {
    val cores = if (temaClaro) CoresClaro else CoresEscuro
    androidx.compose.runtime.CompositionLocalProvider(LocalCoresGateway provides cores) {
        MaterialTheme(
            typography = Typography(),
            content = conteudo
        )
    }
}

/**
 * Texto para estados vazios ("nenhum comando associado ainda",
 * "nenhum cenario criado ainda", etc) -- estilo italico distinto do
 * texto secundario normal (que usa a mesma cor 'suave' mas sem
 * italico), para o utilizador perceber de relance que esta perante
 * um estado "nada aqui", nao uma informacao qualquer. So' estilo:
 * nenhuma alteracao de comportamento ou de onde estes textos
 * aparecem.
 */
@Composable
fun TextoEstadoVazio(
    texto: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val cores = LocalCoresGateway.current
    androidx.compose.material3.Text(
        texto,
        color = cores.suave,
        fontSize = 10.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        modifier = modifier
    )
}
