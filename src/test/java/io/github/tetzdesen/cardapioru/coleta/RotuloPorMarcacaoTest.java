package io.github.tetzdesen.cardapioru.coleta;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.dominio.Analise;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * A deteccao de rotulo usa a marcacao do site, nao o formato do texto. Sem isso,
 * "Pirao" sozinho numa linha seria indistinguivel de um rotulo de secao.
 */
class RotuloPorMarcacaoTest {

    private final ParserCardapio parser = new ParserCardapio(new CardapioProperties(
            "http://exemplo", "teste", Duration.ofSeconds(30), 3, Duration.ofSeconds(2),
            Duration.ofSeconds(60), Duration.ofSeconds(75), 30, ZoneOffset.ofHours(-3),
            Duration.ofSeconds(60), 400));

    private static String pagina(String miolo) {
        return """
                <html><body><div id="content-core">
                <p><strong>Almoço (Alegre) - sexta-feira, 11 de Setembro de 2026</strong></p>
                %s
                <p>O cardápio poderá sofrer alterações.</p>
                </div></body></html>
                """.formatted(miolo)
                // volume de texto suficiente para nao cair no limiar de conteudo
                + "<!-- " + "x".repeat(500) + " -->";
    }

    @Test
    void itemLexicalmenteIdenticoAUmRotuloNaoViraRotulo() {
        // "Pirão" e "Laranja" tem a mesma cara de rotulo; so a marcacao distingue.
        Analise analise = parser.analisar(pagina("""
                <p><strong>Acompanhamento</strong></p>
                <p>Pirão</p>
                <p>Laranja</p>
                """));

        BlocoRefeicao bloco = analise.blocos().get(0);
        assertThat(bloco.secoes()).hasSize(1);
        assertThat(bloco.secoes().get(0).rotulo()).isEqualTo("Acompanhamento");
        assertThat(bloco.secoes().get(0).itens()).containsExactly("Pirão", "Laranja");
        assertThat(analise.avisos()).isEmpty();
    }

    @Test
    void oMesmoTextoMarcadoComoRotuloAbreSecao() {
        Analise analise = parser.analisar(pagina("""
                <p><strong>Acompanhamento</strong></p>
                <p>Arroz</p>
                <p><strong>Pirão</strong></p>
                <p>de peixe</p>
                """));

        BlocoRefeicao bloco = analise.blocos().get(0);
        assertThat(bloco.secoes().stream().map(Secao::rotulo))
                .containsExactly("Acompanhamento", "Pirão");
        assertThat(analise.avisos())
                .anySatisfy(a -> assertThat(a).contains("Pirão"));
    }

    @Test
    void rotuloNovoAbreSecaoEAvisa() {
        Analise analise = parser.analisar(pagina("""
                <p><strong>Entrada</strong></p>
                <p>Alface</p>
                <p><strong>Molho</strong></p>
                <p>Vinagrete</p>
                """));

        BlocoRefeicao bloco = analise.blocos().get(0);
        assertThat(bloco.secoes().stream().map(Secao::rotulo))
                .containsExactly("Entrada", "Molho");
        assertThat(bloco.secoes().get(1).itens()).containsExactly("Vinagrete");

        // o conteudo continua saindo: o aviso e observabilidade, nao falha
        assertThat(analise.reconhecida()).isTrue();
        assertThat(analise.avisos()).hasSize(1);
        assertThat(analise.avisos().get(0)).contains("Molho").contains("CATEGORIAS");
    }
}
