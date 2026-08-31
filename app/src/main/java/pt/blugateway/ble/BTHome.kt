package pt.blugateway.ble

/**
 * Descodificador BTHome v2. Porta direta da lógica já validada na
 * versão web (função descodificaBTHome em ble.js) — mesmos testes
 * de trama real aplicados: 40 00 23 01 64 3A 01 = header v2 não
 * encriptado, PID=35, bateria=100%, clique simples.
 */
data class TramaBTHome(
    val pid: Int?,
    val bateria: Int?,
    val evento: Int?,
    val eventoPos: Int,
    val tamanho: Int
)

object BTHome {

    const val SERVICE_UUID_16 = 0xFCD2
    const val SERVICE_UUID_STR = "0000fcd2-0000-1000-8000-00805f9b34fb"

    /**
     * Descodifica uma trama BTHome v2 NÃO encriptada.
     * Devolve null se: header inválido, versão != 2, ou encriptada
     * (bit 0 do header a 1) — não fazemos decifra AES-128 CCM.
     */
    fun descodifica(bytes: ByteArray): TramaBTHome? {
        if (bytes.isEmpty()) return null

        val header = bytes[0].toInt() and 0xFF
        val encriptado = (header and 0x01) != 0
        val versao = (header shr 5) and 0x07
        if (versao != 2 || encriptado) return null

        var pid: Int? = null
        var bateria: Int? = null
        var evento: Int? = null
        var eventoPos = -1

        var i = 1
        while (i < bytes.size) {
            val obj = bytes[i].toInt() and 0xFF
            i += 1
            when (obj) {
                0x00 -> {
                    if (i >= bytes.size) break
                    pid = bytes[i].toInt() and 0xFF
                    i += 1
                }
                0x01 -> {
                    if (i >= bytes.size) break
                    bateria = bytes[i].toInt() and 0xFF
                    i += 1
                }
                0x3A -> {
                    if (i >= bytes.size) break
                    evento = bytes[i].toInt() and 0xFF
                    eventoPos = i
                    i += 1
                }
                0x02, 0x03 -> i += 2 // temperatura / humidade (2 bytes), ignorados
                else -> break // objeto desconhecido: parar a leitura em segurança
            }
        }

        return TramaBTHome(pid, bateria, evento, eventoPos, bytes.size)
    }

    fun hexString(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
}
