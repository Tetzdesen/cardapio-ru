package io.github.tetzdesen.cardapioru.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.coleta.Retentador;
import io.github.tetzdesen.cardapioru.coleta.Tempo;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.TelegramProperties;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Porte do {@code enviar_telegram} e do {@code _agrupar} do ru_bot.py. */
@Component
public class ClienteTelegram {

    /** Falha de envio, com as refeicoes que o Telegram chegou a confirmar. */
    public static class FalhaDeEnvio extends FalhaDeOrigem {

        private final transient List<String> confirmadas;

        public FalhaDeEnvio(String mensagem, List<String> confirmadas, Throwable causa) {
            super(Causa.TELEGRAM_RECUSOU, mensagem, causa);
            this.confirmadas = List.copyOf(confirmadas);
        }

        public List<String> confirmadas() {
            return confirmadas;
        }
    }

    /** O Telegram cai e volta, e limita requisicoes. 4xx fora do 429 e configuracao. */
    static final Set<Integer> STATUS_REPETIVEL = Set.of(429, 500, 502, 503, 504);

    private static final Logger log = LoggerFactory.getLogger(ClienteTelegram.class);

    private final TelegramProperties props;
    private final HttpClient cliente;
    private final Retentador retentador;
    private final ObjectMapper json = new ObjectMapper();

    // a outra construtora existe para o teste injetar cliente e retentador;
    // com duas, o Spring precisa que a de injecao seja apontada
    @Autowired
    public ClienteTelegram(TelegramProperties props, CardapioProperties cardapio) {
        this(props, HttpClient.newBuilder().connectTimeout(props.timeout()).build(),
                new Retentador(cardapio.tentativas(), cardapio.esperaBase(),
                        cardapio.esperaMaxima(), cardapio.orcamentoTotal(), props.timeout(),
                        Tempo.real()));
    }

    ClienteTelegram(TelegramProperties props, HttpClient cliente, Retentador retentador) {
        this.props = props;
        this.cliente = cliente;
        this.retentador = retentador;
    }

    /**
     * Junta pecas em mensagens de ate {@code limiteMensagem} caracteres, sem
     * partir uma peca ao meio -- e o que permite saber, na falha, quais refeicoes
     * ja chegaram.
     */
    record Mensagem(List<String> refeicoes, String texto) {
    }

    List<Mensagem> agrupar(List<Peca> pecas) {
        List<Mensagem> mensagens = new ArrayList<>();
        List<String> refeicoes = new ArrayList<>();
        String atual = "";

        for (Peca peca : pecas) {
            String candidato = atual.isEmpty() ? peca.texto() : atual + "\n\n" + peca.texto();
            if (!atual.isEmpty() && candidato.length() > props.limiteMensagem()) {
                mensagens.add(new Mensagem(List.copyOf(refeicoes), atual));
                refeicoes = new ArrayList<>();
                atual = peca.texto();
            } else {
                atual = candidato;
            }
            if (peca.refeicao() != null) {
                refeicoes.add(peca.refeicao());
            }
        }
        if (!atual.isBlank()) {
            mensagens.add(new Mensagem(List.copyOf(refeicoes), atual));
        }
        return mensagens;
    }

    /** Envia e devolve as refeicoes que o Telegram confirmou ter recebido. */
    public List<String> enviar(List<Peca> pecas) {
        if (!props.configurado()) {
            throw new FalhaDeEnvio("TELEGRAM_TOKEN e TELEGRAM_CHAT_ID nao estao definidos",
                    List.of(), null);
        }

        Set<String> confirmadas = new LinkedHashSet<>();
        for (Mensagem mensagem : agrupar(pecas)) {
            HttpResponse<String> resposta;
            try {
                resposta = postar(mensagem.texto());
            } catch (FalhaDeOrigem e) {
                throw new FalhaDeEnvio("erro de rede ao falar com o Telegram: " + e.getMessage(),
                        List.copyOf(confirmadas), e);
            }
            if (resposta.statusCode() >= 300) {
                throw new FalhaDeEnvio("Telegram respondeu " + resposta.statusCode() + ": "
                        + resposta.body(), List.copyOf(confirmadas), null);
            }
            confirmadas.addAll(mensagem.refeicoes());
        }
        return List.copyOf(confirmadas);
    }

    private HttpResponse<String> postar(String texto) {
        String url = props.urlBase() + "/bot" + props.token() + "/sendMessage";
        String corpo;
        try {
            corpo = json.writeValueAsString(Map.of(
                    "chat_id", props.chatId(),
                    "text", texto.strip(),
                    "parse_mode", "HTML",
                    "disable_web_page_preview", true));
        } catch (Exception e) {
            throw new IllegalStateException("nao foi possivel montar o corpo do envio", e);
        }

        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(props.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();

        return retentador.executar("Telegram",
                () -> cliente.send(pedido, HttpResponse.BodyHandlers.ofString()),
                r -> {
                    if (!STATUS_REPETIVEL.contains(r.statusCode())) {
                        return Retentador.Veredito.aceitar();
                    }
                    Duration pedida = esperaPedida(r);
                    return pedida != null
                            ? Retentador.Veredito.repetirApos(pedida)
                            : Retentador.Veredito.repetirAgora();
                });
    }

    /** Le o retry_after que o Telegram manda no 429. */
    Duration esperaPedida(HttpResponse<String> resposta) {
        try {
            JsonNode corpo = json.readTree(resposta.body());
            JsonNode valor = corpo.path("parameters").path("retry_after");
            if (!valor.isMissingNode() && valor.isNumber()) {
                return Duration.ofSeconds(valor.asLong());
            }
        } catch (Exception e) {
            log.debug("corpo do 429 nao era JSON legivel: {}", e.getMessage());
        }
        return resposta.headers().firstValue("Retry-After")
                .map(v -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(v.strip()));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }
}
