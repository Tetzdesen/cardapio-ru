package io.github.tetzdesen.cardapioru.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Os valores que no ru_bot.py eram constantes no topo do modulo. Virar
 * configuracao e o que permite apertar o orcamento de tempo por ambiente sem
 * recompilar -- ver design.md, "Orcamento de tempo".
 */
@ConfigurationProperties(prefix = "cardapio")
public record CardapioProperties(
        String urlBase,
        String userAgent,
        Duration timeout,
        int tentativas,
        Duration esperaBase,
        Duration esperaMaxima,
        Duration orcamentoTotal,
        int retencaoDias,
        ZoneId fuso,
        Duration cache,
        int limiarConteudo) {
}
