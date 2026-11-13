# Desenvolvimento

[← voltar ao README](../README.md)

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
| `agenda` | `Agendador` (os horários do anúncio, a vigia, a recuperação, o ping) e `Alerta` |
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
| `whatsapp/` | `docker-compose` do WAHA e como ligá-lo ao bot ([README](../whatsapp/README.md)) |
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
