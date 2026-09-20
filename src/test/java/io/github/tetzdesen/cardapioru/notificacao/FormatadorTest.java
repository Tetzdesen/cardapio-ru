package io.github.tetzdesen.cardapioru.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ParserCardapio;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.dominio.Analise;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Se a assinatura divergir do Python, a virada reenvia o dia inteiro sem motivo
 * e o "so reenvia o que mudou" deixa de valer. Por isso a comparacao e byte a
 * byte com o que o ru_bot.py produzia para as mesmas fixtures.
 */
class FormatadorTest {

    private static JsonNode referencia;

    @BeforeAll
    static void lerReferencia() throws Exception {
        referencia = new ObjectMapper()
                .readTree(FormatadorTest.class.getResourceAsStream("/referencia-ru-bot.json"));
    }

    private static Analise analisar() {
        return analisar(Fixtures.COMPLETA);
    }

    private static Analise analisar(String nome) {
        CardapioProperties props = new CardapioProperties("http://exemplo", "teste",
                Duration.ofSeconds(30), 3, Duration.ofSeconds(2), Duration.ofSeconds(60),
                Duration.ofSeconds(75), 30, ZoneOffset.ofHours(-3), Duration.ofSeconds(60), 400);
        return new ParserCardapio(props).analisar(Fixtures.ler(nome));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {Fixtures.COMPLETA, Fixtures.AO_VIVO})
    void textoFormatadoEIdenticoAoDoPython(String nome) {
        Analise analise = analisar(nome);
        JsonNode esperados = referencia.get(nome).get("blocos");

        for (int i = 0; i < esperados.size(); i++) {
            BlocoRefeicao bloco = analise.blocos().get(i);
            assertThat(Formatador.bloco(bloco))
                    .as("formatacao de " + bloco.refeicao())
                    .isEqualTo(esperados.get(i).get("formatado").asText());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {Fixtures.COMPLETA, Fixtures.AO_VIVO})
    void assinaturaEIdenticaADoPython(String nome) {
        Analise analise = analisar(nome);
        JsonNode esperados = referencia.get(nome).get("blocos");

        for (int i = 0; i < esperados.size(); i++) {
            BlocoRefeicao bloco = analise.blocos().get(i);
            assertThat(Formatador.assinatura(bloco))
                    .as("assinatura de " + bloco.refeicao())
                    .isEqualTo(esperados.get(i).get("assinatura").asText());
        }
    }

    @Test
    void escapeSegueOHtmlEscapeDoPython() {
        assertThat(Formatador.escapar("Pão & Cia <b>\"x\" 'y'</b>"))
                .isEqualTo("Pão &amp; Cia &lt;b&gt;&quot;x&quot; &#x27;y&#x27;&lt;/b&gt;");
    }
}
