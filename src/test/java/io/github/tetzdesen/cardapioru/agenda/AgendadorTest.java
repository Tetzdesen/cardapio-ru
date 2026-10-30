package io.github.tetzdesen.cardapioru.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgendadorTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 21);

    private final ServicoNotificacao notificacao = mock(ServicoNotificacao.class);
    private final ServicoCardapio cardapio = mock(ServicoCardapio.class);
    private final Alerta alerta = mock(Alerta.class);

    /** Relogio que o teste adianta: a recuperacao depende da hora. */
    private LocalTime agora = LocalTime.of(10, 30);
    private final Clock relogio = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.ofHours(-3);
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return HOJE.atTime(agora).toInstant(ZoneOffset.ofHours(-3));
        }
    };

    private final Agendador agendador = new Agendador(notificacao, cardapio, alerta, relogio,
            "", Map.of("desjejum", LocalTime.of(7, 30), "almoco", LocalTime.of(13, 0),
                    "jantar", LocalTime.of(19, 0)));

    private void almocoFalha() {
        given(notificacao.disparar(any(), any(), anyString(), anyBoolean())).willThrow(
                new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "site fora"));
    }

    private void almocoVolta() {
        org.mockito.BDDMockito.willReturn(new DisparoResposta(HOJE, true, List.of("Almoço"),
                List.of(), List.of(), List.of(), List.of()))
                .given(notificacao).disparar(any(), any(), anyString(), anyBoolean());
    }

    @BeforeEach
    void preparar() {
        given(cardapio.hoje()).willReturn(HOJE);
        given(notificacao.disparar(any(), any(), anyString(), anyBoolean())).willReturn(
                new DisparoResposta(HOJE, true, List.of(), List.of(), List.of(), List.of(),
                        List.of()));
        given(notificacao.disparar(any(), any(), anyString(), anyBoolean(), anyBoolean()))
                .willReturn(new DisparoResposta(HOJE, true, List.of(), List.of(), List.of(), List.of(),
                        List.of()));
    }

    @Test
    void desjejumPedeOsDoisNomesDoCafeDeAlegreEmSilencioSeVazio() {
        agendador.desjejum();

        verify(notificacao).disparar(HOJE, List.of("Desjejum", "Café da Manhã"), "alegre", true);
    }

    @Test
    void almocoPedeSoOAlmoco() {
        agendador.almoco();

        verify(notificacao).disparar(eq(HOJE), eq(List.of("almoco")), eq("alegre"), eq(true));
    }

    @Test
    void jantarPedeSoOJantar() {
        agendador.jantar();

        verify(notificacao).disparar(eq(HOJE), eq(List.of("jantar")), eq("alegre"), eq(true));
    }

    @Test
    void vigiaReconfereODiaInteiroSoPorAlteracoes() {
        agendador.vigiar();

        verify(notificacao).disparar(HOJE, List.of(), "alegre", true, true);
    }

    @Test
    void falhaDaVigiaNaoDerrubaOAgendador() {
        given(notificacao.disparar(any(), any(), anyString(), anyBoolean(), anyBoolean()))
                .willThrow(new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "site fora"));

        assertThatCode(agendador::vigiar).doesNotThrowAnyException();
    }

    @Test
    void falhaDoDisparoNaoDerrubaOAgendador() {
        // uma excecao que escapa aqui so vai para o log do scheduler; a nossa diz qual foi
        given(notificacao.disparar(any(), any(), anyString(), anyBoolean())).willThrow(
                new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "site fora"));

        assertThatCode(agendador::desjejum).doesNotThrowAnyException();
        verify(alerta).falhou(eq("desjejum"), any(FalhaDeOrigem.class));
    }

    @Test
    void anuncioQueFalhouETentadoDeNovoAteSair() {
        almocoFalha();
        agendador.almoco();

        agora = LocalTime.of(10, 40);
        agendador.recuperar(); // o site continua fora
        almocoVolta();
        agora = LocalTime.of(10, 50);
        agendador.recuperar();
        agora = LocalTime.of(11, 0);
        agendador.recuperar(); // ja saiu: nao tenta mais

        verify(notificacao, times(3)).disparar(eq(HOJE), eq(List.of("almoco")), eq("alegre"),
                eq(true));
        verify(alerta).recuperou("almoco");
        verify(alerta, never()).desistiu(anyString());
    }

    @Test
    void passadoOPrazoDesisteEAvisa() {
        almocoFalha();
        agendador.almoco();

        agora = LocalTime.of(13, 0);
        agendador.recuperar();
        agendador.recuperar();

        verify(notificacao, times(1)).disparar(any(), any(), anyString(), anyBoolean());
        verify(alerta, times(1)).desistiu("almoco");
    }

    @Test
    void anuncioQueSaiuNaoETentadoDeNovo() {
        agendador.almoco();
        agora = LocalTime.of(10, 40);

        agendador.recuperar();

        verify(notificacao, times(1)).disparar(any(), any(), anyString(), anyBoolean());
        verifyNoInteractions(alerta);
    }

    @Test
    void falhaDeOntemNaoETentadaHoje() {
        almocoFalha();
        given(cardapio.hoje()).willReturn(HOJE.minusDays(1));
        agendador.almoco();

        given(cardapio.hoje()).willReturn(HOJE);
        agendador.recuperar();

        verify(notificacao, times(1)).disparar(any(), any(), anyString(), anyBoolean());
        verify(alerta, never()).desistiu(anyString());
    }

    @Test
    void semUrlPublicaNaoPinga() {
        assertThatCode(agendador::manterAcordado).doesNotThrowAnyException();
        assertThat(agendador).isNotNull();
    }
}
