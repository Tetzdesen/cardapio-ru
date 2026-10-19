package io.github.tetzdesen.cardapioru.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O "hoje" da aplicacao, no fuso do RU. Injetado em vez de lido do sistema para
 * que a poda por retencao seja verificavel com uma data fixa: com o relogio de
 * parede, todo teste com data escrita no codigo apodrece em {@code retencao-dias}.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio(CardapioProperties cardapio) {
        return Clock.system(cardapio.fuso());
    }
}
