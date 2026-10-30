package io.github.tetzdesen.cardapioru.agenda;

import io.github.tetzdesen.cardapioru.notificacao.ClienteTelegram;
import io.github.tetzdesen.cardapioru.notificacao.Formatador;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Avisa quem cuida do bot que um disparo agendado falhou. No Render nao ha
 * codigo de saida nem job vermelho: sem isto, a falha so existe no log, e o
 * grupo simplesmente fica sem o anuncio sem ninguem saber por que.
 *
 * <p>A vigia roda a cada 20 minutos; com o site fora do ar, cada rodada falharia
 * igual. Por isso cada tipo de disparo alerta no maximo uma vez a cada
 * {@link #INTERVALO}.
 */
@Component
public class Alerta {

    static final Duration INTERVALO = Duration.ofHours(3);

    private final ClienteTelegram telegram;
    private final Clock relogio;
    private final Map<String, Instant> ultimo = new HashMap<>();

    public Alerta(ClienteTelegram telegram, Clock relogio) {
        this.telegram = telegram;
        this.relogio = relogio;
    }

    public synchronized void falhou(String qual, Throwable falha) {
        Instant agora = relogio.instant();
        Instant anterior = ultimo.get(qual);
        if (anterior != null && anterior.plus(INTERVALO).isAfter(agora)) {
            return;
        }
        String motivo = falha.getMessage() == null ? falha.getClass().getSimpleName()
                : falha.getMessage();
        if (motivo.length() > 500) {
            motivo = motivo.substring(0, 500) + "…";
        }
        String texto = "⚠️ <b>Cardápio do RU: o anúncio (" + Formatador.escapar(qual)
                + ") falhou</b>\n<code>" + Formatador.escapar(motivo) + "</code>";
        if (telegram.alertar(texto)) {
            ultimo.put(qual, agora);
        }
    }
}
