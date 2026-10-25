package io.github.tetzdesen.cardapioru.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.tetzdesen.cardapioru.coleta.Retentador;
import io.github.tetzdesen.cardapioru.coleta.Tempo;
import io.github.tetzdesen.cardapioru.config.WhatsappProperties;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClienteWhatsappTest {

    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final List<String> corpos = new ArrayList<>();
    private final List<String> caminhos = new ArrayList<>();
    private final List<String> chaves = new ArrayList<>();

    /** Sobe um WAHA falso que responde o status que o plano mandar para a n-esima chamada. */
    private String subir(IntUnaryOperator plano) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            int n = chamadas.incrementAndGet();
            synchronized (corpos) {
                corpos.add(new String(troca.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                caminhos.add(troca.getRequestURI().getPath());
                chaves.add(troca.getRequestHeaders().getFirst("X-Api-Key"));
            }
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(plano.applyAsInt(n), bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void derrubar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private ClienteWhatsapp cliente(String urlBase, int limiteMensagem) {
        WhatsappProperties props = new WhatsappProperties(urlBase, "chave-de-teste", "default",
                "120363000000000000@g.us", Duration.ofSeconds(2), limiteMensagem);
        Retentador retentador = new Retentador(3, Duration.ofSeconds(2), Duration.ofSeconds(60),
                Duration.ofSeconds(75), Duration.ofSeconds(2), new Tempo() {
                    private long agora;

                    @Override
                    public long nanos() {
                        return agora;
                    }

                    @Override
                    public void esperar(Duration quanto) {
                        agora += quanto.toNanos();
                    }
                });
        return new ClienteWhatsapp(props, HttpClient.newHttpClient(), retentador);
    }

    @Test
    void mandaAoGrupoPeloWahaComAChaveEAMarcacaoDoWhatsapp() throws Exception {
        String url = subir(n -> 201);

        Peca almoco = new Peca("almoco", "🍽️ <b>Almoço</b>\n• Arroz &amp; feijão");

        List<String> confirmadas = cliente(url + "/", 4000).enviar(List.of(almoco));

        assertThat(confirmadas).containsExactly("almoco");
        assertThat(caminhos).containsExactly("/api/sendText");
        assertThat(chaves).containsExactly("chave-de-teste");
        JsonNode corpo = new ObjectMapper().readTree(corpos.get(0));
        assertThat(corpo.path("session").asText()).isEqualTo("default");
        assertThat(corpo.path("chatId").asText()).isEqualTo("120363000000000000@g.us");
        assertThat(corpo.path("text").asText()).isEqualTo("🍽️ *Almoço*\n• Arroz & feijão");
        assertThat(corpo.path("linkPreview").asBoolean(true)).isFalse();
    }

    @Test
    void cincoXxERepetido() throws Exception {
        String url = subir(n -> n < 3 ? 503 : 201);

        cliente(url, 4000).enviar(List.of(new Peca("almoco", "t")));

        assertThat(chamadas).hasValue(3);
    }

    @Test
    void chaveRecusadaNaoERepetidaEDizQueFoiOWhatsapp() throws Exception {
        String url = subir(n -> 401);

        assertThatThrownBy(() -> cliente(url, 4000).enviar(List.of(new Peca("almoco", "t"))))
                .isInstanceOfSatisfying(FalhaDeEnvio.class, e ->
                        assertThat(e.causa()).isEqualTo(FalhaDeOrigem.Causa.WHATSAPP_RECUSOU))
                .hasMessageContaining("401");

        assertThat(chamadas).hasValue(1);
    }

    @Test
    void envioPartidoEmDuasMensagensSoConfirmaOQueChegou() throws Exception {
        String url = subir(n -> n == 1 ? 201 : 400);

        // limite baixo forca uma mensagem por peca
        assertThatThrownBy(() -> cliente(url, 10).enviar(List.of(
                new Peca("almoco", "primeira parte"),
                new Peca("jantar", "segunda parte"))))
                .isInstanceOfSatisfying(FalhaDeEnvio.class, e ->
                        assertThat(e.confirmadas()).containsExactly("almoco"));
    }

    @Test
    void semConfiguracaoFicaDeForaDoDisparo() {
        WhatsappProperties vazio = new WhatsappProperties("", "", "default", "",
                Duration.ofSeconds(2), 4000);
        ClienteWhatsapp c = new ClienteWhatsapp(vazio, HttpClient.newHttpClient(),
                new Retentador(3, Duration.ofSeconds(1), Duration.ofSeconds(1),
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Tempo.real()));

        assertThat(c.configurado()).isFalse();
        assertThatThrownBy(() -> c.enviar(List.of(new Peca("almoco", "t"))))
                .isInstanceOf(FalhaDeEnvio.class)
                .hasMessageContaining("WHATSAPP_URL");
    }
}
