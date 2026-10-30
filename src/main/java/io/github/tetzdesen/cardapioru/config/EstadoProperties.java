package io.github.tetzdesen.cardapioru.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Onde vive o estado de envio: um arquivo JSON no formato do ru_bot.py, e nao um
 * banco. No Render, ele sobrevive ao reinicio pelo espelho no GitHub
 * ({@link EstadoGithubProperties}).
 */
@ConfigurationProperties(prefix = "estado")
public record EstadoProperties(Path caminho) {
}
