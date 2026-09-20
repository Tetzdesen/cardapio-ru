package io.github.tetzdesen.cardapioru.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Uma linha da tabela de codigos de saida por teste. O log do Actions mostra o
 * codigo antes de qualquer corpo -- e o codigo que faz o job ficar vermelho na
 * hora certa, e "nada a enviar" nao pode ser uma delas.
 */
class DisparoRunnerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 16);

    private final ServicoNotificacao notificacao = Mockito.mock(ServicoNotificacao.class);
    private final ServicoCardapio cardapio = Mockito.mock(ServicoCardapio.class);
    private final DisparoRunner runner = new DisparoRunner(notificacao, cardapio);

    @BeforeEach
    void preparar() {
        given(cardapio.hoje()).willReturn(HOJE);
    }

    private static DisparoResposta nada() {
        return new DisparoResposta(HOJE, true, List.of(), List.of(), List.of("Almoço"),
                List.of());
    }

    @Test
    void semArgumentosNaoDisparaNada() {
        assertThat(runner.executar(new String[0])).isEqualTo(Saida.OK);
        verifyNoInteractions(notificacao);
    }

    @Test
    void argumentoDeOutroSistemaNaoContaComoDisparo() {
        // e assim que 'java -jar app.jar --spring.profiles.active=web' sobe o
        // servidor sem mandar nada ao grupo
        assertThat(runner.executar(new String[] {"--spring.profiles.active=web"}))
                .isEqualTo(Saida.OK);
        verifyNoInteractions(notificacao);
    }

    @Test
    void enviarDelegaAoMesmoServicoQueOControllerUsa() {
        given(notificacao.disparar(any(), any(), any(), eq(true))).willReturn(nada());

        int codigo = runner.executar(new String[] {
            "--enviar", "--refeicoes", "almoco,jantar", "--campus", "alegre",
            "--vazio-silencioso"});

        assertThat(codigo).isEqualTo(Saida.OK);
        verify(notificacao).disparar(HOJE, List.of("almoco", "jantar"), "alegre", true);
    }

    @Test
    void dataInformadaSubstituiHoje() {
        given(notificacao.disparar(any(), any(), any(), anyBoolean())).willReturn(nada());

        runner.executar(new String[] {"--enviar", "--data=2026-09-11"});

        verify(notificacao).disparar(eq(LocalDate.of(2026, 9, 11)), any(), any(), eq(false));
    }

    @Test
    void nadaAEnviarSaiComSucesso() {
        given(notificacao.disparar(any(), any(), any(), eq(true))).willReturn(nada());

        assertThat(runner.executar(new String[] {"--enviar", "--vazio-silencioso"}))
                .isEqualTo(Saida.OK);
    }

    @Test
    void diaSemCardapioPublicadoSaiComSucesso() {
        given(notificacao.disparar(any(), any(), any(), eq(true))).willReturn(
                new DisparoResposta(HOJE, false, List.of(), List.of(), List.of(), List.of()));

        assertThat(runner.executar(new String[] {"--enviar", "--vazio-silencioso"}))
                .isEqualTo(Saida.OK);
    }

    @Test
    void telegramRecusaSaiCom3() {
        willThrow(new FalhaDeOrigem(FalhaDeOrigem.Causa.TELEGRAM_RECUSOU, "400"))
                .given(notificacao).disparar(any(), any(), any(), anyBoolean());

        assertThat(runner.executar(new String[] {"--enviar"})).isEqualTo(Saida.TELEGRAM);
    }

    @Test
    void estruturaNaoReconhecidaSaiCom4() {
        willThrow(new FalhaDeOrigem(FalhaDeOrigem.Causa.ESTRUTURA_NAO_RECONHECIDA, "reformou"))
                .given(notificacao).disparar(any(), any(), any(), anyBoolean());

        assertThat(runner.executar(new String[] {"--enviar"})).isEqualTo(Saida.ESTRUTURA);
    }

    @Test
    void origemInacessivelSaiCom5() {
        willThrow(new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "sem resposta"))
                .given(notificacao).disparar(any(), any(), any(), anyBoolean());

        assertThat(runner.executar(new String[] {"--enviar"})).isEqualTo(Saida.ORIGEM);
    }

    @Test
    void estadoQueNaoPodeSerGravadoSaiCom6() {
        willThrow(new EstadoIndisponivel("sem permissao", null))
                .given(notificacao).disparar(any(), any(), any(), anyBoolean());

        assertThat(runner.executar(new String[] {"--enviar"})).isEqualTo(Saida.ESTADO);
    }

    @Test
    void refeicaoDesconhecidaSaiCom2() {
        assertThat(runner.executar(new String[] {"--enviar", "--refeicoes", "brunch"}))
                .isEqualTo(Saida.USO);
        verifyNoInteractions(notificacao);
    }

    @Test
    void dataMalFormadaSaiCom2() {
        assertThat(runner.executar(new String[] {"--enviar", "--data", "16/09/2026"}))
                .isEqualTo(Saida.USO);
        verifyNoInteractions(notificacao);
    }

    @Test
    void semEscolherEntreEnviarEImprimirSaiCom2() {
        assertThat(runner.executar(new String[] {"--refeicoes", "almoco"}))
                .isEqualTo(Saida.USO);
        assertThat(runner.executar(new String[] {"--enviar", "--print"}))
                .isEqualTo(Saida.USO);
        verifyNoInteractions(notificacao);
    }

    @Test
    void opcaoComValorFaltandoSaiCom2() {
        assertThat(runner.executar(new String[] {"--enviar", "--data"})).isEqualTo(Saida.USO);
    }

    @Test
    void printImprimeSemEnviarESemTocarNoEstado() {
        BlocoRefeicao almoco = new BlocoRefeicao("Almoço", "Alegre",
                "quarta-feira, 16 de Setembro de 2026",
                List.of(new Secao("Entrada", List.of("Alface & Rúcula"))));
        given(cardapio.consultar(any(), any(), any())).willReturn(
                new ServicoCardapio.Resultado(List.of(almoco), true, List.of(),
                        "https://restaurante.alegre.ufes.br/cardapio"));

        PrintStream original = System.out;
        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturado, true, StandardCharsets.UTF_8));
        int codigo;
        try {
            codigo = runner.executar(new String[] {"--print"});
        } finally {
            System.setOut(original);
        }

        String saida = capturado.toString(StandardCharsets.UTF_8);
        assertThat(codigo).isEqualTo(Saida.OK);
        assertThat(saida)
                .contains("Almoço")
                .contains("Alface & Rúcula")
                .doesNotContain("<b>")
                .doesNotContain("&amp;");
        verify(notificacao, never()).disparar(any(), any(), any(), anyBoolean());
    }
}
