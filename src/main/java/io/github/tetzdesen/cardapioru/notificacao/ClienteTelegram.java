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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Porte do {@code enviar_telegram} do ru_bot.py: o canal original do aviso. */
@Component
@Order(1)
public class ClienteTelegram implements Canal {

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

    @Override
    public String nome() {
        return TELEGRAM;
    }

    @Override
    public boolean configurado() {
        return props.configurado();
    }

    List<Agrupador.Mensagem> agrupar(List<Peca> pecas) {
        return Agrupador.agrupar(pecas, props.limiteMensagem());
    }

    @Override
    public List<String> enviar(List<Peca> pecas) {
        if (!props.configurado()) {
            throw falha("TELEGRAM_TOKEN e TELEGRAM_CHAT_ID nao estao definidos", List.of(),
                    null);
        }

        Set<String> confirmadas = new LinkedHashSet<>();
        for (Agrupador.Mensagem mensagem : agrupar(pecas)) {
            HttpResponse<String> resposta;
            try {
                resposta = postar(props.chatId(), mensagem.texto());
            } catch (FalhaDeOrigem e) {
                throw falha("erro de rede ao falar com o Telegram: " + e.getMessage(),
                        List.copyOf(confirmadas), e);
            }
            if (resposta.statusCode() >= 300) {
                throw falha("Telegram respondeu " + resposta.statusCode() + ": "
                        + resposta.body(), List.copyOf(confirmadas), null);
            }
            confirmadas.addAll(mensagem.refeicoes());
        }
        return List.copyOf(confirmadas);
    }

    /**
     * Manda um alerta ao chat de quem cuida do bot, nao ao grupo. Devolve se
     * chegou; nunca lanca, porque o alerta acompanha uma falha e nao pode
     * esconder a original.
     */
    public boolean alertar(String texto) {
        if (!props.alertaConfigurado()) {
            return false;
        }
        try {
            int status = postar(props.chatAlerta(), texto).statusCode();
            if (status >= 300) {
                log.warn("o alerta nao chegou: Telegram respondeu {}", status);
            }
            return status < 300;
        } catch (RuntimeException e) {
            log.warn("o alerta nao chegou: {}", e.getMessage());
            return false;
        }
    }

    private static FalhaDeEnvio falha(String mensagem, List<String> confirmadas,
            Throwable origem) {
        return new FalhaDeEnvio(FalhaDeOrigem.Causa.TELEGRAM_RECUSOU, mensagem, confirmadas,
                origem);
    }

    private HttpResponse<String> postar(String chatId, String texto) {
        String url = props.urlBase() + "/bot" + props.token() + "/sendMessage";
        String corpo;
        try {
            corpo = json.writeValueAsString(Map.of(
                    "chat_id", chatId,
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
