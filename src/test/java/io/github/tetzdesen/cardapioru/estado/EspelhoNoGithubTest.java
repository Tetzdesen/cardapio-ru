package io.github.tetzdesen.cardapioru.estado;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.EstadoGithubProperties;
import io.github.tetzdesen.cardapioru.config.EstadoProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** O estado sobrevivendo ao reinicio pelo ramo do GitHub, contra um GitHub falso. */
class EspelhoNoGithubTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 30);
    private static final ZoneOffset FUSO = ZoneOffset.ofHours(-3);
    private static final CardapioProperties PROPS = new CardapioProperties("http://x", "t",
            Duration.ofSeconds(1), 1, Duration.ZERO, Duration.ZERO, Duration.ofSeconds(1), 30,
            FUSO, Duration.ZERO, 400);

    /** O GitHub falso: o arquivo do ramo, o sha dele e o que chegou nos PUT. */
    private String remoto;
    private int versao;
    private boolean foraDoAr;
    private final List<JsonNode> gravacoes = new ArrayList<>();

    private HttpServer servidor;
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path pasta;

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String resposta;
            int status;
            if (foraDoAr) {
                status = 503;
                resposta = "{}";
            } else if (troca.getRequestMethod().equals("GET")) {
                status = remoto == null ? 404 : 200;
                resposta = remoto == null ? "{}" : json.writeValueAsString(Map.of(
                        "sha", "sha" + versao,
                        "content", Base64.getMimeEncoder().encodeToString(
                                remoto.getBytes(StandardCharsets.UTF_8))));
            } else {
                JsonNode corpo = json.readTree(troca.getRequestBody());
                gravacoes.add(corpo);
                String esperado = remoto == null ? null : "sha" + versao;
                if (!java.util.Objects.equals(esperado, corpo.path("sha").asText(null))) {
                    status = 409;
                    resposta = "{}";
                } else {
                    remoto = new String(Base64.getDecoder().decode(
                            corpo.path("content").asText()), StandardCharsets.UTF_8);
                    versao++;
                    status = 200;
                    resposta = "{\"content\":{\"sha\":\"sha" + versao + "\"}}";
                }
            }
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    /** Um processo novo: disco vazio de outro conteiner, mesmo GitHub. */
    private ArquivoDeEstado processo(String nomeDoDisco) {
        EstadoGithubProperties github = new EstadoGithubProperties(
                "http://127.0.0.1:" + servidor.getAddress().getPort(), "dono/repo", "estado",
                "estado/ultimo-envio.json", "token-de-teste", Duration.ofSeconds(5));
        Clock relogio = Clock.fixed(DIA.atStartOfDay(FUSO).toInstant(), FUSO);
        return new ArquivoDeEstado(new EstadoProperties(pasta.resolve(nomeDoDisco)), PROPS,
                relogio, new EspelhoNoGithub(github));
    }

    @Test
    void oEstadoSobreviveAoReinicio() {
        ArquivoDeEstado antes = processo("antes.json");
        antes.gravar(Map.of(ArquivoDeEstado.chave(DIA, "almoco"), "abc"));

        ArquivoDeEstado depois = processo("depois.json");

        assertThat(depois.ler()).containsEntry(ArquivoDeEstado.chave(DIA, "almoco"), "abc");
    }

    @Test
    void gravaNoRamoComOShaDaUltimaVersao() {
        remoto = "{\n  \"2026-10-30|desjejum\": \"x\"\n}\n";
        ArquivoDeEstado arquivo = processo("disco.json");

        Map<String, String> estado = arquivo.ler();
        estado.put(ArquivoDeEstado.chave(DIA, "almoco"), "abc");
        arquivo.gravar(estado);

        assertThat(gravacoes).hasSize(1);
        assertThat(gravacoes.get(0).path("branch").asText()).isEqualTo("estado");
        assertThat(gravacoes.get(0).path("sha").asText()).isEqualTo("sha0");
        assertThat(remoto).contains("2026-10-30|desjejum", "2026-10-30|almoco");
    }

    @Test
    void shaDesatualizadoReleEGravaDeNovo() {
        remoto = "{}\n";
        ArquivoDeEstado arquivo = processo("disco.json");
        arquivo.ler();
        versao = 7; // alguem mexeu no ramo a mao

        arquivo.gravar(Map.of(ArquivoDeEstado.chave(DIA, "almoco"), "abc"));

        assertThat(gravacoes).hasSize(2);
        assertThat(remoto).contains("2026-10-30|almoco");
    }

    @Test
    void githubForaDoArValeODiscoSemSobrescreverORamo() throws Exception {
        remoto = "{\n  \"2026-10-30|desjejum\": \"x\"\n}\n";
        foraDoAr = true;
        ArquivoDeEstado arquivo = processo("disco.json");

        arquivo.gravar(Map.of(ArquivoDeEstado.chave(DIA, "almoco"), "abc"));

        assertThat(gravacoes).isEmpty();
        assertThat(Files.readString(pasta.resolve("disco.json"))).contains("almoco");

        // o GitHub volta: a gravacao seguinte junta os dois
        foraDoAr = false;
        Map<String, String> estado = arquivo.ler();
        estado.put(ArquivoDeEstado.chave(DIA, "jantar"), "def");
        arquivo.gravar(estado);

        assertThat(remoto).contains("desjejum", "almoco", "jantar");
    }
}
