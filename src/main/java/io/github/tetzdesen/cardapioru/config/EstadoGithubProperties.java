package io.github.tetzdesen.cardapioru.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Onde o estado e espelhado no GitHub, para sobreviver ao reinicio do conteiner.
 * Sem {@code token}, nao ha espelho e o estado vive so no disco.
 *
 * <p>O {@code ramo} nao e o {@code main} de proposito: o Render faz deploy a cada
 * commit no {@code main}, e um deploy por anuncio reiniciaria o servico.
 */
@ConfigurationProperties(prefix = "estado.github")
public record EstadoGithubProperties(
        String urlBase,
        String repositorio,
        String ramo,
        String caminho,
        String token,
        Duration timeout) {

    public boolean ativo() {
        return token != null && !token.isBlank();
    }
}
