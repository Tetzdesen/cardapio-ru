package io.github.tetzdesen.cardapioru.coleta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ColetorDaUfesTest {

    private final TempoFalso tempo = new TempoFalso();

    private CardapioProperties props(String urlBase) {
        return new CardapioProperties(urlBase, "ru-alegre-bot/3.0 (teste)",
                Duration.ofSeconds(2), 3, Duration.ofSeconds(2), Duration.ofSeconds(60),
                Duration.ofSeconds(75), 30, ZoneOffset.ofHours(-3), Duration.ofSeconds(60), 400);
    }

    private ColetorDaUfes coletor(CardapioProperties p) {
        Retentador retentador = new Retentador(p.tentativas(), p.esperaBase(), p.esperaMaxima(),
                p.orcamentoTotal(), p.timeout(), tempo);
        return new ColetorDaUfes(p, HttpClient.newHttpClient(), retentador);
    }

    @Test
    void mandaOUserAgentIdentificavel() throws Exception {
        try (ServidorDeTeste servidor = new ServidorDeTeste(n -> 200, "<html>ok</html>")) {
            CardapioProperties p = props(servidor.url());
            String html = coletor(p).baixar(null);

            assertThat(html).contains("ok");
            assertThat(servidor.userAgents()).allSatisfy(ua ->
                    assertThat(ua).isEqualTo("ru-alegre-bot/3.0 (teste)"));
        }
    }

    @Test
    void servidorResponde502EDepois200() throws Exception {
        try (ServidorDeTeste servidor = new ServidorDeTeste(n -> n == 1 ? 502 : 200, "ok")) {
            String html = coletor(props(servidor.url())).baixar(null);

            assertThat(html).isEqualTo("ok");
            assertThat(servidor.chamadas()).isEqualTo(2);
            assertThat(tempo.esperas()).containsExactly(Duration.ofSeconds(2));
        }
    }

    @Test
    void servidorForaDoArEsgotaAsTentativasEFalha() throws Exception {
        int portaFechada;
        try (ServerSocket s = new ServerSocket(0)) {
            portaFechada = s.getLocalPort();
        }
        CardapioProperties p = props("http://127.0.0.1:" + portaFechada + "/cardapio");

        assertThatThrownBy(() -> coletor(p).baixar(null))
                .isInstanceOf(FalhaDeOrigem.class)
                .satisfies(e -> assertThat(((FalhaDeOrigem) e).causa())
                        .isEqualTo(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL))
                .hasMessageContaining("3 tentativa");

        assertThat(tempo.esperas()).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void dataInexistenteNaoERepetida() throws Exception {
        try (ServidorDeTeste servidor = new ServidorDeTeste(n -> 404, "nao existe")) {
            CardapioProperties p = props(servidor.url());

            assertThatThrownBy(() -> coletor(p).baixar(null))
                    .isInstanceOf(FalhaDeOrigem.class)
                    .hasMessageContaining("404");

            assertThat(servidor.chamadas()).isEqualTo(1);
            assertThat(tempo.esperas()).isEmpty();
        }
    }

    @Test
    void cincoXxRepeteAteOLimiteDeTentativas() throws Exception {
        try (ServidorDeTeste servidor = new ServidorDeTeste(n -> 503, "fora do ar")) {
            CardapioProperties p = props(servidor.url());

            assertThatThrownBy(() -> coletor(p).baixar(null)).isInstanceOf(FalhaDeOrigem.class);

            assertThat(servidor.chamadas()).isEqualTo(3);
        }
    }
}
