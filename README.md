# Cardápio do RU — UFES Alegre

Lê `https://restaurante.alegre.ufes.br/cardapio` e manda o aviso do dia num chat do
Telegram. O anúncio roda dentro do próprio GitHub Actions: baixa, formata, envia, morre.
**Sem host, sem banco, sem infraestrutura.**

Spring Boot 3.5 · Java 21.

## Como funciona

O runner do Actions já é um computador com rede, e roda de graça no horário certo. Ele
constrói o jar, executa o disparo de uma tacada só e commita de volta o arquivo de estado
quando algo mudou. O que precisa sobreviver entre uma execução e a seguinte — quais
refeições já foram anunciadas — vive em `estado/ultimo-envio.json`, versionado com o
projeto.

```bash
java -jar target/cardapio-ru-*.jar --enviar --refeicoes almoco,jantar --vazio-silencioso
```

| argumento | padrão | o que faz |
|---|---|---|
| `--enviar` | — | manda ao Telegram e grava o estado |
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
| `3` | o Telegram recusou a mensagem |
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
  "2026-09-15|jantar": "459106b701d9f528"
}
```

É o mesmo formato que o `ru_bot.py` gravava, byte a byte — por isso o arquivo que já
estava no repositório continua valendo e a virada não reanunciou nada. A chave **não**
depende de quais refeições o disparo pediu: é isso que faz o anúncio das 05h40 e a
reconferência das 09h30 reconhecerem o que têm em comum.

Entradas mais velhas que 30 dias são podadas na gravação. Entrada ilegível é descartada
sem erro, e a refeição volta a contar como inédita. **Arquivo ausente é o caso normal da
primeira execução**, não é falha.

O arquivo só é escrito depois de o Telegram confirmar, e só com as refeições confirmadas.
Se o envio falhar no meio, o que já chegou ao grupo fica registrado — reenviar amanhã uma
refeição que já chegou é pior do que deixar a falha visível no código de saída.

## Agendar (GitHub Actions)

Settings → Secrets and variables → Actions → New repository secret: `TELEGRAM_TOKEN` (do
[@BotFather](https://t.me/BotFather)) e `TELEGRAM_CHAT_ID` (grupo é negativo:
`-1001234567890`). Para pegar o `chat_id`: mande uma mensagem para o bot e abra
`https://api.telegram.org/bot<TOKEN>/getUpdates`.

O anúncio sai de segunda a sexta:

| horário (BRT) | o que costuma acontecer |
|---|---|
| **05h40** | anuncia o cardápio do dia — desjejum, almoço e jantar |
| **09h30** | reconfere almoço e jantar, e só fala se algo mudou |

### Quem garante o horário: o gatilho externo

