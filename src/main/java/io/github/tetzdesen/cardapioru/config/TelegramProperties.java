package io.github.tetzdesen.cardapioru.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String token,
        String chatId,
        String chatAlerta,
        String urlBase,
        Duration timeout,
        int limiteMensagem) {

    public boolean configurado() {
        return token != null && !token.isBlank() && chatId != null && !chatId.isBlank();
    }

    /** Se as falhas do anuncio vao para um chat privado de quem cuida do bot. */
    public boolean alertaConfigurado() {
        return token != null && !token.isBlank()
                && chatAlerta != null && !chatAlerta.isBlank();
    }
}
