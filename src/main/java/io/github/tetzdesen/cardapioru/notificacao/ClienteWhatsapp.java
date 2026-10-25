package io.github.tetzdesen.cardapioru.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.coleta.Retentador;
import io.github.tetzdesen.cardapioru.coleta.Tempo;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.WhatsappProperties;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Manda o aviso num grupo de WhatsApp pelo WAHA, que mantem um numero conectado
 * como "aparelho conectado". O WhatsApp nao entende HTML: as pecas sao
 * convertidas para a marcacao dele so aqui, na saida, e a assinatura do estado
 * continua saindo do mesmo texto que vai para o Telegram.
 */
@Component
@Order(2)
public class ClienteWhatsapp implements Canal {

    /** O WAHA reinicia a sessao e o WhatsApp limita envios; 4xx fora do 429 e configuracao. */
    static final Set<Integer> STATUS_REPETIVEL = Set.of(429, 500, 502, 503, 504);

    private final WhatsappProperties props;
    private final HttpClient cliente;
    private final Retentador retentador;
    private final ObjectMapper json = new ObjectMapper();

    // a outra construtora existe para o teste injetar cliente e retentador;
    // com duas, o Spring precisa que a de injecao seja apontada
    @Autowired
    public ClienteWhatsapp(WhatsappProperties props, CardapioProperties cardapio) {
        this(props, HttpClient.newBuilder().connectTimeout(props.timeout()).build(),
                new Retentador(cardapio.tentativas(), cardapio.esperaBase(),
                        cardapio.esperaMaxima(), cardapio.orcamentoTotal(), props.timeout(),
                        Tempo.real()));
    }

    ClienteWhatsapp(WhatsappProperties props, HttpClient cliente, Retentador retentador) {
        this.props = props;
        this.cliente = cliente;
        this.retentador = retentador;
    }

    @Override
    public String nome() {
        return "whatsapp";
    }

    @Override
    public boolean configurado() {
        return props.configurado();
    }

    @Override
    public List<String> enviar(List<Peca> pecas) {
        if (!props.configurado()) {
            throw falha("WHATSAPP_URL, WHATSAPP_API_KEY e WHATSAPP_CHAT_ID nao estao definidos",
                    List.of(), null);
        }

        Set<String> confirmadas = new LinkedHashSet<>();
        for (Agrupador.Mensagem mensagem
                : Agrupador.agrupar(pecas, props.limiteMensagem())) {
            HttpResponse<String> resposta;
            try {
                resposta = postar(Formatador.paraWhatsapp(mensagem.texto()));
            } catch (FalhaDeOrigem e) {
                throw falha("erro de rede ao falar com o WAHA: " + e.getMessage(),
                        List.copyOf(confirmadas), e);
            }
            if (resposta.statusCode() >= 300) {
                throw falha("WAHA respondeu " + resposta.statusCode() + ": " + resposta.body(),
                        List.copyOf(confirmadas), null);
            }
            confirmadas.addAll(mensagem.refeicoes());
        }
        return List.copyOf(confirmadas);
    }

    private static FalhaDeEnvio falha(String mensagem, List<String> confirmadas,
            Throwable origem) {
        return new FalhaDeEnvio(FalhaDeOrigem.Causa.WHATSAPP_RECUSOU, mensagem, confirmadas,
                origem);
    }

    private HttpResponse<String> postar(String texto) {
        String url = props.urlBase().replaceAll("/+$", "") + "/api/sendText";
        String corpo;
        try {
            corpo = json.writeValueAsString(Map.of(
                    "session", props.sessao(),
                    "chatId", props.chatId(),
                    "text", texto.strip(),
                    "linkPreview", false));
        } catch (Exception e) {
            throw new IllegalStateException("nao foi possivel montar o corpo do envio", e);
        }

        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", props.apiKey())
                .timeout(props.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();

        return retentador.executar("WAHA",
                () -> cliente.send(pedido, HttpResponse.BodyHandlers.ofString()),
                r -> STATUS_REPETIVEL.contains(r.statusCode())
                        ? Retentador.Veredito.repetirAgora()
                        : Retentador.Veredito.aceitar());
    }
}
