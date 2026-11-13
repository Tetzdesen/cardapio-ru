# Deploy e configuração

[← voltar ao README](../README.md)

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
[Estado](funcionamento.md#estado)). Para criar:

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

Cada tipo de disparo alerta no máximo uma vez a cada 3 horas, para a vigia e a
recuperação não repetirem o mesmo alerta com o site fora do ar. Quando a recuperação
consegue, chega `✅ o anúncio (almoco) saiu na nova tentativa`; quando passa do prazo sem
conseguir, `❌ o anúncio (almoco) não saiu hoje`.

### Por que a API se pinga

O plano grátis desliga o serviço depois de 15 minutos sem requisição, e serviço desligado
não dispara nada. A cada 10 minutos a aplicação chama o próprio `/actuator/health` pela URL
pública (`RENDER_EXTERNAL_URL`, que o Render injeta). Precisa ser a URL pública: só o que
passa pela borda do Render conta como tráfego. Um serviço ligado o mês inteiro cabe nas
750 horas grátis.

### Limites do plano grátis

- Se o site da UFES ficar fora do ar até o prazo da refeição, ela não é anunciada naquele
  dia. A falha vai ao log e ao chat de alerta.
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
