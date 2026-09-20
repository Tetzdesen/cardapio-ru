package io.github.tetzdesen.cardapioru.estado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.EstadoProperties;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O formato e o do {@code salvar_estado} do ru_bot.py, byte a byte: e o que
 * faz a virada ser silenciosa em vez de reanunciar o dia inteiro.
 */
class ArquivoDeEstadoTest {

    @TempDir
    private Path pasta;

    private static final CardapioProperties PROPS = new CardapioProperties("http://exemplo",
            "teste", Duration.ofSeconds(30), 3, Duration.ofSeconds(2), Duration.ofSeconds(60),
            Duration.ofSeconds(75), 30, ZoneOffset.ofHours(-3), Duration.ofSeconds(60), 400);

    private ArquivoDeEstado em(Path caminho) {
        return new ArquivoDeEstado(new EstadoProperties(caminho), PROPS);
    }

    @Test
    void leOArquivoVersionadoNoRepositorio() {
        // o estado que ja esta no repositorio, gravado pelo ru_bot.py
        Path real = Path.of("estado/ultimo-envio.json");
        assertThat(real).as("o estado herdado do ru_bot.py precisa continuar no repo").exists();

        Map<String, String> lido = em(real).ler();

        assertThat(lido).containsExactlyInAnyOrderEntriesOf(Map.of(
                "2026-09-15|almoco", "3697e402437c133a",
                "2026-09-15|jantar", "459106b701d9f528"));
    }

    @Test
    void arquivoAusenteEEstadoVazio() {
        assertThat(em(pasta.resolve("nao-existe.json")).ler()).isEmpty();
    }

    @Test
    void arquivoInteiroIlegivelEEstadoVazio() throws IOException {
        Path caminho = pasta.resolve("estado.json");
        Files.writeString(caminho, "{ isto nao e json", StandardCharsets.UTF_8);

        assertThat(em(caminho).ler()).isEmpty();
    }

    @Test
    void arquivoQueNaoEObjetoEEstadoVazio() throws IOException {
        Path caminho = pasta.resolve("estado.json");
        Files.writeString(caminho, "[\"almoco\"]", StandardCharsets.UTF_8);

        assertThat(em(caminho).ler()).isEmpty();
    }

    @Test
    void entradaDeFormatoAnteriorOuIlegivelEDescartadaSemDerrubarAsDemais()
            throws IOException {
        Path caminho = pasta.resolve("estado.json");
        Files.writeString(caminho, """
                {
                  "2026-09-16": "sem refeicao na chave",
                  "2026-09-16|brunch": "refeicao que o RU nao serve",
                  "ontem|almoco": "data que nao e data",
                  "2026-09-16|jantar": 42,
                  "2026-09-16|desjejum": "   ",
                  "2026-09-16|almoco": "3697e402437c133a"
                }
                """, StandardCharsets.UTF_8);

        assertThat(em(caminho).ler())
                .containsExactlyEntriesOf(Map.of("2026-09-16|almoco", "3697e402437c133a"));
    }

    @Test
    void gravaNoMesmoFormatoDoRuBot() throws IOException {
        Path caminho = pasta.resolve("estado.json");
        Map<String, String> estado = new LinkedHashMap<>();
        // fora de ordem de proposito: a gravacao ordena por chave
        estado.put(hoje("jantar"), "459106b701d9f528");
        estado.put(hoje("almoco"), "3697e402437c133a");

        em(caminho).gravar(estado);

        assertThat(Files.readString(caminho, StandardCharsets.UTF_8)).isEqualTo("""
                {
                  "%s": "3697e402437c133a",
                  "%s": "459106b701d9f528"
                }
                """.formatted(hoje("almoco"), hoje("jantar")));
    }

    @Test
    void estadoVazioGravaObjetoVazioComQuebraDeLinha() {
        assertThat(ArquivoDeEstado.serializar(new TreeMap<>())).isEqualTo("{}\n");
    }

    @Test
    void entradaVelhaSaiNaPoda() throws IOException {
        Path caminho = pasta.resolve("estado.json");
        Map<String, String> estado = new LinkedHashMap<>();
        estado.put(LocalDate.now(ZoneOffset.ofHours(-3)).minusDays(31) + "|almoco", "velha");
        estado.put(hoje("almoco"), "nova");

        em(caminho).gravar(estado);

        assertThat(em(caminho).ler()).containsOnlyKeys(hoje("almoco"));
    }

    @Test
    void criaAPastaQuandoElaAindaNaoExiste() {
        Path caminho = pasta.resolve("sub/pasta/estado.json");

        em(caminho).gravar(Map.of(hoje("almoco"), "3697e402437c133a"));

        assertThat(caminho).exists();
    }

    @Test
    void gravacaoImpossivelViraEstadoIndisponivel() throws IOException {
        // o caminho existe como diretorio: escrever nele nao tem como dar certo
        Path caminho = pasta.resolve("estado.json");
        Files.createDirectory(caminho);

        assertThatThrownBy(() -> em(caminho).gravar(Map.of(hoje("almoco"), "aaaa")))
                .isInstanceOf(EstadoIndisponivel.class)
                .hasMessageContaining("gravar o estado");
    }

    private static String hoje(String refeicao) {
        return LocalDate.now(ZoneOffset.ofHours(-3)) + "|" + refeicao;
    }
}
