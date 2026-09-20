package io.github.tetzdesen.cardapioru.coleta;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.dominio.Analise;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Paridade com o ru_bot.py. A referencia em referencia-ru-bot.json foi gerada
 * rodando o parser Python sobre as mesmas fixtures; congela-la aqui e o que
 * mantem a comparacao valida depois que o Python sair do repositorio.
 */
class ParserCardapioTest {

    private static JsonNode referencia;

    private final ParserCardapio parser = new ParserCardapio(props());

    private static CardapioProperties props() {
        return new CardapioProperties("http://exemplo", "teste", Duration.ofSeconds(30), 3,
                Duration.ofSeconds(2), Duration.ofSeconds(60), Duration.ofSeconds(75), 30,
                ZoneOffset.ofHours(-3), Duration.ofSeconds(60), 400);
    }

    @BeforeAll
    static void lerReferencia() throws Exception {
        referencia = new ObjectMapper()
                .readTree(ParserCardapioTest.class.getResourceAsStream("/referencia-ru-bot.json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {Fixtures.COMPLETA, Fixtures.VAZIA, Fixtures.QUEBRADA,
            Fixtures.AO_VIVO})
    void reconheceAEstruturaComoOPythonReconhecia(String nome) {
        Analise analise = parser.analisar(Fixtures.ler(nome));
        assertThat(analise.reconhecida())
                .isEqualTo(referencia.get(nome).get("reconhecida").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {Fixtures.COMPLETA, Fixtures.AO_VIVO})
    void blocosBatemComOPython(String nome) {
        Analise analise = parser.analisar(Fixtures.ler(nome));
        JsonNode esperados = referencia.get(nome).get("blocos");

        assertThat(analise.blocos()).hasSize(esperados.size());

        for (int i = 0; i < esperados.size(); i++) {
            BlocoRefeicao obtido = analise.blocos().get(i);
            JsonNode esperado = esperados.get(i);

            assertThat(obtido.refeicao()).isEqualTo(esperado.get("refeicao").asText());
            assertThat(obtido.campus()).isEqualTo(esperado.get("campus").asText());
            assertThat(obtido.data()).isEqualTo(esperado.get("data").asText());

            JsonNode secoes = esperado.get("secoes");
            assertThat(obtido.secoes()).hasSize(secoes.size());
            for (int j = 0; j < secoes.size(); j++) {
                Secao secao = obtido.secoes().get(j);
                assertThat(secao.rotulo()).isEqualTo(secoes.get(j).get("rotulo").asText());
                assertThat(secao.itens())
                        .containsExactlyElementsOf(itensDe(secoes.get(j)));
            }
        }
    }

    private static List<String> itensDe(JsonNode secao) {
        return secao.get("itens").valueStream().map(JsonNode::asText).toList();
    }

    @Test
    void ordemDasSecoesEADaPagina() {
        Analise analise = parser.analisar(Fixtures.ler(Fixtures.COMPLETA));
        BlocoRefeicao almoco = analise.blocos().stream()
                .filter(b -> b.refeicao().equals("Almoço")).findFirst().orElseThrow();

        assertThat(almoco.secoes().stream().map(Secao::rotulo))
                .containsExactly("Entrada", "Prato Proteico", "Opção", "Acompanhamento",
                        "Guarnição", "Sobremesa", "Suco");
    }

    @Test
    void diaSemCardapioNaoEFalhaDeParser() {
        Analise analise = parser.analisar(Fixtures.ler(Fixtures.VAZIA));

        assertThat(analise.reconhecida()).isTrue();
        assertThat(analise.blocos()).isEmpty();
    }

    @Test
    void siteReformadoCaiEmRamoDiferenteDeDiaVazio() {
        Analise vazia = parser.analisar(Fixtures.ler(Fixtures.VAZIA));
        Analise quebrada = parser.analisar(Fixtures.ler(Fixtures.QUEBRADA));

        assertThat(vazia.reconhecida()).isTrue();
        assertThat(quebrada.reconhecida()).isFalse();
        assertThat(quebrada.blocos()).isEmpty();
    }

    @Test
    void rotulosMarcadosBatemComOPython() {
        ParserCardapio.Pagina pagina = parser.lerPagina(Fixtures.ler(Fixtures.COMPLETA));
        List<String> esperados = referencia.get(Fixtures.COMPLETA)
                .get("rotulos_marcados").valueStream().map(JsonNode::asText).toList();

        assertThat(pagina.rotulos()).containsExactlyInAnyOrderElementsOf(esperados);
    }
}
