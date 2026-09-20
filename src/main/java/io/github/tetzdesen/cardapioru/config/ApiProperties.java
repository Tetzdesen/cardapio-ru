package io.github.tetzdesen.cardapioru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api")
public record ApiProperties(String token) {

    public boolean protegida() {
        return token != null && !token.isBlank();
    }
}
