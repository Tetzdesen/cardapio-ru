package io.github.tetzdesen.cardapioru.coleta;

import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.ConfiancaTls;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Baixa a pagina do cardapio com verificacao TLS ativa e retry de transitorio.
 * Porte do {@code baixar} do ru_bot.py.
 */
@Component
public class ColetorDaUfes {

    /**
     * O servidor da UFES cai e volta. 4xx fora do 429 e erro de configuracao ou
     * de rota -- repetir so gasta o orcamento.
     */
    static final Set<Integer> STATUS_REPETIVEL = Set.of(429, 500, 502, 503, 504);

    private static final Logger log = LoggerFactory.getLogger(ColetorDaUfes.class);

    private final CardapioProperties props;
    private final HttpClient cliente;
    private final Retentador retentador;

    @Autowired
    public ColetorDaUfes(CardapioProperties props) {
        this(props, clientePadrao(props), retentadorPadrao(props));
    }

    ColetorDaUfes(CardapioProperties props, HttpClient cliente, Retentador retentador) {
        this.props = props;
        this.cliente = cliente;
        this.retentador = retentador;
    }

    private static HttpClient clientePadrao(CardapioProperties props) {
        try {
            return HttpClient.newBuilder()
                    .sslContext(ConfiancaTls.contexto())
                    .connectTimeout(props.timeout())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "nao foi possivel montar a confianca TLS\n" + ConfiancaTls.COMO_OBTER, e);
        }
    }

    private static Retentador retentadorPadrao(CardapioProperties props) {
        return new Retentador(props.tentativas(), props.esperaBase(), props.esperaMaxima(),
                props.orcamentoTotal(), props.timeout(), Tempo.real());
    }

    public String urlDe(LocalDate dia) {
        return dia == null ? props.urlBase() : props.urlBase() + "/" + dia;
    }

    /** O HTML cru da pagina. Lanca {@link FalhaDeOrigem} quando nao da para obter. */
    public String baixar(LocalDate dia) {
        String url = urlDe(dia);
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", props.userAgent())
                .timeout(props.timeout())
                .GET()
                .build();

        HttpResponse<String> resposta = retentador.executar(
                "GET " + url,
                () -> cliente.send(pedido, HttpResponse.BodyHandlers.ofString()),
                r -> STATUS_REPETIVEL.contains(r.statusCode())
                        ? Retentador.Veredito.repetirAgora()
                        : Retentador.Veredito.aceitar());

        if (resposta.statusCode() >= 400) {
            log.error("{} respondeu {}", url, resposta.statusCode());
            throw new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL,
                    url + " respondeu " + resposta.statusCode());
        }
        return resposta.body();
    }

    /** Quanto tempo, no pior caso, uma coleta pode demorar. */
    public java.time.Duration piorCaso() {
        return retentador.piorCaso();
    }
}
