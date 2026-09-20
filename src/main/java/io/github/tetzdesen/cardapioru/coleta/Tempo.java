package io.github.tetzdesen.cardapioru.coleta;

import java.time.Duration;

/**
 * Relogio e espera juntos. Sao a mesma abstracao porque o orcamento de tentativas
 * so e verificavel se quem espera e quem mede o tempo forem o mesmo objeto: com
 * um relogio de parede e uma espera falsa, o teste nunca ve o orcamento acabar.
 */
public interface Tempo {

    long nanos();

    void esperar(Duration quanto) throws InterruptedException;

    static Tempo real() {
        return new Tempo() {
            @Override
            public long nanos() {
                return System.nanoTime();
            }

            @Override
            public void esperar(Duration quanto) throws InterruptedException {
                if (!quanto.isNegative() && !quanto.isZero()) {
                    Thread.sleep(quanto.toMillis());
                }
            }
        };
    }
}
