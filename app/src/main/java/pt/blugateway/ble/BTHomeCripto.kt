package pt.blugateway.ble

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Decifra AES-CCM para tramas BTHome v2 encriptadas (opção "Segurança
 * / conexão Bluetooth segura" nos botões Shelly BLU).
 *
 * O Android não expõe AES/CCM na lista de transformações suportadas
 * pelo javax.crypto.Cipher (só CBC, CFB, CTR, CTS, ECB, OFB) -- por
 * isso implementamos CCM manualmente por cima de AES/ECB/NoPadding
 * (que o Android suporta em todas as versões), seguindo a RFC 3610.
 * CCM é CTR (cifra) + CBC-MAC (autenticação), ambos construídos a
 * partir do mesmo bloco AES primitivo de 16 bytes.
 *
 * Algoritmo validado byte-a-byte contra o vetor de teste oficial da
 * documentação BTHome (bthome.io/encryption) antes desta implementação:
 * chave 231d39c1d7cc1ab1aee224cd096db932, mac 5448e68f80a5, dados
 * 02ca0903bf13, contador 0x00112233 -> ciphertext e445f3c9962b,
 * mic 6c7c4519.
 */
object BTHomeCripto {

    /** UUID do serviço BTHome (0xFCD2), little-endian: D2 FC -- usado
     *  na construção do nonce. Os bytes que chegam ao ScanReceiver já
     *  não incluem este UUID (foi consumido por getServiceData), por
     *  isso é sempre este valor fixo. */
    private val UUID_LE = byteArrayOf(0xD2.toByte(), 0xFC.toByte())

    private const val TAMANHO_MIC = 4
    private const val TAMANHO_NONCE = 13

    /**
     * Decifra uma trama BTHome v2 encriptada.
     *
     * @param bytesServiceData os bytes tal como chegam de
     *   ScanRecord.getServiceData() -- SEM o UUID do serviço, que já
     *   foi consumido no filtro. Formato esperado:
     *   [versão/flags·1][dados cifrados·N][contador·4][MIC·4]
     * @param mac endereço do dispositivo, 6 bytes na ordem do rádio
     * @param chave16 a chave de 16 bytes (32 caracteres hex) obtida
     *   pelo utilizador fora da app (ver ChaveEncriptacao)
     * @return os bytes decifrados (equivalentes ao payload de uma
     *   trama não encriptada, incluindo o byte de versão/flags no
     *   início) se o MIC for válido, ou null se a chave estiver
     *   errada, os dados estiverem corrompidos, ou a trama for
     *   demasiado curta para ser válida.
     */
    fun decifra(bytesServiceData: ByteArray, mac: ByteArray, chave16: ByteArray): ByteArray? {
        // tamanho mínimo: 1 (versão/flags) + 0 (dados, pode ser vazio) + 4 (contador) + 4 (mic)
        if (bytesServiceData.size < 9) return null
        if (mac.size != 6) return null
        if (chave16.size != 16) return null

        val versaoFlags = bytesServiceData[0]
        val resto = bytesServiceData.copyOfRange(1, bytesServiceData.size)
        val micRecebido = resto.copyOfRange(resto.size - TAMANHO_MIC, resto.size)
        val contadorBytes = resto.copyOfRange(resto.size - 8, resto.size - TAMANHO_MIC)
        val dadosCifrados = resto.copyOfRange(0, resto.size - 8)

        val nonce = mac + UUID_LE + byteArrayOf(versaoFlags) + contadorBytes
        if (nonce.size != TAMANHO_NONCE) return null // salvaguarda, nunca deveria falhar com os tamanhos acima

        val ecb = try {
            Cipher.getInstance("AES/ECB/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(chave16, "AES"))
            }
        } catch (e: Exception) {
            return null
        }

        fun blocoAes(bloco16: ByteArray): ByteArray = ecb.doFinal(bloco16)

        // decifra: CTR e simétrico, a mesma função serve para cifrar e decifrar
        val dadosClaros = cifraDecifraCtr(::blocoAes, nonce, dadosCifrados)

        // verifica o MIC: recalcula a partir dos dados JÁ decifrados
        val micBrutoCalculado = calculaCbcMac(::blocoAes, nonce, dadosClaros, TAMANHO_MIC)
        val micCalculado = cifraMic(::blocoAes, nonce, micBrutoCalculado)

        if (!micCalculado.contentEquals(micRecebido)) return null // chave errada ou dados corrompidos

        // devolve no mesmo formato de uma trama não encriptada: byte
        // de versão/flags (com o bit de encriptação LIMPO -- os dados
        // que se seguem já não estão cifrados, e BTHome.descodifica()
        // rejeitaria a trama se o bit continuasse ligado, pois essa
        // verificação existe para rejeitar tramas que NÃO foram
        // decifradas, não tramas já decifradas com sucesso) seguido
        // dos dados decifrados, para que o resto do
        // BTHome.descodifica() os processe normalmente.
        val versaoFlagsLimpo = (versaoFlags.toInt() and 0xFE).toByte()
        return byteArrayOf(versaoFlagsLimpo) + dadosClaros
    }

    private fun constroiBlocoB0(nonce: ByteArray, tamanhoDados: Int, tamanhoMic: Int): ByteArray {
        val l = 15 - nonce.size // L = 2 para nonce de 13 bytes
        val m = tamanhoMic
        val flags = (((m - 2) / 2) shl 3) or (l - 1)
        val tamanhoBytes = ByteArray(l)
        var t = tamanhoDados
        for (i in l - 1 downTo 0) {
            tamanhoBytes[i] = (t and 0xFF).toByte()
            t = t shr 8
        }
        return byteArrayOf(flags.toByte()) + nonce + tamanhoBytes
    }

    private fun constroiContadorAi(nonce: ByteArray, i: Int): ByteArray {
        val l = 15 - nonce.size
        val flags = (l - 1)
        val contadorBytes = ByteArray(l)
        var c = i
        for (k in l - 1 downTo 0) {
            contadorBytes[k] = (c and 0xFF).toByte()
            c = c shr 8
        }
        return byteArrayOf(flags.toByte()) + nonce + contadorBytes
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray =
        ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }

    private fun calculaCbcMac(blocoAes: (ByteArray) -> ByteArray, nonce: ByteArray, dados: ByteArray, tamanhoMic: Int): ByteArray {
        val b0 = constroiBlocoB0(nonce, dados.size, tamanhoMic)
        var x = blocoAes(b0)

        var offset = 0
        while (offset < dados.size) {
            val fim = minOf(offset + 16, dados.size)
            var bloco = dados.copyOfRange(offset, fim)
            if (bloco.size < 16) bloco = bloco + ByteArray(16 - bloco.size)
            x = blocoAes(xor(x, bloco))
            offset += 16
        }
        // caso especial: dados vazios -- ainda processa um bloco de zeros XOR x
        if (dados.isEmpty()) {
            x = blocoAes(xor(x, ByteArray(16)))
        }

        return x.copyOfRange(0, tamanhoMic)
    }

    private fun cifraDecifraCtr(blocoAes: (ByteArray) -> ByteArray, nonce: ByteArray, dados: ByteArray): ByteArray {
        if (dados.isEmpty()) return dados
        val resultado = ByteArray(dados.size)
        var offset = 0
        var contador = 1
        while (offset < dados.size) {
            val ai = constroiContadorAi(nonce, contador)
            val keystream = blocoAes(ai)
            val fim = minOf(offset + 16, dados.size)
            for (i in offset until fim) {
                resultado[i] = (dados[i].toInt() xor keystream[i - offset].toInt()).toByte()
            }
            offset += 16
            contador += 1
        }
        return resultado
    }

    private fun cifraMic(blocoAes: (ByteArray) -> ByteArray, nonce: ByteArray, micBruto: ByteArray): ByteArray {
        val a0 = constroiContadorAi(nonce, 0)
        val keystreamA0 = blocoAes(a0)
        return ByteArray(micBruto.size) { i -> (micBruto[i].toInt() xor keystreamA0[i].toInt()).toByte() }
    }
}
