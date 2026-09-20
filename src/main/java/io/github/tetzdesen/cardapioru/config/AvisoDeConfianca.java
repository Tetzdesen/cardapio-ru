package io.github.tetzdesen.cardapioru.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A ausencia do intermediario aparece na partida, e nao so na primeira consulta
 * que falhar -- e o que o cenario "Ausencia do intermediario e detectada na
 * partida" pede. Nao derruba a aplicacao: a saude nao depende da UFES.
 */
@Component
public class AvisoDeConfianca {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeConfianca.class);

    @EventListener(ApplicationReadyEvent.class)
    public void verificar() {
        if (ConfiancaTls.intermediarioDisponivel()) {
            log.info("intermediario da RNP/ICPEdu carregado; verificacao TLS ativa");
        } else {
            log.warn("intermediario da RNP/ICPEdu AUSENTE -- o download do cardapio vai "
                    + "falhar no handshake.\n{}", ConfiancaTls.COMO_OBTER);
        }
    }
}
