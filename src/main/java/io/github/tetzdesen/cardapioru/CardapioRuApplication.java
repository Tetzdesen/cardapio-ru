package io.github.tetzdesen.cardapioru;

import io.github.tetzdesen.cardapioru.cli.Argumentos;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CardapioRuApplication {

    /**
     * O padrao e o disparo de uma tacada so, que e o caminho que roda todo dia;
     * o perfil {@code web} sobe o servidor. Por isso o encerramento e explicito:
     * e dele que sai o codigo de saida que o agendador le.
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext contexto =
                SpringApplication.run(CardapioRuApplication.class, Argumentos.paraSpring(args));

        if (!(contexto instanceof WebApplicationContext)) {
            System.exit(SpringApplication.exit(contexto));
        }
    }
}
