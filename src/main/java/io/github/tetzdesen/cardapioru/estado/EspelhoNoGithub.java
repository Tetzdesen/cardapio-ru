package io.github.tetzdesen.cardapioru.estado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.config.EstadoGithubProperties;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * O arquivo de estado guardado num ramo do repositorio, pela API de conteudo do
 * GitHub. O disco do conteiner do Render some a cada deploy e reinicio; o ramo
 * nao.
 *
 * <p>So ha um escritor -- a API --, entao o {@code sha} da ultima leitura ou
 * gravacao e quase sempre o atual. Quando nao e (alguem mexeu no ramo a mao),
 * o GitHub recusa, o sha e relido e a gravacao vai de novo: o que esta na
 * memoria da API e o estado mais novo.
 */
@Component
public class EspelhoNoGithub {

    private final EstadoGithubProperties props;
    private final HttpClient cliente;
    private final ObjectMapper json = new ObjectMapper();

    private String sha;

    public EspelhoNoGithub(EstadoGithubProperties props) {
        this.props = props;
        this.cliente = HttpClient.newBuilder().connectTimeout(props.timeout()).build();
    }

    public boolean ativo() {
        return props.ativo();
    }

    public String descricao() {
        return props.repositorio() + "@" + props.ramo() + ":" + props.caminho();
    }

    /**
     * O texto do arquivo no ramo; vazio se ele ainda nao existe.
     *
     * @throws EstadoIndisponivel se o GitHub nao responder ou recusar
     */
    public synchronized Optional<String> baixar() {
        HttpResponse<String> resposta = chamar(pedido(url() + "?ref=" + codificar(props.ramo()))
                .GET());
        if (resposta.statusCode() == 404) {
            sha = null;
            return Optional.empty();
        }
        exigirSucesso(resposta, "ler");
        JsonNode corpo = ler(resposta.body());
        sha = corpo.path("sha").asText(null);
        byte[] bytes = Base64.getMimeDecoder().decode(corpo.path("content").asText(""));
        return Optional.of(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Grava o texto no ramo, num commit.
     *
     * @throws EstadoIndisponivel se o GitHub nao responder ou recusar
     */
    public synchronized void enviar(String texto) {
        HttpResponse<String> resposta = gravar(texto);
        if (resposta.statusCode() == 409 || resposta.statusCode() == 422) {
            // sha desatualizado: rele e tenta uma vez mais
            baixar();
            resposta = gravar(texto);
        }
        exigirSucesso(resposta, "gravar");
        sha = ler(resposta.body()).path("content").path("sha").asText(null);
    }

    private HttpResponse<String> gravar(String texto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("message", "estado: cardápio anunciado");
        corpo.put("content", Base64.getEncoder()
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8)));
        corpo.put("branch", props.ramo());
        if (sha != null) {
            corpo.put("sha", sha);
        }
        try {
            return chamar(pedido(url())
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo))));
        } catch (IOException e) {
            throw new EstadoIndisponivel("nao foi possivel montar o pedido ao GitHub", e);
        }
    }

    private String url() {
        return props.urlBase().replaceAll("/+$", "") + "/repos/" + props.repositorio()
                + "/contents/" + props.caminho();
    }

    private HttpRequest.Builder pedido(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(props.timeout())
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + props.token())
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private HttpResponse<String> chamar(HttpRequest.Builder pedido) {
        try {
            return cliente.send(pedido.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new EstadoIndisponivel("GitHub inacessivel (" + descricao() + "): "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EstadoIndisponivel("interrompido falando com o GitHub", e);
        }
    }

    private void exigirSucesso(HttpResponse<String> resposta, String acao) {
        if (resposta.statusCode() / 100 != 2) {
            // o corpo do GitHub explica (token sem permissao, ramo inexistente) e
            // nao traz o token de volta
            throw new EstadoIndisponivel("GitHub recusou " + acao + " o estado em "
                    + descricao() + ": " + resposta.statusCode() + " " + resposta.body(), null);
        }
    }

    private JsonNode ler(String corpo) {
        try {
            return json.readTree(corpo);
        } catch (IOException e) {
            throw new EstadoIndisponivel("resposta ilegivel do GitHub: " + e.getMessage(), e);
        }
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}
