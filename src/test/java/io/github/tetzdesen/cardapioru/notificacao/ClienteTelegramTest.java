package io.github.tetzdesen.cardapioru.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.tetzdesen.cardapioru.coleta.Retentador;
import io.github.tetzdesen.cardapioru.coleta.Tempo;
import io.github.tetzdesen.cardapioru.config.TelegramProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClienteTelegramTest {

    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final List<String> corpos = new ArrayList<>();
    private final List<Duration> esperas = new ArrayList<>();

    private record Resposta(int status, String corpo, String retryAfter) {
    }

    private String subir(IntFunction<Resposta> plano) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            int n = chamadas.incrementAndGet();
            synchronized (corpos) {
                corpos.add(new String(troca.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            Resposta r = plano.apply(n);
            if (r.retryAfter() != null) {
                troca.getResponseHeaders().add("Retry-After", r.retryAfter());
            }
            byte[] bytes = r.corpo().getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(r.status(), bytes.length);
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

    private ClienteTelegram cliente(String urlBase, int limiteMensagem) {
        TelegramProperties props = new TelegramProperties("token-de-teste", "-100123",
                urlBase, Duration.ofSeconds(2), limiteMensagem);
        Retentador retentador = new Retentador(3, Duration.ofSeconds(2), Duration.ofSeconds(60),
                Duration.ofSeconds(75), Duration.ofSeconds(2), new Tempo() {
                    private long agora;

                    @Override
                    public long nanos() {
                        return agora;
                    }

                    @Override
                    public void esperar(Duration quanto) {
                        esperas.add(quanto);
                        agora += quanto.toNanos();
                    }
                });
        return new ClienteTelegram(props, HttpClient.newHttpClient(), retentador);
    }

    @Test
    void limiteDeRequisicoesRespeitaAEsperaPedida() throws Exception {
        String url = subir(n -> n == 1
                ? new Resposta(429, "{\"parameters\":{\"retry_after\":7}}", null)
                : new Resposta(200, "{\"ok\":true}", null));

        List<String> confirmadas =
                cliente(url, 4000).enviar(List.of(new Peca("almoco", "texto do almoço")));

        assertThat(confirmadas).containsExactly("almoco");
        assertThat(esperas).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void retryAfterEmCabecalhoTambemVale() throws Exception {
        String url = subir(n -> n == 1
                ? new Resposta(429, "nao e json", "5")
                : new Resposta(200, "{\"ok\":true}", null));

        cliente(url, 4000).enviar(List.of(new Peca("almoco", "texto")));

        assertThat(esperas).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void tokenInvalidoNaoERepetido() throws Exception {
        String url = subir(n -> new Resposta(401, "{\"description\":\"Unauthorized\"}", null));

        assertThatThrownBy(() -> cliente(url, 4000).enviar(List.of(new Peca("almoco", "t"))))
                .isInstanceOf(ClienteTelegram.FalhaDeEnvio.class)
                .hasMessageContaining("401");

        assertThat(chamadas).hasValue(1);
        assertThat(esperas).isEmpty();
    }

    @Test
    void cincoXxERepetido() throws Exception {
        String url = subir(n -> n < 3
                ? new Resposta(502, "erro", null)
                : new Resposta(200, "{\"ok\":true}", null));

        cliente(url, 4000).enviar(List.of(new Peca("almoco", "t")));

        assertThat(chamadas).hasValue(3);
    }

    @Test
    void envioPartidoEmDuasMensagensSoConfirmaOQueChegou() throws Exception {
        String url = subir(n -> n == 1
                ? new Resposta(200, "{\"ok\":true}", null)
                : new Resposta(400, "{\"description\":\"Bad Request\"}", null));

        // limite baixo forca uma mensagem por peca
        assertThatThrownBy(() -> cliente(url, 10).enviar(List.of(
                new Peca("almoco", "primeira parte"),
                new Peca("jantar", "segunda parte"))))
                .isInstanceOfSatisfying(ClienteTelegram.FalhaDeEnvio.class, e ->
                        assertThat(e.confirmadas()).containsExactly("almoco"));
    }

    @Test
    void agrupaSemPartirUmaPecaAoMeio() {
        ClienteTelegram c = cliente("http://exemplo", 30);

        List<ClienteTelegram.Mensagem> mensagens = c.agrupar(List.of(
                new Peca("almoco", "a".repeat(20)),
                new Peca("jantar", "b".repeat(20))));

        assertThat(mensagens).hasSize(2);
        assertThat(mensagens.get(0).refeicoes()).containsExactly("almoco");
        assertThat(mensagens.get(1).refeicoes()).containsExactly("jantar");
    }

    @Test
    void pecasPequenasViajamJuntas() {
        ClienteTelegram c = cliente("http://exemplo", 4000);

        List<ClienteTelegram.Mensagem> mensagens = c.agrupar(List.of(
                new Peca("almoco", "almoço"), new Peca("jantar", "jantar"),
                Peca.avulsa("<a href=\"x\">ver no site</a>")));

        assertThat(mensagens).hasSize(1);
        assertThat(mensagens.get(0).refeicoes()).containsExactly("almoco", "jantar");
    }

    @Test
    void semConfiguracaoDoTelegramFalhaSemChamarNinguem() {
        TelegramProperties vazio = new TelegramProperties("", "", "http://exemplo",
                Duration.ofSeconds(2), 4000);
        ClienteTelegram c = new ClienteTelegram(vazio, HttpClient.newHttpClient(),
                new Retentador(3, Duration.ofSeconds(1), Duration.ofSeconds(1),
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Tempo.real()));

        assertThatThrownBy(() -> c.enviar(List.of(new Peca("almoco", "t"))))
                .isInstanceOf(ClienteTelegram.FalhaDeEnvio.class)
                .hasMessageContaining("TELEGRAM_TOKEN");
    }
}
