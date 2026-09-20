package io.github.tetzdesen.cardapioru.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guarda de projeto: desativar verificacao TLS nao pode ser uma opcao que exista
 * no codigo, nem atras de configuracao. Se alguem adicionar um trust-all numa
 * madrugada em que o cardapio nao chegou, e aqui que aparece.
 */
class VerificacaoTlsSempreAtivaTest {

    private static final List<String> PROIBIDOS = List.of(
            "checkServerTrusted",
            "HostnameVerifier",
            "NoopHostname",
            "TrustAllStrategy",
            "INSECURE",
            "setDefaultSSLSocketFactory");

    @Test
    void nenhumCaminhoDeCodigoDesabilitaVerificacao() throws IOException {
        try (Stream<Path> arquivos = Files.walk(Path.of("src/main/java"))) {
            List<String> suspeitos = arquivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String fonte = ler(p);
                        return PROIBIDOS.stream().anyMatch(fonte::contains);
                    })
                    .map(Path::toString)
                    .toList();

            assertThat(suspeitos)
                    .as("arquivos com indicio de verificacao TLS desligada")
                    .isEmpty();
        }
    }

    @Test
    void aplicationYmlNaoOfereceChaveParaDesligarTls() throws IOException {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"),
                StandardCharsets.UTF_8);
        assertThat(yml.toLowerCase())
                .doesNotContain("insecure")
                .doesNotContain("verify-ssl")
                .doesNotContain("trust-all");
    }

    private static String ler(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
