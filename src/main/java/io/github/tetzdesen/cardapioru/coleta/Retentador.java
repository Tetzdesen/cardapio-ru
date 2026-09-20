package io.github.tetzdesen.cardapioru.coleta;

import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repete uma chamada em falha transitoria, com espera dobrando a cada vez --
 * porte do {@code _com_retry} do ru_bot.py.
 *
 * <p>A diferenca em relacao ao original e o <b>orcamento</b>: a soma das esperas
 * e dos timeouts nao pode passar de {@code orcamentoTotal}, porque do outro lado
 * ha um curl de workflow com paciencia finita. Estourar o orcamento significa
 * desistir e responder erro, nao continuar tentando as cegas.
 */
public class Retentador {

    /** Um resultado e repetivel ou nao; quando e, pode pedir uma espera especifica. */
    public record Veredito(boolean repetir, Duration esperaPedida) {

        public static Veredito aceitar() {
            return new Veredito(false, null);
        }

        public static Veredito repetirAgora() {
            return new Veredito(true, null);
        }

        public static Veredito repetirApos(Duration espera) {
            return new Veredito(true, espera);
        }
    }

    @FunctionalInterface
    public interface Chamada<T> {
        T chamar() throws IOException, InterruptedException;
    }

    @FunctionalInterface
    public interface Avaliacao<T> {
        Veredito avaliar(T resultado);
    }

    private static final Logger log = LoggerFactory.getLogger(Retentador.class);

    private final int tentativas;
    private final Duration esperaBase;
    private final Duration esperaMaxima;
    private final Duration orcamentoTotal;
    private final Duration timeoutPorTentativa;
    private final Tempo tempo;

    public Retentador(int tentativas, Duration esperaBase, Duration esperaMaxima,
            Duration orcamentoTotal, Duration timeoutPorTentativa, Tempo tempo) {
        this.tentativas = tentativas;
        this.esperaBase = esperaBase;
        this.esperaMaxima = esperaMaxima;
        this.orcamentoTotal = orcamentoTotal;
        this.timeoutPorTentativa = timeoutPorTentativa;
        this.tempo = tempo;
    }

    /**
     * Quanto tempo, no pior caso, este retentador pode consumir: o timeout de
     * cada tentativa mais as esperas entre elas. E o numero que precisa caber no
     * timeout do cliente.
     */
    public Duration piorCaso() {
        Duration total = timeoutPorTentativa.multipliedBy(tentativas);
        for (int t = 1; t < tentativas; t++) {
            total = total.plus(min(esperaDe(t), esperaMaxima));
        }
        return total.compareTo(orcamentoTotal) > 0 ? orcamentoTotal : total;
    }

    public <T> T executar(String descricao, Chamada<T> chamada, Avaliacao<T> avaliacao) {
        long inicio = tempo.nanos();
        Exception ultimoErro = null;
        int feitas = 0;

        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            feitas = tentativa;
            T resultado;
            try {
                resultado = chamada.chamar();
            } catch (SSLException e) {
                // Cadeia de certificado quebrada nao conserta sozinha: repetir so
                // atrasa a mensagem de erro que explica o conserto.
                throw new FalhaDeOrigem(FalhaDeOrigem.Causa.CONFIANCA_TLS,
                        e.getMessage() + "\n\n" + io.github.tetzdesen.cardapioru.config
                                .ConfiancaTls.COMO_OBTER, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL,
                        descricao + " interrompido", e);
            } catch (IOException e) {
                ultimoErro = e;
                Optional<Duration> espera = proximaEspera(tentativa, null, inicio);
                if (espera.isEmpty()) {
                    break;
                }
                log.warn("{} falhou ({}); tentativa {} de {} em {}s",
                        descricao, e, tentativa + 1, tentativas, espera.get().toSeconds());
                if (!dormir(espera.get())) {
                    break;
                }
                continue;
            }

            Veredito veredito = avaliacao.avaliar(resultado);
            if (!veredito.repetir()) {
                return resultado;
            }
            Optional<Duration> espera = proximaEspera(tentativa, veredito.esperaPedida(), inicio);
            if (espera.isEmpty()) {
                return resultado;
            }
            log.warn("{} pediu nova tentativa; tentativa {} de {} em {}s",
                    descricao, tentativa + 1, tentativas, espera.get().toSeconds());
            if (!dormir(espera.get())) {
                return resultado;
            }
        }

        // `feitas` e nao `tentativas`: o orcamento pode ter cortado antes do limite,
        // e um log que anuncia 5 tentativas tendo feito 3 manda depurar o lugar errado.
        log.error("{} falhou em {} de {} tentativa(s) permitidas: {}",
                descricao, feitas, tentativas, String.valueOf(ultimoErro), ultimoErro);
        throw new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL,
                descricao + " falhou em " + feitas + " tentativa(s): " + ultimoErro,
                ultimoErro);
    }

    /** Vazio quando nao ha mais tentativa: acabaram, ou o orcamento nao comporta. */
    private Optional<Duration> proximaEspera(int tentativa, Duration pedida, long inicio) {
        if (tentativa >= tentativas) {
            return Optional.empty();
        }
        Duration espera = min(pedida != null ? pedida : esperaDe(tentativa), esperaMaxima);
        Duration decorrido = Duration.ofNanos(tempo.nanos() - inicio);
        Duration apos = decorrido.plus(espera).plus(timeoutPorTentativa);
        if (apos.compareTo(orcamentoTotal) > 0) {
            log.warn("orcamento de {}s esgotado apos {}s; nao ha tempo para outra tentativa",
                    orcamentoTotal.toSeconds(), decorrido.toSeconds());
            return Optional.empty();
        }
        return Optional.of(espera);
    }

    private Duration esperaDe(int tentativa) {
        return esperaBase.multipliedBy(1L << (tentativa - 1));
    }

    private boolean dormir(Duration quanto) {
        try {
            tempo.esperar(quanto);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
