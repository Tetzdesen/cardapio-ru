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
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>O cardapio do dia inteiro sai numa mensagem so, as 05h40, quando quem vai
 * ao RU esta acordando. A vigia cuida das correcoes que o site faz depois.
 *
 * <p>Entre o anuncio e a noite, a vigia reconfere o site e reanuncia, com aviso,
 * a refeicao que mudou depois de ir para o grupo.
 *
 * <p>A falha de um disparo vai ao log e, se configurado, ao chat de alerta: no
 * Render nao ha codigo de saida para alguem ver.
 *
 * <p>Se o anuncio falhar, a {@link #recuperar recuperacao} tenta de novo a cada
 * rodada, pedindo so as refeicoes cujo prazo ainda nao passou -- depois dele o RU
 * ja esta fechando e anunciar nao serve a ninguem. Passado o ultimo prazo, desiste.
 * Repetir e seguro porque o disparo e idempotente pelo estado: o canal que ja
 * recebeu nao recebe de novo. So entra o que falhou neste processo; o que nem
 * chegou a rodar (o servico reiniciando na hora exata) fica de fora, porque
 * refazer tudo depois de cada reinicio repetiria as mensagens que nao sao
 * gravadas, como "Sem jantar hoje".
 */
@Component
@Profile("web")
@ConditionalOnProperty(name = "agenda.ativa", havingValue = "true")
@EnableScheduling
public class Agendador {

    private static final Logger log = LoggerFactory.getLogger(Agendador.class);

    private static final String CAMPUS = "alegre";

    /** Nome do anuncio nos logs e alertas. */
    private static final String ANUNCIO = "cardapio do dia";

    /** O que cada refeicao pede: "cafe da manha" cobre Desjejum e Cafe da Manha. */
    private static final Map<String, String> REFEICOES = Map.of(
            "desjejum", "cafe da manha", "almoco", "almoco", "jantar", "jantar");

    /** Ordem das refeicoes no dia. */
    private static final List<String> ORDEM = List.of("desjejum", "almoco", "jantar");

    private final ServicoNotificacao notificacao;
    private final ServicoCardapio cardapio;
    private final Alerta alerta;
    private final Clock relogio;
    private final Map<String, LocalTime> prazos;
    private final String urlPublica;

    /** O dia cujo anuncio falhou e ainda nao saiu; null quando nao ha pendencia. */
    private final AtomicReference<LocalDate> pendente = new AtomicReference<>();
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

    /** O cardapio do dia inteiro, numa mensagem so. */
    @Scheduled(cron = "${agenda.anuncio}", zone = "${agenda.fuso}")
    public void anunciar() {
        disparar(List.of());
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

    /** Tenta de novo o anuncio que falhou hoje, so com as refeicoes ainda no prazo. */
    @Scheduled(cron = "${agenda.recuperacao}", zone = "${agenda.fuso}")
    public void recuperar() {
        LocalDate dia = pendente.get();
        if (dia == null) {
            return;
        }
        if (!dia.equals(cardapio.hoje())) {
            pendente.set(null); // de outro dia: o anuncio dele ja passou
            return;
        }
        LocalTime agora = LocalTime.now(relogio);
        List<String> abertas = ORDEM.stream().filter(r -> agora.isBefore(prazos.get(r))).toList();
        if (abertas.isEmpty()) {
            pendente.set(null);
            log.warn("agenda: passou do prazo do jantar ({}); o anuncio nao saiu",
                    prazos.get("jantar"));
            alerta.desistiu(ANUNCIO);
            return;
        }
        log.info("agenda: tentando de novo o anuncio que falhou, com {}", abertas);
        disparar(abertas.size() == ORDEM.size() ? List.of() : Filtro.refeicoesPedidas(
                String.join(",", abertas.stream().map(REFEICOES::get).toList())));
    }

    /** {@code pedidas} vazia e o dia inteiro. */
    private void disparar(List<String> pedidas) {
        LocalDate hoje = cardapio.hoje();
        try {
            var relato = notificacao.disparar(hoje, pedidas, CAMPUS, true);
            log.info("agenda: enviadas {}, omitidas {}", relato.enviadas(), relato.omitidas());
            if (pendente.getAndSet(null) != null) {
                alerta.recuperou(ANUNCIO);
            }
        } catch (RuntimeException e) {
            // sem quem leia o codigo de saida, o log e o unico lugar onde a falha aparece
            log.error("agenda: o disparo falhou", e);
            pendente.set(hoje);
            alerta.falhou(ANUNCIO, e);
        }
    }
}
