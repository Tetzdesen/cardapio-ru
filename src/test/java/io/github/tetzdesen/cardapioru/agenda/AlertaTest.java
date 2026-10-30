package io.github.tetzdesen.cardapioru.agenda;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.tetzdesen.cardapioru.notificacao.ClienteTelegram;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AlertaTest {

    private final ClienteTelegram telegram = mock(ClienteTelegram.class);
    private Instant agora = Instant.parse("2026-10-30T09:00:00Z");
    private final Clock relogio = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    };
    private final Alerta alerta = new Alerta(telegram, relogio);

    @Test
    void mandaOMotivoEscapado() {
        given(telegram.alertar(anyString())).willReturn(true);

        alerta.falhou("almoco", new IllegalStateException("site <fora>"));

        verify(telegram).alertar(contains("<code>site &lt;fora&gt;</code>"));
    }

    @Test
    void mesmaFalhaRepetidaSoAlertaDeTresEmTresHoras() {
        given(telegram.alertar(anyString())).willReturn(true);

        alerta.falhou("vigia", new IllegalStateException("fora"));
        agora = agora.plus(Duration.ofMinutes(20));
        alerta.falhou("vigia", new IllegalStateException("fora"));
        alerta.falhou("jantar", new IllegalStateException("fora"));
        agora = agora.plus(Alerta.INTERVALO);
        alerta.falhou("vigia", new IllegalStateException("fora"));

        verify(telegram, times(2)).alertar(contains("(vigia)"));
        verify(telegram).alertar(contains("(jantar)"));
    }

    @Test
    void recuperacaoSoEAvisadaSeAFalhaFoi() {
        given(telegram.alertar(anyString())).willReturn(true);

        alerta.recuperou("almoco"); // nenhuma falha avisada: nada a dizer
        alerta.falhou("almoco", new IllegalStateException("fora"));
        alerta.recuperou("almoco");

        verify(telegram).alertar(contains("saiu na nova tentativa"));
    }

    @Test
    void recuperarLiberaOProximoAlertaDeFalha() {
        given(telegram.alertar(anyString())).willReturn(true);

        alerta.falhou("almoco", new IllegalStateException("fora"));
        alerta.recuperou("almoco");
        alerta.falhou("almoco", new IllegalStateException("fora de novo"));

        verify(telegram, times(2)).alertar(contains("falhou"));
    }

    @Test
    void alertaQueNaoChegouTentaDeNovoNaProxima() {
        given(telegram.alertar(anyString())).willReturn(false, true);

        alerta.falhou("vigia", new IllegalStateException("fora"));
        alerta.falhou("vigia", new IllegalStateException("fora"));

        verify(telegram, times(2)).alertar(anyString());
    }
}
