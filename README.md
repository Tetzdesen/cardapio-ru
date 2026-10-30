# Cardápio do RU — UFES Alegre

Bot que lê o cardápio do Restaurante Universitário em
`https://restaurante.alegre.ufes.br/cardapio` e anuncia cada refeição num grupo do
Telegram e, se configurado, num grupo de WhatsApp — com o horário da unidade, o aviso
quando o cardápio muda e o aviso dos dias em que não vai ter RU.

Roda como uma API Spring Boot no [Render](https://render.com), plano grátis, com o
agendador dentro da própria aplicação. **Sem banco e sem agendador externo.**

Spring Boot 3.5 · Java 21.

```
🚫 *Amanhã não vai ter RU* — sábado, 24/10
📍 _Alegre_

🌙 *Jantar* — sexta-feira, 23 de Outubro de 2026
📍 _Alegre e Jerônimo Monteiro_
⚠️ Alegre: 17h15 às 18h30 *(horário reduzido)*

🍗 *Prato Proteico*
• Sassami à Milanesa (CG)

🧃 *Suco*
• Acerola 🍒

🔗 ver no site: https://restaurante.alegre.ufes.br/cardapio/2026-10-23
```

## Sumário

- [Como funciona](#como-funciona)
- [O que vai ao grupo](#o-que-vai-ao-grupo)
- [Canais](#canais)
- [Hospedar no Render](#hospedar-no-render)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Estado](#estado)
- [A API HTTP e a página](#a-api-http-e-a-página)
- [Linha de comando](#linha-de-comando)
- [Rodar local](#rodar-local)
- [Estrutura do código](#estrutura-do-código)
- [Testes](#testes)
- [Se o parser quebrar](#se-o-parser-quebrar)
- [Cuidados](#cuidados)

## Como funciona

```
            agenda (05h40, 10h30, 16h30) e vigia (a cada 20 min)
                                  │
                                  ▼
 site do RU ──► cardápio do dia ──┐
 (Plone/UFES)                     ├─► o que é novidade? ──► Telegram / WhatsApp
 página inicial ──► avisos ───────┘   (estado de envio)          │
  (horário especial, dia fechado)            ▲                   │
                                             └── grava o que ────┘
                                                 o canal confirmou
```

1. **Coleta.** Baixa a página do cardápio da data e extrai as refeições, cada uma com as
   suas seções (entrada, prato proteico, sobremesa...). Baixa também as notícias da
   página inicial que falam de horário ou atendimento e tira delas as exceções: horário
   reduzido, refeição sem atendimento, dia fechado.
2. **Decisão.** Compara cada refeição com o que já foi anunciado (o [estado](#estado)):
   inédita vai, inalterada fica de fora, alterada volta com aviso de mudança.
3. **Envio.** Manda a cada canal configurado e grava só o que o canal confirmou.

No Render, quem dispara é o `Agendador`, dentro da própria API. O mesmo jar também roda
pela [linha de comando](#linha-de-comando), para conferir o cardápio ou anunciar à mão.

## O que vai ao grupo

### Agenda

O anúncio sai no fuso `America/Sao_Paulo` (`agenda.*` em `application.yml`):

| horário | dias | o que faz |
|---|---|---|
| **05h40** | seg–sex | anuncia o desjejum |
| **10h30** | seg–sáb | anuncia o almoço (sábado só tem almoço) |
| **16h30** | seg–sex | anuncia o jantar |
| a cada 20 min, 6h–19h59 | seg–sex | **vigia**: reanuncia o que mudou |

Cada refeição chega numa mensagem própria, um pouco antes de o RU abrir para ela: quem lê
o aviso do jantar de manhã já esqueceu quando chega a hora. Todos os disparos da agenda
pedem o campus `alegre`.

A **vigia** reconfere o site entre um anúncio e outro. Se uma refeição que já foi para o
grupo mudar depois, ela volta com "⚠️ O cardápio mudou desde o último aviso." A que ainda
não foi anunciada fica para o seu horário: a vigia não antecipa nada e, em dia sem
cardápio, fica quieta.

### Horário de funcionamento

Cada refeição vem com o horário da unidade, logo abaixo do campus: `🕒` no horário
regular, `⚠️ ... (horário reduzido)` quando um aviso o encurta, `🚫 sem atendimento`
quando a unidade não abre.

O horário regular fica no código (`QuadroDeHorarios`), copiado da página de
funcionamento do RU, porque muda uma vez por semestre, se tanto:

| refeição | Alegre | Jerônimo Monteiro |
|---|---|---|
| desjejum | 06h45 às 08h00 | 07h00 às 08h00 |
| almoço | 11h00 às 13h30 | 11h30 às 13h00 |
| jantar | 17h15 às 19h30 | 17h15 às 18h30 |
| almoço de sábado | 11h00 às 12h30 | 11h30 às 12h30 |

O que muda de uma semana para outra vem dos avisos da página inicial, lidos de novo a
cada 30 minutos. Na falha (site fora, aviso ilegível), vale o horário regular: o
horário enfeita o anúncio e não pode segurar o cardápio.

O horário respeita o campus pedido: com `alegre`, o grupo não recebe o de Jerônimo
Monteiro. O horário fica fora da assinatura do estado — aviso de horário novo não é
cardápio novo e não reenvia o dia.

### Dias sem RU

Quando um aviso dá uma unidade como fechada o dia inteiro, o grupo é avisado duas vezes,
cada uma uma vez só por canal:

- **antes**, até 7 dias de antecedência, no primeiro anúncio depois de o aviso sair:
  `🚫 Amanhã não vai ter RU — sábado, 24/10` (ou `Não vai ter RU — segunda-feira, 26/10`);
- **no próprio dia**: `🚫 Hoje não tem RU — sábado, 24/10`.

Refeição sem atendimento não manda o cardápio, mesmo que o site o tenha publicado. No dia
sem RU fica só o aviso do dia; no dia fechado só em parte vai `🚫 Sem jantar hoje` no
lugar do cardápio do jantar. Como essa refeição não é gravada, se o aviso for retirado o
cardápio ainda sai normalmente. Num dia fechado, a falta de página de cardápio também não
conta como falha.

O aviso do RU é entendido em dois formatos:

- **Lista por unidade e por dia**, como o RU publica os horários especiais:

  ```
  📍 RU Alegre
  🗓️ Sábado (24/10/2026)
  🚫 Fechado durante todo o dia
  🗓️ Segunda-feira (26/10/2026)
  🍲 Jantar: Sem atendimento
  ```

- **Texto corrido**, só para o dia inteiro: "nos dias 30 (sexta-feira) e 31 de outubro
  (sábado) não haverá atendimento". A frase precisa fechar o RU e não citar refeição; a
  data pode ser numérica ou por extenso, e data entre parênteses não conta ("originalmente
  assinalado a 28 de outubro" não é dia fechado).

Refeição suspensa em texto corrido ("nesse dia não será servido o jantar") fica de fora,
porque a data costuma estar em outra frase: melhor anunciar o horário regular do que um
horário adivinhado.

### Dia sem cardápio

Na agenda, dia sem cardápio publicado não manda nada (`silencioso`). No disparo manual não
silencioso, vai `Sem cardápio publicado para hoje.` — a menos que o dia seja sem RU.

## Canais

Cada destino é um `Canal` (padrão Strategy): o `ClienteTelegram` e o `ClienteWhatsapp`
implementam a mesma interface, e o `DisparoTransacional` aplica a cada canal configurado a
mesma regra — decide o que é novidade, envia, grava — sem saber qual é qual.

| canal | liga com | marcação |
|---|---|---|
| Telegram | `TELEGRAM_TOKEN`, `TELEGRAM_CHAT_ID` | HTML |
| WhatsApp | `WHATSAPP_URL`, `WHATSAPP_API_KEY`, `WHATSAPP_CHAT_ID` | `*negrito*`, convertida na saída |

- Canal sem configuração fica de fora; sem nenhum, o envio falha.
- Os canais falham sozinhos: o WhatsApp fora do ar não impede o Telegram de receber, e o
  disparo seguinte manda só ao canal que ficou para trás.
- Mensagens longas são partidas em até 4000 caracteres, sem cortar uma refeição ao meio.
- O WhatsApp passa pelo [WAHA](whatsapp/README.md), um servidor que você hospeda fora do
  Render (uma VM grátis da Oracle Cloud serve).

Um canal novo é uma classe que implementa `Canal` e vira `@Component`; o disparo o encontra
sozinho.

## Hospedar no Render

O `render.yaml` descreve o serviço. Uma vez só:

1. No Render, **New → Blueprint**, aponte para este repositório.
2. Preencha as variáveis (ver [Variáveis de ambiente](#variáveis-de-ambiente)):
   - `TELEGRAM_TOKEN`, do [@BotFather](https://t.me/BotFather);
   - `TELEGRAM_CHAT_ID`, o id do grupo (negativo: `-1001234567890`). Para descobrir,
     coloque o bot no grupo, mande uma mensagem e abra
     `https://api.telegram.org/bot<TOKEN>/getUpdates`;
   - `TELEGRAM_CHAT_ALERTA`, o id do seu chat privado com o bot, que aparece no mesmo
     `getUpdates` depois de você mandar uma mensagem a ele;
   - `ESTADO_GITHUB_TOKEN`, para o estado sobreviver aos deploys (ver abaixo);
   - as do WhatsApp, se for usar.
3. Crie o ramo `estado` no GitHub, a partir do `main`
   (`git push origin main:refs/heads/estado`).
4. No log do deploy, procure a linha `Started CardapioRuApplication`. Teste com
   `curl https://<servico>.onrender.com/api/v1/cardapios`.

O anúncio só liga com `AGENDA_ATIVA=true`, que o `render.yaml` define: a API local e os
testes não falam com o grupo sozinhos.

### O token do estado

No Render grátis, o disco do contêiner some a cada deploy e reinício. O
`ESTADO_GITHUB_TOKEN` faz o estado ser espelhado no ramo `estado` deste repositório (ver
[Estado](#estado)). Para criar:

1. GitHub → **Settings → Developer settings → Fine-grained tokens → Generate new token**.
2. *Repository access*: **Only select repositories**, só este repositório.
3. *Permissions → Repository permissions → Contents*: **Read and write**.
4. Copie o token para a variável no Render.

### Alertas de falha

No Render não há código de saída nem job vermelho: um anúncio que falha (site fora do ar,
página irreconhecível, canal recusando) só apareceria no log. Com `TELEGRAM_CHAT_ALERTA`, o
próprio bot manda o motivo a esse chat:

```
⚠️ Cardápio do RU: o anúncio (almoco) falhou
nenhum bloco de refeicao reconhecido em https://restaurante.alegre.ufes.br/cardapio/...
```

Cada tipo de disparo alerta no máximo uma vez a cada 3 horas, para a vigia não repetir o
mesmo alerta a cada 20 minutos com o site fora do ar.

### Por que a API se pinga

O plano grátis desliga o serviço depois de 15 minutos sem requisição, e serviço desligado
não dispara nada. A cada 10 minutos a aplicação chama o próprio `/actuator/health` pela URL
pública (`RENDER_EXTERNAL_URL`, que o Render injeta). Precisa ser a URL pública: só o que
passa pela borda do Render conta como tráfego. Um serviço ligado o mês inteiro cabe nas
750 horas grátis.

### Limites do plano grátis

- Não há reenvio de falha: se o site da UFES estiver fora na hora de uma refeição, aquela
  refeição não é anunciada. A falha vai ao log e ao chat de alerta.
- Sem `ESTADO_GITHUB_TOKEN`, um reinício esquece o que já foi anunciado. Cada refeição só é
  pedida uma vez por dia, mas o aviso de dia sem RU pode se repetir e a vigia deixa de
  perceber mudanças nas refeições anunciadas antes do reinício.

## Variáveis de ambiente

| variável | obrigatória | para quê |
|---|---|---|
| `TELEGRAM_TOKEN`, `TELEGRAM_CHAT_ID` | para o Telegram | o bot e o grupo |
| `TELEGRAM_CHAT_ALERTA` | não | chat privado que recebe as falhas do anúncio agendado |
| `WHATSAPP_URL`, `WHATSAPP_API_KEY`, `WHATSAPP_CHAT_ID` | para o WhatsApp | o WAHA e o grupo (`...@g.us`) |
| `WHATSAPP_SESSAO` | não | sessão do WAHA (padrão `default`) |
| `ESTADO_GITHUB_TOKEN` | no Render, sim | espelha o estado no ramo `estado` |
| `ESTADO_GITHUB_REPO` | não | repositório do espelho (padrão `Tetzdesen/cardapio-ru`) |
| `ESTADO_GITHUB_RAMO` | não | ramo do espelho (padrão `estado`) |
| `ESTADO` | não | arquivo do estado (padrão `estado/ultimo-envio.json`) |
| `AGENDA_ATIVA` | não | `true` liga o anúncio agendado (o `render.yaml` liga) |
| `API_TOKEN` | não | segredo que o `POST /api/v1/notificacoes` exige |
| `PORT` | não | porta HTTP da API (padrão `8080`; o Render injeta) |
| `JAVA_OPTS` | não | opções da JVM na imagem Docker |

O resto — URL do site, User-Agent, timeout, tentativas, retenção, cache, horários da
agenda — está em `application.yml` e raramente precisa mudar.

## Estado

O que já foi anunciado fica num JSON, uma linha por `(data, refeição, canal)`, com a
assinatura do conteúdo anunciado:

```json
{
  "2026-10-23|almoco": "3697e402437c133a",
  "2026-10-23|jantar": "459106b701d9f528",
  "2026-10-23|jantar|whatsapp": "459106b701d9f528",
  "2026-10-24|sem-ru": "antes"
}
```

- **Cada canal tem a sua entrada.** A do Telegram, que veio primeiro, fica sem sufixo; as
  dos outros levam o nome do canal no fim.
- **A chave não depende de quais refeições o disparo pediu.** É isso que faz disparos que
  pedem refeições diferentes reconhecerem o que têm em comum.
- **`sem-ru`** guarda até onde o dia sem RU já foi avisado: `antes` ou `hoje`.
- **Só se grava o que o canal confirmou.** Se o envio falhar no meio, o que já chegou ao
  grupo fica registrado — reenviar uma refeição que já chegou é pior do que deixar a
  falha visível.
- Entradas mais velhas que 30 dias são podadas na gravação. Entrada ilegível é descartada
  sem erro, e a refeição volta a contar como inédita. Arquivo ausente é o caso normal da
  primeira execução, não é falha.

É o mesmo formato que o `ru_bot.py`, a versão em Python que veio antes, gravava byte a
byte, e a assinatura é calculada sobre o mesmo texto que ele produzia.

### O espelho no GitHub

Com `ESTADO_GITHUB_TOKEN`, o arquivo em disco vira cópia de trabalho do que está no ramo
`estado`: a primeira leitura depois de subir traz o arquivo de lá, e cada gravação vira
um commit nesse ramo (`estado: cardápio anunciado`).

- Não é o `main` de propósito: commit no `main` é deploy no Render, e um deploy por
  anúncio reiniciaria o serviço.
- Se o GitHub falhar, o disco continua valendo e a gravação seguinte leva tudo junto —
  sem sobrescrever o ramo com um disco que não o leu.
- No log da partida, a aplicação diz onde o estado está (`estado espelhado em ...`).

Fora do Render, sem o token, o estado vive só no disco; na imagem Docker, monte um volume
em `/app/estado`.

## A API HTTP e a página

É o modo que roda no Render (perfil `web`): página, consulta em JSON, disparo manual,
saúde e o agendador.

| método | caminho | autenticação | o que faz |
|---|---|---|---|
| `GET` | `/` | aberta | página com o cardápio do dia, navegável por data |
| `GET` | `/api/v1/cardapios` | aberta | cardápio de hoje (fuso de Brasília) |
| `GET` | `/api/v1/cardapios/{data}` | aberta | cardápio da data, em `AAAA-MM-DD` |
| `POST` | `/api/v1/notificacoes` | `Bearer $API_TOKEN` | decide o que é novidade, envia e relata |
| `GET` | `/actuator/health` | aberta | saúde da aplicação |

### Consulta

Os dois `GET` aceitam `?refeicoes=almoco,jantar` (ou `todas`) e `?campus=alegre`. "Hoje" é
a **ausência da data**, não um segmento `/hoje`: seria um valor que não é uma data no
mesmo lugar onde datas aparecem.

```bash
curl 'https://<servico>.onrender.com/api/v1/cardapios/2026-10-23?campus=alegre'
```

```json
{
  "data": "2026-10-23",
  "origem": "https://restaurante.alegre.ufes.br/cardapio/2026-10-23",
  "publicado": true,
  "refeicoes": [
    {
      "refeicao": "Jantar",
      "campus": "Alegre e Jerônimo Monteiro",
      "dataPorExtenso": "sexta-feira, 23 de Outubro de 2026",
      "horarios": [
        { "unidade": "Alegre", "faixa": "17h15 às 18h30", "reduzido": true, "fechado": false }
      ],
      "secoes": [
        { "rotulo": "Prato Proteico", "itens": ["Sassami à Milanesa (CG)"] }
      ]
    }
  ],
  "semRu": [
    { "dia": "2026-10-24", "unidades": ["Alegre"] }
  ],
  "avisos": []
}
```

- `publicado` é `false` no dia sem cardápio, que responde `200`: ausência de cardápio não
  é falha.
- `semRu` são os dias, a partir da data consultada, que os avisos do RU dão como fechados.
  Num dia sem RU sem página de cardápio, a resposta é `200` com `publicado: false`, e não
  `502`.
- `avisos` traz o que o parser estranhou, como um rótulo de seção desconhecido.

A página em `/` usa essa mesma consulta: mostra as refeições em cards, com o horário de
cada unidade, e avisa quando o dia é sem RU.

### Disparo manual

```bash
curl -X POST https://<servico>.onrender.com/api/v1/notificacoes \
  -H "Authorization: Bearer $API_TOKEN" -H 'Content-Type: application/json' \
  -d '{"refeicoes":"almoco","campus":"alegre","silencioso":true}'
```

| campo | padrão | o que faz |
|---|---|---|
| `data` | hoje | a data, em `AAAA-MM-DD` |
| `refeicoes` | todas | lista separada por vírgula |
| `campus` | todos | `alegre`, `jeronimo` |
| `silencioso` | `true` | não avisa no grupo quando não há cardápio |
| `soAlteracoes` | `false` | só reenvia o que já foi anunciado e mudou (é o que a vigia faz) |

A resposta diz o que aconteceu: `enviadas`, `alteradas`, `omitidas` e os dias `semRu`
avisados. Disparo em que nada precisava ser enviado é `200` com `enviadas` vazio.

Disparos simultâneos no mesmo processo — o agendador e uma chamada HTTP — esperam um pelo
outro. Sem `API_TOKEN` definido, como no `render.yaml`, o `POST` recusa tudo com `401`.

### Status

| status | quando |
|---|---|
| `200` | cardápio entregue (inclusive vazio) ou disparo feito, com ou sem novidade |
| `400` | parâmetro inválido (data mal formada, refeição desconhecida) |
| `401` | sem token ou token errado |
| `502` | página irreconhecível ou canal recusou; `causa` diz qual |
| `503` | o estado não pôde ser gravado depois de um envio confirmado |
| `504` | site da UFES inacessível |

O corpo de erro traz `status`, `causa` (`ESTRUTURA_NAO_RECONHECIDA`, `TELEGRAM_RECUSOU`,
`WHATSAPP_RECUSOU`, `ORIGEM_INACESSIVEL`, ...) e `mensagem`.

## Linha de comando

Sem o perfil `web`, o jar é um disparo de uma tacada só: roda, faz o que foi pedido e sai.
Serve para conferir o cardápio antes de mexer em algo, ou anunciar à mão.

```bash
java -jar target/cardapio-ru-*.jar --print                       # confere sem enviar
java -jar target/cardapio-ru-*.jar --print --data 2026-10-23 --refeicoes todas
java -jar target/cardapio-ru-*.jar --enviar --refeicoes almoco --vazio-silencioso
```

| argumento | padrão | o que faz |
|---|---|---|
| `--enviar` | — | manda aos canais configurados e grava o estado |
| `--print` | — | só imprime o que iria ao grupo; não envia nem grava |
| `--data AAAA-MM-DD` | hoje (fuso de Brasília) | a data do cardápio |
| `--refeicoes` | `almoco,jantar` | lista separada por vírgula, ou `todas` |
| `--campus` | `alegre` | `alegre`, `jeronimo`, ou vazio para tudo |
| `--vazio-silencioso` | desligado | não avisa no grupo quando não há cardápio |
| `--estado ARQUIVO` | `estado/ultimo-envio.json` | onde fica o estado de envio |

Escolha **um** entre `--enviar` e `--print`: sem isso o disparo recusa com código `2`, em
vez de adivinhar se você queria falar com o grupo. As duas grafias funcionam
(`--data=2026-09-16` e `--data 2026-09-16`). O `--print` manda o cardápio para o stdout e o
log para o stderr, então `--print > arquivo` funciona.

### Códigos de saída

| código | significado |
|---|---|
| `0` | sucesso, **inclusive** "nada a enviar" e "dia sem cardápio publicado" |
| `2` | erro de uso: argumento faltando, data mal formada, refeição desconhecida |
| `3` | um canal recusou a mensagem (Telegram ou WhatsApp), ou nenhum está configurado |
| `4` | página irreconhecível — o site provavelmente mudou |
| `5` | site da UFES inacessível depois de esgotadas as tentativas |
| `6` | o estado não pôde ser gravado depois de um envio confirmado |

**Nenhuma falha de origem vira `0`**: sem isso, uma reforma no site da UFES deixaria o bot
mudo sem ninguém perceber. E o contrário também vale: um dia sem novidade não pode virar
erro, ou todo mundo aprende a ignorar o erro.

> **Data que o site nunca publicou sai com `4`, não com `0`.** Para essa data, o Plone
> devolve a moldura institucional — texto demais para parecer página vazia, sem nenhum
> cabeçalho de refeição, sem dizer que não há cardápio. O parser não distingue isso de um
> site reformado, e o comportamento está fixado por teste (`DataNaoPublicadaTest`). A
> exceção é o dia sem RU por aviso, em que a página sem cardápio é o esperado. Por isso a
> agenda só pede o sábado para o almoço, que o site publica; sábado sem cardápio e sem
> aviso vira alerta.

## Rodar local

Precisa de Java 21 e Maven.

```bash
mvn verify                                          # compila e roda os testes
java -jar target/cardapio-ru-*.jar --print          # confere o cardápio sem enviar nada
java -jar target/cardapio-ru-*.jar --spring.profiles.active=web   # sobe a API na 8080
```

Nenhuma variável é obrigatória para `--print` nem para subir a API. Para `--enviar`,
defina as do canal. A API local não anuncia sozinha (`AGENDA_ATIVA` é `false` por padrão).

### Docker

O `Dockerfile` empacota a API com o perfil `web` já ativado e um `HEALTHCHECK`. Monte um
volume em `/app/estado` ou defina `ESTADO_GITHUB_TOKEN`: sem nenhum dos dois o estado some
a cada reinício, e a aplicação avisa isso no log da partida.

```bash
docker build -t cardapio-ru .
docker run -p 8080:8080 -v "$PWD/estado:/app/estado" \
  -e TELEGRAM_TOKEN=... -e TELEGRAM_CHAT_ID=... -e API_TOKEN=... cardapio-ru
```

## Estrutura do código

`src/main/java/io/github/tetzdesen/cardapioru/`:

| pacote | o que tem |
|---|---|
| `agenda` | `Agendador` (os horários do anúncio, a vigia, o ping) e `Alerta` |
| `api` | os controllers, os corpos de resposta e o mapa de erros para status HTTP |
| `cli` | o disparo de uma tacada só e os códigos de saída |
| `coleta` | baixar o site (`ColetorDaUfes`, com retry e TLS), o parser do cardápio, o leitor de avisos e o `QuadroDeHorarios` |
| `config` | propriedades, segurança do `POST`, TLS e os avisos de partida |
| `dominio` | `BlocoRefeicao`, `Secao`, `Horario`, `DiaSemRu` |
| `erro` | as falhas que viram código de saída e status HTTP |
| `estado` | o arquivo de estado, o espelho no GitHub e a classificação inédita/alterada |
| `notificacao` | os canais, o formatador das mensagens e o `DisparoTransacional` |
| `servico` | `ServicoCardapio` (com cache curto) e `ServicoHorario` |

Outros arquivos:

| arquivo | para quê |
|---|---|
| `render.yaml` | o serviço no Render (Blueprint) |
| `Dockerfile` | a imagem da API |
| `whatsapp/` | `docker-compose` do WAHA e como ligá-lo ao bot ([README](whatsapp/README.md)) |
| `src/main/resources/static/index.html` | a página do cardápio |
| `src/main/resources/rnp-icpedu.pem` | o certificado intermediário que o site da UFES não envia |

## Testes

```bash
mvn test
```

Nenhum teste toca a rede nem escreve no estado de verdade: o site, o Telegram, o WhatsApp
e o GitHub são simulados. Os testes rodam contra páginas reais congeladas em
`src/test/resources/fixtures/`:

| fixture | o que representa |
|---|---|
| `pagina-completa.html` | dia com as três refeições |
| `pagina-2026-09-16.html` | segunda página real, capturada ao vivo |
| `pagina-vazia.html` | página que declara não haver cardápio |
| `pagina-quebrada.html` | site reformado |
| `pagina-nao-publicada.html` | data que o site nunca publicou (a moldura institucional) |
| `aviso-horario-2310-2610.html` | aviso em lista: horário reduzido e dias fechados por unidade |
| `aviso-sem-atendimento-3010-3110.html` | aviso em texto corrido, com datas por extenso |
| `aviso-suspensao-1010.html` | suspensão de um dia nas duas unidades |
| `aviso-ponto-facultativo-3010.html` | aviso que **não** fecha nada ("funcionará normalmente") |
| `aviso-suspensao-jantar-2905.html` | refeição suspensa em texto corrido, que fica de fora |

`referencia-ru-bot.json` guarda a saída que o `ru_bot.py` produzia para as páginas de
cardápio — texto formatado e assinatura, byte a byte. É o que prova que o porte para Java
não mudou quais refeições contam como alteradas. Se você mexer no parser ou no
formatador, é essa comparação que avisa.

## Se o parser quebrar

O site é um Plone da UFES e a estrutura muda de vez em quando. O parser **não depende de
classes CSS**: acha os cabeçalhos por regex (`Almoço (Alegre) - sexta-feira, ...`) e separa
as seções pela lista de rótulos em `Vocabulario.CATEGORIAS`.

Duas coisas avisam antes de virar problema:

- **Rótulo novo** ("Molho", "Prato Vegano"): a aplicação loga
  `rotulo de secao desconhecido: 'Molho'`, devolve o aviso no relato e **abre a seção
  assim mesmo** — o conteúdo continua saindo. Adicione o rótulo em `CATEGORIAS` para calar
  o aviso. A detecção usa a marcação do site (`<p><strong>Rótulo</strong></p>`), e não o
  formato do texto, porque itens e rótulos são lexicalmente idênticos ("Pirão", "Laranja").
- **Estrutura irreconhecível**: código `4` na linha de comando, `502` na API e alerta no
  agendador — a menos que o dia esteja fechado por aviso.

Para inspecionar o HTML cru:

```bash
curl -s https://restaurante.alegre.ufes.br/cardapio > pagina.html
```

Um aviso de horário num formato novo não quebra nada: no pior caso, o anúncio sai com o
horário regular. Para ensinar o formato, capture a notícia como fixture e acrescente o
caso em `HorariosTest`.

## Cuidados

O RU não tem API pública, então isso é scraping. A consulta é servida de um cache curto em
memória, os avisos são relidos no máximo a cada 30 minutos, e o `User-Agent` é
identificável e aponta para este repositório.

Falha passageira do servidor (timeout, 502) é repetida 3 vezes com espera dobrando; erro
definitivo (404, certificado inválido) não é repetido. O total de tentativas cabe num
orçamento de tempo configurado.

A verificação de TLS fica sempre ligada, e não há opção para desligá-la. O servidor da UFES
não envia o certificado intermediário da RNP/ICPEdu, então o `rnp-icpedu.pem` empacotado
com a aplicação completa a cadeia. Se ele faltar, a aplicação avisa **na partida**, não na
primeira consulta que falhar.

A comparação do `API_TOKEN` é em tempo constante, e uma credencial recusada vai ao log
sem o valor apresentado.
