# bthome-bridge / BluGateway

Telemóvel Android como gateway BLE para um comando Shelly BLU, sem a dongle USB e sem nenhum dispositivo Shelly por perto.

Funciona com a app fechada e o ecrã apagado: o scan é registado no subsistema Bluetooth do Android através de um `PendingIntent`, e é o próprio sistema que acorda a app quando o comando emite.

## Porquê

A dongle BLU Gateway funciona bem para sensores fixos em casa, mas não para um botão que anda no bolso: longe de casa, não há gateway ao alcance. A solução é usar o único aparelho que está sempre ao lado do botão: o próprio telemóvel.

## Instalar

Descarregue o `app-debug.apk` da [última Release](../../releases/latest) e instale-o.

**Após instalar, vá às definições de bateria e ponha a app em "Sem restrições".** Samsung e Xiaomi matam processos em segundo plano agressivamente — é a causa mais comum de cliques perdidos.

## Funcionalidades principais

### Ações por clique

Cada tipo de clique (simples, duplo, longo, etc.) pode chamar um URL arbitrário — GET ou POST — com marcadores dinâmicos:

`{evento}` `{codigo}` `{botao}` `{mac}` `{bateria}` `{rssi}` `{lat}` `{lon}`

### Combinações de cliques

Reagir a sequências específicas de cliques (ex: duplo + simples) em vez de só a cliques isolados. Ver [Combinações de cliques](../../wiki/Combinações-de-cliques).

### Alarme de fora de alcance

Alerta quando o beacon deixa de ser recebido durante demasiado tempo, ou quando o RSSI cai abaixo de um limiar. Com agenda semanal. Ver [Alarme de fora de alcance](../../wiki/Alarme-de-fora-de-alcance).

### Cenários de trajeto

Detetar automaticamente quando percorreu um percurso específico e executar ações. Define a rota num mapa (OpenStreetMap, com cálculo via OSRM), e a app deteta quando o beacon (e o telemóvel) estão a percorrer esse trajeto.

**Algoritmo híbrido Map Matching + LCSS:** projeta cada ponto GPS no segmento mais próximo do template e mede o progresso em metros — proporcional independentemente da densidade da rota ou da velocidade. Adaptado automaticamente ao RSSI do beacon (proxy de movimento gratuito), ao espaçamento do template, e à velocidade estimada. Ver [Cenários de trajeto](../../wiki/Cenários-de-trajeto).

### GPS adaptativo por RSSI

Enquanto o modo trajeto está activo, o GPS adapta-se ao estado de movimento detectado pelo RSSI do beacon:
- **Em movimento** (RSSI varia): GPS a 1 s
- **Parado recente** (< 5 min): GPS a 30 s
- **Parado longo** (≥ 5 min): GPS desligado

Poupança típica: ~95 % do consumo GPS face ao modo contínuo permanente.

### Simulador de percurso

Antes de fazer o percurso real, simule-o a partir da lista de cenários: o template é reproduzido ponto a ponto com linha verde a crescer e percentagem em tempo real, mostrando exactamente onde a ação dispararia.

## Ativar um cenário Shelly

```
POST https://shelly-XX-eu.shelly.cloud/scene/manual_run
Content-Type: application/x-www-form-urlencoded

id=SCENE_ID&auth_key=YOUR_AUTH_KEY
```

O `id` do cenário obtém-se em control.shelly.cloud → editar cenário → URL. A auth key está em Definições de utilizador → Authorization cloud key.

Envie sempre a auth key no **corpo** do pedido POST, nunca na URL — ficaria exposta em logs de proxy e histórico de rede.

Ver [Ativar um cenário Shelly](../../wiki/Ativar-um-cenário-Shelly) para mais detalhes e alternativas sem cloud.

## Wiki

Documentação completa em [github.com/altinojoao/bthome-bridge/wiki](../../wiki):

- [Como funciona](../../wiki/Como-funciona)
- [Instalação](../../wiki/Instalação)
- [Cenários de trajeto](../../wiki/Cenários-de-trajeto)
- [Localização e mapa de trajeto](../../wiki/Localização-e-mapa-de-trajeto)
- [Combinações de cliques](../../wiki/Combinações-de-cliques)
- [Alarme de fora de alcance](../../wiki/Alarme-de-fora-de-alcance)
- [Perguntas frequentes](../../wiki/Perguntas-frequentes)
- [Limitações](../../wiki/Limitações)

## Licença

Copyright © Altino Natalino. Todos os direitos reservados.
