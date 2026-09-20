package io.github.tetzdesen.cardapioru.config;

import io.github.tetzdesen.cardapioru.estado.ArquivoDeEstado;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Estado efemero perde o que ja foi enviado, e sem isso toda refeicao volta a
 * ser inedita: o cardapio do dia seria reanunciado no grupo. O mesmo raciocinio
 * que valia para banco em memoria vale para um caminho que nao sobrevive -- um
 * diretorio temporario, um conteiner sem volume. Se for para acontecer, que
 * apareca no log antes de custar um reenvio.
 */
@Component
public class AvisoDeEstado {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeEstado.class);

    private final ArquivoDeEstado arquivo;

    public AvisoDeEstado(ArquivoDeEstado arquivo) {
        this.arquivo = arquivo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificar() {
        Path caminho = arquivo.caminho().toAbsolutePath();
        log.info("estado de envio em {}{}", caminho,
                Files.exists(caminho) ? "" : " (ainda nao existe)");

        Path temporario = Path.of(System.getProperty("java.io.tmpdir"));
        if (caminho.startsWith(temporario)) {
            log.warn("o estado esta num diretorio temporario ({}): ele some, e o cardapio "
                    + "do dia sera reanunciado. Aponte 'estado.caminho' para um lugar que "
                    + "sobreviva entre execucoes.", caminho);
        }
    }
}
