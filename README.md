# bthome-bridge

Transforma um telemóvel Android no gateway BLE de um comando Shelly BLU, sem a
dongle USB e sem nenhum dispositivo Shelly por perto.

Funciona com a app fechada e o ecrã apagado: o scan é registado no subsistema
Bluetooth do Android através de um `PendingIntent`, e é o próprio sistema que
acorda a app quando o comando emite.

## Porquê

A dongle BLU Gateway e qualquer dispositivo Plus/Pro/Gen3 funcionam bem como
gateways BLE, mas têm de estar fisicamente perto do dispositivo BLU — cerca de
10 m dentro de casa. Serve para um sensor fixo, mas não para um botão que anda
no bolso: longe de casa, não há gateway ao alcance.

A solução é usar o único aparelho que está sempre ao lado do botão: o próprio
telemóvel.

## Instalar

Descarregue o `app-debug.apk` da [última
Release](../../releases/latest) e instale-o. O Android vai pedir para
autorizar a instalação de fontes desconhecidas.

**Depois de instalar, vá às definições de bateria e ponha a app em "sem
restrições".** A Samsung e a Xiaomi matam processos em segundo plano de forma
agressiva, e é essa a causa mais comum de cliques perdidos.

## Usar

1. Abrir a app e conceder as permissões pedidas.
2. **Detetar o comando** e carregar no botão durante a procura.
3. Escolher o comando na lista.
4. Preencher o endereço a chamar para os cliques que interessam.
5. **Começar a ouvir.**

Deixe em branco os cliques que não quer usar.

### Marcadores disponíveis no endereço

`{evento}` `{codigo}` `{botao}` `{mac}` `{bateria}` `{rssi}`

Em POST, se não indicar corpo, é enviado um JSON com todos estes campos.

## Ativar um cenário Shelly

```
POST https://shelly-XX-eu.shelly.cloud/scene/manual_run
Content-Type: application/x-www-form-urlencoded

id=SCENE_ID&auth_key=YOUR_AUTH_KEY
```

Método POST, com `id` e `auth_key` no **corpo** do pedido — nunca na URL.
Uma auth key na query string fica exposta em qualquer sítio por onde essa
URL passe (logs de proxy, histórico de DNS/CDN, um *man-in-the-middle* que
capture a URL completa); repetir essa URL captada bastaria para disparar
cenários com a conta comprometida. O corpo do pedido não sofre dessa
exposição da mesma forma. O endereço do servidor e a auth key estão na app
Shelly, em Definições de utilizador → Authorization cloud key. O ID do
cenário obtém-se abrindo o cenário para edição em control.shelly.cloud e
lendo-o na barra de endereço.

Este endpoint não está documentado pela Shelly e pode mudar sem aviso. A auth
key dá controlo total da conta e não pode ser revogada individualmente — mudar
a palavra-passe da conta é a única forma de a invalidar. Não a exponha em
capturas de ecrã.

**Porque não precisa de gateway físico:** o pedido não passa pelo sistema de
eventos BLU da Shelly — vai diretamente à cloud a pedir "executa o cenário X".
A cloud verifica a auth key e executa; não pergunta de onde veio o pedido nem
que dispositivo o originou. O comando BLU só interessa deste lado, é o que faz
a app disparar o pedido.

**Quem distingue o tipo de clique é a app, não a cloud.** Ao descodificar a
trama, a app sabe se foi simples, duplo, triplo ou longo, e cada um tem o seu
próprio campo de endereço, apontado a um ID de cenário diferente. O pedido
`manual_run` não transporta essa informação — só o ID do cenário a correr. Por
isso, para comportamentos diferentes por tipo de clique, são precisos vários
cenários, um por ID.

Alternativa sem chave: uma VPN até casa e chamar o RPC do dispositivo
diretamente por IP local. Sem cloud, sem chave — mas perdem-se os cenários
(vivem na cloud), e é preciso algo em casa a correr a VPN.

## Alarme de fora de alcance

Por comando, é possível ativar um alerta que dispara se não chegar nenhum
sinal (clique ou anúncio) durante um tempo configurável, ou se o último RSSI
recebido for mais fraco que um limite configurável. Requer que o comando
tenha o modo beacon ativado — sem beacon, o único sinal possível é o próprio
clique, o que não permite distinguir "fora de alcance" de "não foi premido".

O alarme pode ficar sempre ativo (24h) ou só dentro de uma agenda semanal
configurável por dia, com vários períodos por dia, incluindo períodos que
atravessam a meia-noite.

## Combinações de cliques

Um perfil pode reagir a uma sequência de cliques (ex: duplo → simples →
longo) dentro de uma janela de tempo, em vez de reagir a cada clique
isoladamente.

## Notas técnicas

O comando repete o mesmo anúncio cerca de vinte vezes por clique. A app
distingue um clique novo de um eco pelo ID de pacote BTHome (objeto `0x00`),
que se mantém constante durante as repetições e só muda no clique seguinte.
Esse último ID é guardado em disco, não em memória, porque o sistema mata e
reinicia o processo entre cliques.

O filtro de scan é obrigatório: o Android recusa scans por `PendingIntent` sem
pelo menos um `ScanFilter`. Aqui filtra-se pelo service data BTHome (`0xFCD2`)
e pelo endereço do comando.

Se o comando estiver emparelhado com encriptação (AES-128-CCM), a app consegue
decifrar as tramas desde que a chave de 32 caracteres hex seja introduzida
manualmente no comando associado — essa chave só é exposta pela Shelly através
de ferramentas de debug BLE, não pela app normal.

## Limitações

- **Só Android.** O iOS bloqueia o scan em segundo plano de anúncios não
  conectáveis. Não é portável.
- **APK de depuração**, assinado com a chave de debug. Serve para uso pessoal,
  não para a Play Store.
- **Custo de bateria.** Usa o modo de scan de baixa latência para não perder
  cliques. É o modo caro.
- **Não substitui um gateway fixo.** Para sensores que ficam em casa, um
  dispositivo Plus/Pro ou a dongle continuam a ser a resposta certa. Isto
  serve para dispositivos BLU que andam consigo.

## Testado com

Shelly BLU Button 1 (SBBT-002C). Não testado contra o RC Button 4, que emite
vários objetos de botão por trama — contribuições bem-vindas.

Sem qualquer ligação à Shelly.

## Compilar a partir do código

```
git clone https://github.com/altinojoao/bthome-bridge
cd bthome-bridge
gradle assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.
