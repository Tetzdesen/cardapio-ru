package io.github.tetzdesen.cardapioru.coleta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Relogio de mentira: a espera nao dorme, mas adianta o relogio de verdade. */
final class TempoFalso implements Tempo {

    private final List<Duration> esperas = new ArrayList<>();
    private long agora;

    @Override
    public long nanos() {
        return agora;
    }

    @Override
    public void esperar(Duration quanto) {
        esperas.add(quanto);
        avancar(quanto);
    }

    void avancar(Duration quanto) {
        agora += quanto.toNanos();
    }

    List<Duration> esperas() {
        return List.copyOf(esperas);
    }

    Duration decorrido() {
        return Duration.ofNanos(agora);
    }
}
