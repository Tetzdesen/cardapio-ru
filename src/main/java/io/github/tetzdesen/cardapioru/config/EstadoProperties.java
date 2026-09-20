package io.github.tetzdesen.cardapioru.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Onde vive o estado de envio. E um arquivo versionado com o projeto, e nao um
 * banco: o runner do agendamento e efemero, entao o que precisa sobreviver entre
 * execucoes volta para o repositorio por um commit -- ver design.md, "O estado
 * vira um arquivo JSON no formato do ru_bot.py".
 */
@ConfigurationProperties(prefix = "estado")
public record EstadoProperties(Path caminho) {
}
