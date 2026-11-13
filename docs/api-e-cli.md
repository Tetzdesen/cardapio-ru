# API HTTP e linha de comando

[← voltar ao README](../README.md)

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
