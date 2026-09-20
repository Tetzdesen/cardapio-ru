package io.github.tetzdesen.cardapioru.coleta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * O orcamento existe porque do outro lado ha um curl de workflow com paciencia
 * finita: a aplicacao precisa desistir antes dele, para que a falha chegue como
 * resposta e nao como timeout sem explicacao.
 */
class OrcamentoDeTempoTest {

    private final TempoFalso tempo = new TempoFalso();

    private Retentador retentador(int tentativas, Duration base, Duration orcamento,
            Duration timeout) {
        return new Retentador(tentativas, base, Duration.ofSeconds(60), orcamento, timeout, tempo);
    }

    @Test
    void piorCasoCabeNoOrcamentoConfigurado() {
        Duration orcamento = Duration.ofSeconds(75);
        Retentador r = retentador(3, Duration.ofSeconds(2), orcamento, Duration.ofSeconds(30));

        // 3 timeouts de 30s + esperas de 2s e 4s = 96s, acima do orcamento:
        // o teto declarado e o orcamento, nao a soma ingenua.
        assertThat(r.piorCaso()).isLessThanOrEqualTo(orcamento);
    }

    @Test
    void paraDeTentarQuandoOOrcamentoNaoComportaOutraTentativa() {
        Duration timeout = Duration.ofSeconds(5);
        Duration orcamento = Duration.ofSeconds(12);
        Retentador r = retentador(5, Duration.ofSeconds(2), orcamento, timeout);

        // Cada tentativa consome o timeout inteiro, que e o pior caso real.
        assertThatThrownBy(() -> r.executar("teste",
                () -> {
                    tempo.avancar(timeout);
                    throw new IOException("sempre falha");
                },
                x -> Retentador.Veredito.aceitar()))
                .isInstanceOf(FalhaDeOrigem.class)
                .as("relata as tentativas feitas, nao as permitidas")
                .hasMessageContaining("falhou em 2 tentativa(s)");

        assertThat(tempo.decorrido())
                .as("tempo total gasto, esperas incluidas")
                .isLessThanOrEqualTo(orcamento);
        // 5s + espera de 2s + 5s = 12s, exatamente o orcamento; uma terceira
        // tentativa exigiria 12 + 4 + 5 = 21s.
        assertThat(tempo.esperas())
                .as("desistiu antes de esgotar as 5 tentativas")
                .containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void comOrcamentoFolgadoUsaTodasAsTentativas() {
        Duration timeout = Duration.ofSeconds(5);
        Retentador r = retentador(3, Duration.ofSeconds(2), Duration.ofSeconds(120), timeout);

        assertThatThrownBy(() -> r.executar("teste",
                () -> {
                    tempo.avancar(timeout);
                    throw new IOException("sempre falha");
                },
                x -> Retentador.Veredito.aceitar()))
                .isInstanceOf(FalhaDeOrigem.class)
                .hasMessageContaining("3 tentativa");

        assertThat(tempo.esperas()).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void configuracaoDeProducaoCabeNoOrcamento() {
        // Os mesmos valores de application.yml: 3 tentativas, base 2s, timeout 30s.
        Duration orcamento = Duration.ofSeconds(75);
        Duration timeout = Duration.ofSeconds(30);
        Retentador r = retentador(3, Duration.ofSeconds(2), orcamento, timeout);

        assertThatThrownBy(() -> r.executar("producao",
                () -> {
                    tempo.avancar(timeout);
                    throw new IOException("origem muda");
                },
                x -> Retentador.Veredito.aceitar()))
                .isInstanceOf(FalhaDeOrigem.class);

        assertThat(tempo.decorrido()).isLessThanOrEqualTo(orcamento);
    }
}
