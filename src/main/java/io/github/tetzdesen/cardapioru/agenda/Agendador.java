package io.github.tetzdesen.cardapioru.agenda;

import io.github.tetzdesen.cardapioru.coleta.Filtro;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>Entre um anuncio e outro, a vigia reconfere o site e reanuncia, com aviso,
 * a refeicao que ja foi para o grupo e mudou depois disso. A que ainda nao foi
 * anunciada fica para o seu horario.
 *
 * <p>A falha de um disparo vai ao log e, se configurado, ao chat de alerta: no
 * Render nao ha codigo de saida para alguem ver.
 *
 * <p>A refeicao cujo anuncio falhou e tentada de novo a cada rodada da
 * {@link #recuperar recuperacao}, ate o prazo dela -- depois disso o RU ja esta
 * fechando e anunciar nao serve a ninguem. Repetir e seguro porque o disparo e
 * idempotente pelo estado: o canal que ja recebeu nao recebe de novo. So entra o
 * que falhou neste processo; o que nem chegou a rodar (o servico reiniciando na
 * hora exata) fica de fora, porque refazer tudo depois de cada reinicio
 * repetiria as mensagens que nao sao gravadas, como "Sem jantar hoje".
 */
@Component
@Profile("web")
@ConditionalOnProperty(name = "agenda.ativa", havingValue = "true")
@EnableScheduling
public class Agendador {

    private static final Logger log = LoggerFactory.getLogger(Agendador.class);

    private static final String CAMPUS = "alegre";

    /** O que cada anuncio pede: "cafe da manha" cobre Desjejum e Cafe da Manha. */
    private static final Map<String, String> REFEICOES = Map.of(
            "desjejum", "cafe da manha", "almoco", "almoco", "jantar", "jantar");

    private final ServicoNotificacao notificacao;
    private final ServicoCardapio cardapio;
    private final Alerta alerta;
    private final Clock relogio;
    private final Map<String, LocalTime> prazos;
    private final String urlPublica;

    /** Os anuncios que falharam, pelo dia em que falharam. */
    private final Map<String, LocalDate> pendentes = new ConcurrentHashMap<>();
    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Autowired
    public Agendador(ServicoNotificacao notificacao, ServicoCardapio cardapio, Alerta alerta,
            Clock relogio,
            @Value("${agenda.url-publica:}") String urlPublica,
            @Value("${agenda.prazo.desjejum}") String prazoDesjejum,
            @Value("${agenda.prazo.almoco}") String prazoAlmoco,
            @Value("${agenda.prazo.jantar}") String prazoJantar) {
        this(notificacao, cardapio, alerta, relogio, urlPublica, Map.of(
                "desjejum", LocalTime.parse(prazoDesjejum),
                "almoco", LocalTime.parse(prazoAlmoco),
                "jantar", LocalTime.parse(prazoJantar)));
    }

    Agendador(ServicoNotificacao notificacao, ServicoCardapio cardapio, Alerta alerta,
            Clock relogio, String urlPublica, Map<String, LocalTime> prazos) {
        this.notificacao = notificacao;
        this.cardapio = cardapio;
        this.alerta = alerta;
        this.relogio = relogio;
        this.prazos = Map.copyOf(prazos);
        this.urlPublica = urlPublica;
        if (urlPublica.isBlank()) {
            log.warn("agenda.url-publica vazia: nada mantem o servico acordado, e num host "
                    + "que desliga por inatividade o anuncio nao vai disparar");
        }
    }

    @Scheduled(cron = "${agenda.desjejum}", zone = "${agenda.fuso}")
    public void desjejum() {
        disparar("desjejum");
    }

    /** Tarde o bastante para pegar as correcoes que o site costuma fazer de manha. */
    @Scheduled(cron = "${agenda.almoco}", zone = "${agenda.fuso}")
    public void almoco() {
        disparar("almoco");
    }

    @Scheduled(cron = "${agenda.jantar}", zone = "${agenda.fuso}")
    public void jantar() {
        disparar("jantar");
    }

    /** Todas as refeicoes do dia: so as ja anunciadas que mudaram vao ao grupo. */
    @Scheduled(cron = "${agenda.vigia}", zone = "${agenda.fuso}")
    public void vigiar() {
        try {
            var relato = notificacao.disparar(cardapio.hoje(), List.of(), CAMPUS, true, true);
            if (!relato.enviadas().isEmpty()) {
                log.info("vigia: reanunciadas por mudanca {}", relato.enviadas());
            }
        } catch (RuntimeException e) {
            log.error("vigia: a reconferencia falhou", e);
            alerta.falhou("vigia", e);
        }
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

    /** Tenta de novo os anuncios que falharam hoje, ate o prazo de cada um. */
    @Scheduled(cron = "${agenda.recuperacao}", zone = "${agenda.fuso}")
    public void recuperar() {
        LocalDate hoje = cardapio.hoje();
        LocalTime agora = LocalTime.now(relogio);
        for (Map.Entry<String, LocalDate> pendente : Map.copyOf(pendentes).entrySet()) {
            String qual = pendente.getKey();
            if (!pendente.getValue().equals(hoje)) {
                pendentes.remove(qual); // de outro dia: o anuncio dele ja passou
            } else if (agora.isBefore(prazos.get(qual))) {
                log.info("agenda ({}): tentando de novo o anuncio que falhou", qual);
                disparar(qual);
            } else {
                pendentes.remove(qual);
                log.warn("agenda ({}): passou do prazo de {}; o anuncio nao saiu", qual,
                        prazos.get(qual));
                alerta.desistiu(qual);
            }
        }
    }

    private void disparar(String qual) {
        List<String> pedidas = Filtro.refeicoesPedidas(REFEICOES.get(qual));
        LocalDate hoje = cardapio.hoje();
        try {
            var relato = notificacao.disparar(hoje, pedidas, CAMPUS, true);
            log.info("agenda ({}): enviadas {}, omitidas {}", qual, relato.enviadas(),
                    relato.omitidas());
            if (pendentes.remove(qual) != null) {
                alerta.recuperou(qual);
            }
        } catch (RuntimeException e) {
            // sem quem leia o codigo de saida, o log e o unico lugar onde a falha aparece
            log.error("agenda ({}): o disparo falhou", qual, e);
            pendentes.put(qual, hoje);
            alerta.falhou(qual, e);
        }
    }
}
