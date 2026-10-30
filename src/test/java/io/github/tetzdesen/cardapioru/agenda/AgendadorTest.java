package io.github.tetzdesen.cardapioru.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgendadorTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 21);

    private final ServicoNotificacao notificacao = mock(ServicoNotificacao.class);
    private final ServicoCardapio cardapio = mock(ServicoCardapio.class);
    private final Agendador agendador = new Agendador(notificacao, cardapio, "");

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
    }

    @Test
    void semUrlPublicaNaoPinga() {
        assertThatCode(agendador::manterAcordado).doesNotThrowAnyException();
        assertThat(agendador).isNotNull();
    }
}
