package io.github.tetzdesen.cardapioru.coleta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiltroTest {

    private static final List<BlocoRefeicao> BLOCOS = List.of(
            bloco("Desjejum", "Alegre e Jerônimo Monteiro"),
            bloco("Almoço", "Alegre e Jerônimo Monteiro"),
            bloco("Almoço", "Jerônimo Monteiro"),
            bloco("Jantar", "Alegre"));

    private static BlocoRefeicao bloco(String refeicao, String campus) {
        return new BlocoRefeicao(refeicao, campus, "sexta-feira",
                List.of(new Secao("Entrada", List.of("Alface"))));
    }

    @Test
    void soAlmocoDeAlegre() {
        List<BlocoRefeicao> filtrados =
                Filtro.aplicar(BLOCOS, Filtro.refeicoesPedidas("almoço"), "alegre");

        assertThat(filtrados).hasSize(1);
        assertThat(filtrados.get(0).refeicao()).isEqualTo("Almoço");
        assertThat(filtrados.get(0).campus()).isEqualTo("Alegre e Jerônimo Monteiro");
    }

    @Test
    void semAcentoEComCaixaDiferenteFuncionaIgual() {
        assertThat(Filtro.aplicar(BLOCOS, Filtro.refeicoesPedidas("ALMOCO"), "alegre"))
                .hasSize(1);
    }

    @Test
    void todasNaoFiltraNada() {
        assertThat(Filtro.aplicar(BLOCOS, Filtro.refeicoesPedidas("todas"), null))
                .hasSize(BLOCOS.size());
    }

    @Test
    void cafeDaManhaEDesjejumSaoAMesmaRefeicao() {
        List<BlocoRefeicao> filtrados =
                Filtro.aplicar(BLOCOS, Filtro.refeicoesPedidas("café da manhã"), null);

        assertThat(filtrados).hasSize(1);
        assertThat(filtrados.get(0).refeicao()).isEqualTo("Desjejum");
    }

    @Test
    void refeicaoDesconhecidaERecusada() {
        assertThatThrownBy(() -> Filtro.refeicoesPedidas("brunch"))
                .isInstanceOf(ParametroInvalido.class)
                .hasMessageContaining("brunch")
                .satisfies(e -> {
                    ParametroInvalido p = (ParametroInvalido) e;
                    assertThat(p.parametro()).isEqualTo("refeicoes");
                    assertThat(p.aceitos().toString()).contains("Almoço").contains("Jantar");
                });
    }
}
