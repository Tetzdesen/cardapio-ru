# Cardápio do RU — UFES Alegre

Lê `https://restaurante.alegre.ufes.br/cardapio` e manda o aviso do dia num chat do
Telegram e, se configurado, num grupo de WhatsApp. Roda como API no
[Render](https://render.com), plano grátis, com o agendador dentro da própria aplicação. **Sem banco, sem agendador externo.**

Spring Boot 3.5 · Java 21.

## Canais

Cada destino do aviso é um `Canal` (padrão Strategy): o `ClienteTelegram` e o
`ClienteWhatsapp` implementam a mesma interface, e o `DisparoTransacional` aplica a cada
canal configurado a mesma regra — decide o que é novidade, envia, grava — sem saber qual
é qual. Canal sem configuração fica de fora; sem nenhum, o envio falha com código `3`.

| canal | liga com | marcação |
|---|---|---|
| Telegram | `TELEGRAM_TOKEN`, `TELEGRAM_CHAT_ID` | HTML |
| WhatsApp | `WHATSAPP_URL`, `WHATSAPP_API_KEY`, `WHATSAPP_CHAT_ID` | `*negrito*`, convertida na saída |

O WhatsApp passa pelo [WAHA](whatsapp/README.md), um servidor que você hospeda fora do
Render. Os canais falham sozinhos: o WhatsApp fora do ar não impede o Telegram de
receber, e o disparo seguinte manda só ao canal que ficou para trás.

Um canal novo é uma classe que implementa `Canal` e vira `@Component`; o disparo o
encontra sozinho.

## Como funciona

No Render, a API (perfil `web`) anuncia sozinha — ver "Hospedar no Render". O mesmo jar
também faz um disparo de uma tacada só pela linha de comando, que é o que o botão manual
do Actions roda e commita de volta o arquivo de estado quando algo mudou. O que precisa sobreviver entre uma execução e a seguinte — quais
refeições já foram anunciadas — vive em `estado/ultimo-envio.json`, versionado com o
projeto.

```bash
java -jar target/cardapio-ru-*.jar --enviar --refeicoes almoco,jantar --vazio-silencioso
```

| argumento | padrão | o que faz |
|---|---|---|
| `--enviar` | — | manda aos canais configurados e grava o estado |
| `--print` | — | só imprime o cardápio formatado; não envia nem grava |
| `--data AAAA-MM-DD` | hoje (fuso de Brasília) | a data do cardápio |
| `--refeicoes` | `almoco,jantar` | lista separada por vírgula, ou `todas` |
| `--campus` | `alegre` | `alegre`, `jeronimo`, ou vazio para tudo |
| `--vazio-silencioso` | desligado | não avisa no grupo quando não há cardápio |
| `--estado ARQUIVO` | `estado/ultimo-envio.json` | onde fica o estado de envio |

Escolha **um** entre `--enviar` e `--print`: sem isso o disparo recusa com código `2`, em
vez de adivinhar se você queria falar com o grupo. As duas grafias funcionam
(`--data=2026-09-16` e `--data 2026-09-16`), como no `ru_bot.py`.

## Códigos de saída

É o código que faz o job do Actions ficar vermelho — o log só explica o porquê.

| código | significado |
|---|---|
| `0` | sucesso, **inclusive** "nada a enviar" e "dia sem cardápio publicado" |
| `2` | erro de uso: argumento faltando, data mal formada, refeição desconhecida |
| `3` | um canal recusou a mensagem (Telegram ou WhatsApp), ou nenhum está configurado |
| `4` | página irreconhecível — o site provavelmente mudou |
| `5` | site da UFES inacessível depois de esgotadas as tentativas |
| `6` | o estado não pôde ser gravado depois de um envio confirmado |

**Nenhuma falha de origem vira `0`.** Era esse o buraco que o código `4` do bot antigo
existia para tapar: sem ele, uma reforma no site da UFES deixaria o bot mudo sem ninguém
perceber. E o contrário também vale: um dia sem novidade **não** pode ficar vermelho, ou
todo mundo aprende a ignorar o vermelho.

> **Data de fim de semana sai com `4`, não com `0`.** Para uma data que o site nunca
> publicou, o Plone devolve a moldura institucional — texto demais para parecer página
> vazia, sem nenhum cabeçalho de refeição, sem dizer que não há cardápio. O parser não
> distingue isso de um site reformado. É o mesmo comportamento do `ru_bot.py`, está
> **aceito e fixado por teste** (`DataNaoPublicadaTest`), e corrigir exige heurística nova.
> O agendamento roda seg–sex, então o anúncio nunca passa por aí.

## Estado

Uma linha por `(data, refeição)`, com a assinatura do conteúdo anunciado:

```json
{
  "2026-09-15|almoco": "3697e402437c133a",
  "2026-09-15|jantar": "459106b701d9f528",
  "2026-09-15|jantar|whatsapp": "459106b701d9f528"
}
```

Cada canal tem a sua entrada. A do Telegram, que veio primeiro, fica sem sufixo; as dos
outros levam o nome do canal no fim.

É o mesmo formato que o `ru_bot.py` gravava, byte a byte — por isso o arquivo que já
estava no repositório continua valendo e a virada não reanunciou nada. A chave **não**
depende de quais refeições o disparo pediu: é isso que faz disparos que pedem refeições
diferentes reconhecerem o que têm em comum.

Entradas mais velhas que 30 dias são podadas na gravação. Entrada ilegível é descartada
sem erro, e a refeição volta a contar como inédita. **Arquivo ausente é o caso normal da
primeira execução**, não é falha.

O arquivo só é escrito depois de o canal confirmar, e só com as refeições confirmadas.
Se o envio falhar no meio, o que já chegou ao grupo fica registrado — reenviar amanhã uma
refeição que já chegou é pior do que deixar a falha visível no código de saída.

## Hospedar no Render

O `render.yaml` descreve o serviço. Uma vez só:

1. No Render, **New → Blueprint**, aponte para este repositório.
2. Preencha `TELEGRAM_TOKEN` (do [@BotFather](https://t.me/BotFather)) e
   `TELEGRAM_CHAT_ID` (grupo é negativo: `-1001234567890`; para descobrir, mande uma
   mensagem para o bot e abra `https://api.telegram.org/bot<TOKEN>/getUpdates`).
3. No log do deploy, procure a linha `Started CardapioRuApplication`. Teste com
   `curl https://<servico>.onrender.com/api/v1/cardapios`.

O anúncio sai de segunda a sexta, no fuso `America/Sao_Paulo` (`agenda.*` em
`application.yml`):

| horário | o que faz |
|---|---|
| **05h40** | anuncia o desjejum |
| **10h30** | anuncia o almoço |
| **16h30** | anuncia o jantar |

Cada refeição chega numa mensagem própria, um pouco antes de o RU abrir para ela.

Entre um anúncio e outro, a **vigia** reconfere o site a cada 20 minutos, das 6h às
19h59 (`agenda.vigia`). Se uma refeição que já foi para o grupo mudar depois, ela volta
com o aviso "⚠️ O cardápio mudou desde o último aviso." A que ainda não foi anunciada
fica para o seu horário: a vigia não antecipa nada, e em dia sem cardápio fica quieta.

Só liga com `AGENDA_ATIVA=true`, que o `render.yaml` define: a API local e os testes não
falam com o grupo sozinhos.

### Por que a API se pinga

O plano grátis desliga o serviço depois de 15 minutos sem requisição, e serviço desligado
não dispara nada. A cada 10 minutos a aplicação chama o próprio `/actuator/health` pela
URL pública (`RENDER_EXTERNAL_URL`, que o Render injeta). Precisa ser a URL pública: só o
que passa pela borda do Render conta como tráfego. Um serviço ligado o mês inteiro cabe
nas 750 horas grátis.

### O que se perde por ser grátis

O estado fica no disco do contêiner e **some quando ele reinicia** (deploy, manutenção do
Render). Como cada refeição só é pedida uma vez por dia, isso não faz nada chegar de novo.
Não há reenvio de falha: se o site da UFES estiver fora na hora de uma refeição, o erro
fica no log do Render e aquela refeição não é anunciada.

### O workflow do Actions

Ficou só como botão manual (Actions → *Cardápio do RU* → **Run workflow**), sem
`schedule`. Ele lê o estado do repositório, não o do Render, então pode repetir o que a
API já mandou — use só se o Render estiver fora. Ele precisa dos secrets `TELEGRAM_TOKEN`
e `TELEGRAM_CHAT_ID`.

## Rodar local

```bash
mvn verify                                    # compila e roda os testes
java -jar target/cardapio-ru-*.jar --print    # confere o cardápio sem enviar nada
```

Nenhuma variável é obrigatória para `--print`. Para `--enviar`, defina `TELEGRAM_TOKEN` e
`TELEGRAM_CHAT_ID`.

| variável | para quê |
|---|---|
| `TELEGRAM_TOKEN`, `TELEGRAM_CHAT_ID` | o envio pelo Telegram |
| `WHATSAPP_URL`, `WHATSAPP_API_KEY`, `WHATSAPP_CHAT_ID` | o envio pelo WhatsApp (opcional) |
| `WHATSAPP_SESSAO` | sessão do WAHA (padrão `default`) |
| `ESTADO` | caminho do estado (padrão `estado/ultimo-envio.json`) |
| `API_TOKEN` | segredo que o `POST /notificacoes` exige, se você subir a API |
| `PORT` | porta HTTP da API (padrão `8080`) |

O resto — URL base, User-Agent, timeout, tentativas, retenção, cache — está em
`application.yml` e raramente precisa mudar.

## A API HTTP

É o modo que roda no Render (perfil `web`): consulta em JSON, disparo manual e o
agendador.

```bash
java -jar target/cardapio-ru-*.jar --spring.profiles.active=web
```

| método | caminho | autenticação | o que faz |
|---|---|---|---|
| `GET` | `/api/v1/cardapios` | aberta | cardápio de hoje (fuso de Brasília) |
| `GET` | `/api/v1/cardapios/{data}` | aberta | cardápio da data, em `AAAA-MM-DD` |
| `POST` | `/api/v1/notificacoes` | token | decide o que é novidade, envia e relata |
| `GET` | `/actuator/health` | aberta | saúde da aplicação |

Os dois `GET` aceitam `?refeicoes=almoco,jantar` e `?campus=alegre`. "Hoje" é a **ausência
da data**, não um segmento `/hoje`: seria um valor que não é uma data no mesmo lugar onde
datas aparecem.

```bash
curl 'http://localhost:8080/api/v1/cardapios?campus=alegre'
curl -X POST http://localhost:8080/api/v1/notificacoes \
  -H "Authorization: Bearer $API_TOKEN" -H 'Content-Type: application/json' \
  -d '{"refeicoes":"almoco,jantar","campus":"alegre","silencioso":true}'
```

Com `"soAlteracoes":true` no corpo, o disparo só reenvia a refeição que já foi anunciada
e mudou desde então — é o que a vigia do agendador faz.

Status: `200` cardápio entregue (inclusive vazio) ou disparo sem novidade; `400` parâmetro
inválido; `401` sem token ou token errado; `502` página irreconhecível ou canal
recusou (`causa` diz qual: `TELEGRAM_RECUSOU`, `WHATSAPP_RECUSOU`); `503` o estado não
pôde ser gravado; `504` site inacessível.

Disparos simultâneos no mesmo processo — o agendador e uma chamada HTTP — esperam um pelo
outro. Sem `API_TOKEN` definido, como no `render.yaml`, o `POST` recusa tudo.

O `Dockerfile` empacota a API (perfil `web` já ativado, `curl` instalado para o
`HEALTHCHECK`). Monte um volume em `/app/estado`: sem ele o estado some a cada reinício e
o cardápio do dia seria reanunciado — a aplicação avisa isso no log da partida.

```bash
docker build -t cardapio-ru .
docker run -p 8080:8080 -v "$PWD/estado:/app/estado" \
  -e TELEGRAM_TOKEN=... -e TELEGRAM_CHAT_ID=... -e API_TOKEN=... cardapio-ru
```

## Testes

```bash
mvn test
```

Nenhum teste toca a rede nem escreve no estado de verdade. Todos rodam contra páginas
reais congeladas em `src/test/resources/fixtures/`:

| fixture | o que representa |
|---|---|
| `pagina-completa.html` | dia com as três refeições |
| `pagina-2026-09-16.html` | segunda página real, capturada ao vivo durante a virada |
| `pagina-vazia.html` | página que declara não haver cardápio |
| `pagina-quebrada.html` | site reformado |
| `pagina-nao-publicada.html` | data de fim de semana (a moldura institucional) |

`referencia-ru-bot.json` guarda a saída que o `ru_bot.py` produzia para essas mesmas
páginas — texto formatado e assinatura, byte a byte. É o que prova que o porte para Java
não mudou o que vai para o grupo nem quais refeições contam como alteradas. Se você mexer
no parser ou no formatador, é essa comparação que avisa.

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
- **Estrutura irreconhecível**: código `4`, e o job do Actions falha.

Para inspecionar o HTML cru:

```bash
curl -s https://restaurante.alegre.ufes.br/cardapio > pagina.html
```

## Cuidados

O RU não tem API pública, então isso é scraping. A consulta é servida de um cache curto em
memória, e o `User-Agent` é identificável e aponta para este repositório.

Falha passageira do servidor (timeout, 502) é repetida 3 vezes com espera dobrando; erro
definitivo (404, certificado inválido) não é repetido. O total de tentativas cabe num
orçamento de tempo configurado.

A verificação de TLS fica sempre ligada, e não há opção para desligá-la. O servidor da UFES
não envia o certificado intermediário da RNP/ICPEdu, então o `rnp-icpedu.pem` empacotado
com a aplicação completa a cadeia. Se ele faltar, a aplicação avisa **na partida**, não na
primeira consulta que falhar.
