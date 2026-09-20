package io.github.tetzdesen.cardapioru.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import org.junit.jupiter.api.Test;

/** As duas grafias do argparse, e os padroes do ru_bot.py. */
class ArgumentosTest {

    @Test
    void aceitaAsDuasGrafias() {
        Argumentos colada = Argumentos.ler(new String[] {"--data=2026-09-16", "--campus=alegre"});
        Argumentos separada = Argumentos.ler(new String[] {"--data", "2026-09-16",
            "--campus", "alegre"});

        assertThat(colada).isEqualTo(separada);
        assertThat(colada.data()).isEqualTo("2026-09-16");
        assertThat(colada.campus()).isEqualTo("alegre");
    }

    @Test
    void semArgumentosUsaOsPadroesDoRuBot() {
        Argumentos padrao = Argumentos.ler(new String[0]);

        assertThat(padrao.refeicoes()).isEqualTo("almoco,jantar");
        assertThat(padrao.campus()).isEqualTo("alegre");
        assertThat(padrao.data()).isNull();
        assertThat(padrao.enviar()).isFalse();
        assertThat(padrao.imprimir()).isFalse();
        assertThat(padrao.vazioSilencioso()).isFalse();
    }

    @Test
    void interruptoresLigamSozinhos() {
        Argumentos a = Argumentos.ler(new String[] {"--enviar", "--print",
            "--vazio-silencioso"});

        assertThat(a.enviar()).isTrue();
        assertThat(a.imprimir()).isTrue();
        assertThat(a.vazioSilencioso()).isTrue();
    }

    @Test
    void opcaoComValorFaltandoERecusada() {
        assertThatThrownBy(() -> Argumentos.ler(new String[] {"--data"}))
                .isInstanceOf(ParametroInvalido.class);
        assertThatThrownBy(() -> Argumentos.ler(new String[] {"--data", "--enviar"}))
                .isInstanceOf(ParametroInvalido.class);
    }

    @Test
    void estadoViraAPropriedadeQueAAplicacaoLiga() {
        // sem a traducao, '--estado=x' viraria uma propriedade escalar chamada
        // 'estado', colidindo com o objeto de mesmo prefixo
        assertThat(Argumentos.paraSpring(new String[] {"--estado", "/tmp/e.json", "--enviar"}))
                .containsExactly("--estado.caminho=/tmp/e.json", "--enviar");
        assertThat(Argumentos.paraSpring(new String[] {"--estado=/tmp/e.json"}))
                .containsExactly("--estado.caminho=/tmp/e.json");
    }

    @Test
    void oQueNaoENossoPassaIntacto() {
        String[] alheios = {"--spring.profiles.active=web", "--server.port=9000"};

        assertThat(Argumentos.paraSpring(alheios)).containsExactly(alheios);
        assertThat(Argumentos.quantasNossas(alheios)).isZero();
    }

    @Test
    void contaSoAsNossasOpcoes() {
        assertThat(Argumentos.quantasNossas(new String[] {"--enviar", "--debug"})).isEqualTo(1);
        assertThat(Argumentos.quantasNossas(
                new String[] {"--estado.caminho=/tmp/e.json"})).isEqualTo(1);
    }
}
