package io.github.tetzdesen.cardapioru.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** O WAHA que mantem o numero conectado ao WhatsApp. Ver whatsapp/README.md. */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsappProperties(
        String urlBase,
        String apiKey,
        String sessao,
        String chatId,
        Duration timeout,
        int limiteMensagem) {

    public boolean configurado() {
        return urlBase != null && !urlBase.isBlank() && apiKey != null && !apiKey.isBlank()
                && chatId != null && !chatId.isBlank();
    }
}
