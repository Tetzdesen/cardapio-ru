package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Decide o que e novidade, envia e relata. E por aqui que entram tanto o
 * controller quanto a execucao de uma tacada so -- ter um caminho so e o que
 * impede o CLI e o HTTP de divergirem.
 *
 * <p>Nao ha refazer depois de uma falha: sem indice unico nao existe corrida a
 * perder, e repetir o disparo seria um segundo envio ao grupo. Dois disparos no
 * mesmo processo -- o agendador e uma chamada HTTP -- esperam um pelo outro, para
 * que o segundo leia o estado que o primeiro gravou.
 */
@Service
public class ServicoNotificacao {

    private final DisparoTransacional disparo;

    public ServicoNotificacao(DisparoTransacional disparo) {
        this.disparo = disparo;
    }

    public synchronized DisparoResposta disparar(LocalDate data, List<String> refeicoes, String campus,
            boolean silencioso) {
        return disparo.executar(data, refeicoes, campus, silencioso);
    }
}
