package io.github.tetzdesen.cardapioru.agenda;

import io.github.tetzdesen.cardapioru.coleta.Filtro;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O anuncio rodando dentro da propria API, para hospedar tudo num lugar so. Sem
 * {@code agenda.ativa=true} nao existe: a API local e os testes nao falam com o
 * grupo sozinhos.
 *
 * <p>O plano gratis do Render desliga o servico depois de 15 minutos sem
 * requisicao, e servico desligado nao dispara nada. Por isso a API chama a si
 * mesma pela URL publica -- precisa ser a publica, e nao localhost, porque so o
 * que passa pela borda do Render conta como trafego.
 *
 * <p>Cada refeicao sai numa mensagem propria, pouco antes de o RU abrir para ela:
 * quem le o aviso do jantar de manha ja esqueceu quando chega a hora. Pedir uma
 * refeicao por disparo e o que separa as mensagens -- o cardapio do dia ainda e
 * consultado inteiro, mas so a refeicao da vez vai para o grupo.
 *
 * <p>O estado fica no disco do conteiner e some quando ele reinicia. Como cada
 * refeicao so e pedida uma vez por dia, um reinicio nao faz nada chegar de novo.
 */
@Component
@Profile("web")
@ConditionalOnProperty(name = "agenda.ativa", havingValue = "true")
@EnableScheduling
public class Agendador {

    private static final Logger log = LoggerFactory.getLogger(Agendador.class);

    private static final String CAMPUS = "alegre";

    private final ServicoNotificacao notificacao;
    private final ServicoCardapio cardapio;
    private final String urlPublica;
    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Agendador(ServicoNotificacao notificacao, ServicoCardapio cardapio,
            @Value("${agenda.url-publica:}") String urlPublica) {
        this.notificacao = notificacao;
        this.cardapio = cardapio;
        this.urlPublica = urlPublica;
        if (urlPublica.isBlank()) {
            log.warn("agenda.url-publica vazia: nada mantem o servico acordado, e num host "
                    + "que desliga por inatividade o anuncio nao vai disparar");
        }
    }

    /** "cafe da manha" cobre os dois nomes que o site usa: Desjejum e Cafe da Manha. */
    @Scheduled(cron = "${agenda.desjejum}", zone = "${agenda.fuso}")
    public void desjejum() {
        disparar("desjejum", "cafe da manha");
    }

    /** Tarde o bastante para pegar as correcoes que o site costuma fazer de manha. */
    @Scheduled(cron = "${agenda.almoco}", zone = "${agenda.fuso}")
    public void almoco() {
        disparar("almoco", "almoco");
    }

    @Scheduled(cron = "${agenda.jantar}", zone = "${agenda.fuso}")
    public void jantar() {
        disparar("jantar", "jantar");
    }

    @Scheduled(fixedDelayString = "${agenda.manter-acordado}",
            initialDelayString = "${agenda.manter-acordado}")
    public void manterAcordado() {
        if (urlPublica.isBlank()) {
            return;
        }
        HttpRequest pedido = HttpRequest.newBuilder(
                        URI.create(urlPublica.replaceAll("/+$", "") + "/actuator/health"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            int status = cliente.send(pedido, HttpResponse.BodyHandlers.discarding())
                    .statusCode();
            log.debug("manter acordado: {}", status);
        } catch (Exception e) {
            // falhar aqui nao e grave: o proximo ping tenta de novo antes dos 15 min
            log.warn("manter acordado falhou: {}", e.toString());
        }
    }

    private void disparar(String qual, String refeicoes) {
        List<String> pedidas = Filtro.refeicoesPedidas(refeicoes);
        try {
            var relato = notificacao.disparar(cardapio.hoje(), pedidas, CAMPUS, true);
            log.info("agenda ({}): enviadas {}, omitidas {}", qual, relato.enviadas(),
                    relato.omitidas());
        } catch (RuntimeException e) {
            // sem quem leia o codigo de saida, o log e o unico lugar onde a falha aparece
            log.error("agenda ({}): o disparo falhou", qual, e);
        }
    }
}