O `schedule` do Actions **não serve para horário**: de 21 a 23/09 ele atrasou todas as seis
execuções entre 4h40 e 6h38 — sem perder nenhuma, mas nenhuma perto da hora. O
`workflow_dispatch`, ao contrário, vira run no mesmo segundo. Por isso quem dispara nos
horários é o [cron-job.org](https://cron-job.org), chamando a API do GitHub.

São dois jobs lá, com fuso **`America/Sao_Paulo`**, **segunda a sexta**, e o alerta de
falha por e-mail **ligado**:

| job | horário | corpo |
|---|---|---|
| manhã | 05:40 | `{"ref":"main","inputs":{"previsto":"08:40Z"}}` |
| reconferência | 09:30 | `{"ref":"main","inputs":{"previsto":"12:30Z"}}` |

Os dois com:

```
POST https://api.github.com/repos/Tetzdesen/cardapio-ru/actions/workflows/cardapio.yml/dispatches
Authorization: Bearer <token>
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
```

Sucesso é **`204`**, sem corpo. O `previsto` é em UTC e só serve para medir o atraso (ver
adiante): nada do envio lê esse valor. Isto é tudo o que existe fora do repositório — com
esta seção dá para recriar os jobs do zero.

### A reserva: o `schedule` do Actions

Os dois `cron` do workflow (`40 8` e `30 12`, em UTC) continuam lá, nos mesmos horários.
No dia normal eles chegam horas depois do gatilho externo, encontram o estado já gravado e
terminam em segundos sem enviar nada. No dia em que o externo falha, **anunciam com
atraso** — pior que na hora, melhor que nunca. Cada refeição chega uma vez só, venha de
onde vier a execução: quem suprime a repetição é o estado, não a ordem das execuções.

### Que refeições cada execução pede

Não depende de quem disparou, e sim da **hora da execução em Brasília**:

- antes das **08h** → desjejum, almoço e jantar;
- a partir das 08h → almoço e jantar, **exceto** se nada de hoje foi anunciado ainda — aí
  volta a pedir o desjejum, para que uma manhã perdida não custe o desjejum em silêncio.

Mudar o horário de um gatilho não exige mexer em mais nada, desde que o da manhã continue
antes das 08h e o da reconferência depois.

### Quando os dois caminhos falham

Actions → *Cardápio do RU* → **Run workflow**, sem preencher nada. O clique segue a mesma
regra de hora acima e envia só o que ficou para trás; clicar duas vezes não repete nada.

### A credencial do gatilho externo

Token *fine-grained* do GitHub, criado pelo mantenedor em Settings → Developer settings →
Personal access tokens → Fine-grained tokens:

- **Repository access**: só `Tetzdesen/cardapio-ru`;
- **Permissions**: só **Actions: Read and write** (o `Metadata: Read` vem obrigatório);
  nada de `Contents`, `Secrets` ou administração;
- **Validade**: um ano. **Expira em: _(anotar ao criar)_.**

Ele fica guardado **só no cron-job.org**, no cabeçalho dos dois jobs — não é segredo do
repositório. Quem o obtiver consegue disparar, cancelar ou reexecutar runs deste workflow,
e nada além disso: não lê o `TELEGRAM_TOKEN`, não faz push e não alcança outro repositório.
Disparos a mais são absorvidos pelo estado.

- **Renovar**: antes da data acima, gere um token novo com as mesmas permissões, troque o
  cabeçalho nos dois jobs, rode o *Test run* de cada um (tem que dar `204`), revogue o
  antigo e atualize a data aqui.
- **Revogar** (vazou, ou não é mais usado): apague o token na mesma tela do GitHub. A
  reserva continua anunciando, com atraso, até existir um novo.

### Onde ver o atraso

Toda run — inclusive a que falha — escreve no resumo (a página da run, abaixo do grafo dos
passos) uma linha assim:

| origem | previsto | real | atraso | refeições | enviou |
|---|---|---|---|---|---|
| externo | 08:40Z | 08:41Z | 0h01 | desjejum,almoco,jantar | sim |

`origem` é `externo` (veio do cron-job.org), `schedule` (a reserva) ou `manual` (o botão,
sem atraso a medir). Horários em UTC. Ver `schedule` com `enviou: sim` num dia útil quer
dizer que o gatilho externo não funcionou naquele horário.

### Quando o alerta do cron-job.org chegar

O e-mail diz que a chamada não deu `204`. Pelo código:

- **`401`** — token expirado ou revogado. Renove como acima.
- **`404`** — URL errada, workflow renomeado, ou token sem acesso a este repositório.
- **`422`** — corpo inválido: `ref` inexistente ou input que o workflow não declara.
- **`5xx`** ou tempo esgotado — instabilidade do GitHub; em geral passa sozinho.

Em qualquer caso a reserva anuncia horas depois. Se o horário importa naquele dia, use o
**Run workflow**.

### O resto do workflow

O workflow precisa de `contents: write`: é ele que commita `estado/ultimo-envio.json`
quando muda, com `[skip ci]` na mensagem. Execução que não envia nada não gera commit. Se
`main` andou desde o checkout, o push rebaseia e tenta de novo, em vez de perder o estado.

Duas execuções não se sobrepõem por causa do `concurrency: group: cardapio`, e nenhuma é
descartada — cancelar perderia o envio que a primeira ainda não gravou. **É daí que vem a
garantia de envio único**, já que não há mais banco com índice para dar essa garantia.

O job tem teto de **10 minutos** (o ciclo normal leva menos de 40 segundos). Sem ele, uma
execução pendurada seguraria o grupo de `concurrency` por até seis horas, e com ele o
disparo seguinte.

## Rodar local

```bash
mvn verify                                    # compila e roda os testes
java -jar target/cardapio-ru-*.jar --print    # confere o cardápio sem enviar nada
```

Nenhuma variável é obrigatória para `--print`. Para `--enviar`, defina `TELEGRAM_TOKEN` e
`TELEGRAM_CHAT_ID`.

| variável | para quê |
|---|---|
| `TELEGRAM_TOKEN`, `TELEGRAM_CHAT_ID` | o envio |
| `ESTADO` | caminho do estado (padrão `estado/ultimo-envio.json`) |
| `API_TOKEN` | segredo que o `POST /notificacoes` exige, se você subir a API |
| `PORT` | porta HTTP da API (padrão `8080`) |

O resto — URL base, User-Agent, timeout, tentativas, retenção, cache — está em
`application.yml` e raramente precisa mudar.

## A API HTTP (opcional)

A aplicação também serve HTTP, sobre o mesmo arquivo de estado. **Ela não participa do
agendamento** — existe para uso local, para um disparo manual, ou para hospedar depois,
se algum dia for útil ter o cardápio como JSON.

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

Status: `200` cardápio entregue (inclusive vazio) ou disparo sem novidade; `400` parâmetro
inválido; `401` sem token ou token errado; `502` página irreconhecível ou Telegram
recusou; `503` o estado não pôde ser gravado; `504` site inacessível.

**Chamadas simultâneas ao disparo podem enviar duas vezes** — não há reserva antes do
envio. Quem chama serializa; é o que o agendamento faz.

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
