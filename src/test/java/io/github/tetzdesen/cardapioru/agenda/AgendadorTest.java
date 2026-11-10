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

    private static final String ANUNCIO = "cardapio do dia";

    private void siteFora() {
        given(notificacao.disparar(any(), any(), anyString(), anyBoolean())).willThrow(
                new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "site fora"));
    }

    private void siteVolta() {
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
    void anuncioPedeODiaInteiroEmSilencioSeVazio() {
        agendador.anunciar();

        verify(notificacao).disparar(HOJE, List.of(), "alegre", true);
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
        siteFora();

        assertThatCode(agendador::anunciar).doesNotThrowAnyException();
        verify(alerta).falhou(eq(ANUNCIO), any(FalhaDeOrigem.class));
    }

    @Test
    void anuncioQueFalhouETentadoDeNovoAteSair() {
        agora = LocalTime.of(5, 40);
        siteFora();
        agendador.anunciar();

        agora = LocalTime.of(5, 50);
        agendador.recuperar(); // o site continua fora
        siteVolta();
        agora = LocalTime.of(6, 0);
        agendador.recuperar();
        agora = LocalTime.of(6, 10);
        agendador.recuperar(); // ja saiu: nao tenta mais

        verify(notificacao, times(3)).disparar(HOJE, List.of(), "alegre", true);
        verify(alerta).recuperou(ANUNCIO);
        verify(alerta, never()).desistiu(anyString());
    }

    @Test
    void novaTentativaDeixaDeForaARefeicaoQueJaPassouDoPrazo() {
        siteFora();
        agendador.anunciar();
        siteVolta();

        agora = LocalTime.of(10, 30); // desjejum ja passou
        agendador.recuperar();

        verify(notificacao).disparar(HOJE, List.of("almoco", "jantar"), "alegre", true);
    }

    @Test
    void depoisDoUltimoPrazoDesisteEAvisa() {
        siteFora();
        agendador.anunciar();

        agora = LocalTime.of(19, 0);
        agendador.recuperar();
        agendador.recuperar();

        verify(notificacao, times(1)).disparar(any(), any(), anyString(), anyBoolean());
        verify(alerta, times(1)).desistiu(ANUNCIO);
    }

    @Test
    void anuncioQueSaiuNaoETentadoDeNovo() {
        agendador.anunciar();
        agora = LocalTime.of(10, 40);

        agendador.recuperar();

        verify(notificacao, times(1)).disparar(any(), any(), anyString(), anyBoolean());
        verifyNoInteractions(alerta);
    }

    @Test
    void falhaDeOntemNaoETentadaHoje() {
        siteFora();
        given(cardapio.hoje()).willReturn(HOJE.minusDays(1));
        agendador.anunciar();

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
