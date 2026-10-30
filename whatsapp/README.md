# WAHA — o WhatsApp do bot

Servidor que mantém um número de WhatsApp conectado e recebe pedidos HTTP para mandar
mensagem. O bot no Render chama o WAHA; o WAHA fala com o WhatsApp.

> **API não oficial.** O WAHA entra como um "aparelho conectado", igual ao WhatsApp Web, e
> isso vai contra os termos do WhatsApp. Use um **chip separado**, nunca o seu número.

## Onde rodar

Fora do Render: a sessão fica em `sessions/` e precisa sobreviver a reinícios, senão o
QR code tem de ser escaneado de novo. Serve qualquer máquina sempre ligada com Docker e
IP público — uma VM grátis da Oracle Cloud, por exemplo. Libere as portas **80** e
**443** no firewall (na Oracle, também na *Security List* da rede). Em ARM, troque a
imagem para `devlikeapro/waha:noweb-arm` no `docker-compose.yml`.

## Subir

```bash
cp .env.example .env      # preencha domínio, chave e senhas
docker compose up -d
docker compose logs -f    # o Caddy avisa quando o certificado sair
```

## Conectar o número

1. Abra `https://<WAHA_DOMINIO>/dashboard` e entre com o usuário e senha do `.env`.
2. Crie e inicie a sessão `default`.
3. No celular do chip separado: WhatsApp → **Aparelhos conectados** → **Conectar um
   aparelho**, e escaneie o QR do painel. O status passa a `WORKING`.
4. Coloque esse número no grupo de amigos.

## Descobrir o ID do grupo e testar

```bash
curl -s https://$WAHA_DOMINIO/api/default/groups -H "X-Api-Key: $WAHA_API_KEY"
```

O ID termina em `@g.us`. Para testar o envio:

```bash
curl -X POST https://$WAHA_DOMINIO/api/sendText \
  -H "X-Api-Key: $WAHA_API_KEY" -H 'Content-Type: application/json' \
  -d '{"session":"default","chatId":"120363xxxxxxxx@g.us","text":"☕ *Teste* do cardápio"}'
```

O WhatsApp usa `*negrito*` e `_itálico_`, não HTML.

## Ligar ao bot

No Render, em *Environment* do serviço, preencha:

| variável | valor |
|---|---|
| `WHATSAPP_URL` | `https://<WAHA_DOMINIO>` |
| `WHATSAPP_API_KEY` | o mesmo `WAHA_API_KEY` do `.env` |
| `WHATSAPP_CHAT_ID` | o ID do grupo, terminado em `@g.us` |
| `WHATSAPP_SESSAO` | o nome da sessão no WAHA, se não for `default` |

O bot passa a mandar cada refeição aos dois canais. Para conferir antes, rode local com
as mesmas variáveis e `--enviar --refeicoes almoco`.

## Cuidados

- `sessions/` é a conexão com o WhatsApp: faça backup e não apague.
- Se o celular sair da conta ou o WhatsApp desconectar o aparelho, o status vira
  `SCAN_QR_CODE` e é preciso escanear de novo pelo painel.
