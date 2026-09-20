package io.github.tetzdesen.cardapioru.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.estado.ArquivoDeEstado;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DisparoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 11);

    @MockitoBean
    private ColetorDaUfes coletor;

    @MockitoBean
    private ClienteTelegram telegram;

    @Autowired
    private ServicoNotificacao servico;

    @Autowired
    private ServicoCardapio cardapio;

    @Autowired
    private ArquivoDeEstado arquivo;

    @BeforeEach
    void limpar() throws Exception {
        java.nio.file.Files.deleteIfExists(arquivo.caminho());
        cardapio.limparCache();
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.COMPLETA));
        given(coletor.urlDe(any())).willReturn("https://restaurante.alegre.ufes.br/cardapio");
        given(telegram.enviar(any())).willAnswer(inv -> {
            List<Peca> pecas = inv.getArgument(0);
            return pecas.stream().map(Peca::refeicao).filter(r -> r != null).toList();
        });
    }

    @Test
    void estadoVazioTrataTudoComoIneditoESemAvisoDeMudanca() {
        DisparoResposta r = servico.disparar(DIA, List.of(), "alegre", true);

        assertThat(r.enviadas()).containsExactly("Desjejum", "Almoço", "Jantar");
        assertThat(r.alteradas()).isEmpty();
        assertThat(r.omitidas()).isEmpty();

        verify(telegram).enviar(argQueNaoContem(Formatador.AVISO_MUDANCA));
    }

    @Test
    void disparoSeguinteSemAlteracaoNaoEnviaNada() {
        servico.disparar(DIA, List.of(), "alegre", true);

        DisparoResposta segundo = servico.disparar(DIA, List.of(), "alegre", true);

        assertThat(segundo.enviadas()).isEmpty();
        assertThat(segundo.omitidas()).containsExactly("Desjejum", "Almoço", "Jantar");
        verify(telegram).enviar(any());
    }

    @Test
    void reconferenciaPedindoMenosRefeicoesReconheceOQueAManhaEnviou() {
        servico.disparar(DIA, List.of(), "alegre", true);

        DisparoResposta reconferencia =
                servico.disparar(DIA, List.of("Almoço", "Jantar"), "alegre", true);

        assertThat(reconferencia.enviadas()).isEmpty();
        assertThat(reconferencia.omitidas()).containsExactly("Almoço", "Jantar");
    }

    @Test
    void soARefeicaoQueMudouEReenviadaComAviso() {
        servico.disparar(DIA, List.of(), "alegre", true);

        // o almoço mudou de conteudo; o resto continua igual
        var estado = arquivo.ler();
        estado.put(ArquivoDeEstado.chave(DIA, "almoco"), "assinatura-de-antes");
        arquivo.gravar(estado);
        cardapio.limparCache();

        DisparoResposta r = servico.disparar(DIA, List.of(), "alegre", true);

        assertThat(r.enviadas()).containsExactly("Almoço");
        assertThat(r.alteradas()).containsExactly("Almoço");
        assertThat(r.omitidas()).containsExactly("Desjejum", "Jantar");
        verify(telegram).enviar(argQueContem(Formatador.AVISO_MUDANCA));
    }

    @Test
    void telegramRecusaEOEstadoNaoFicaGravado() {
        willThrow(new ClienteTelegram.FalhaDeEnvio("Telegram respondeu 400", List.of(), null))
                .given(telegram).enviar(any());

        assertThatThrownBy(() -> servico.disparar(DIA, List.of(), "alegre", true))
                .isInstanceOf(ClienteTelegram.FalhaDeEnvio.class);

        assertThat(arquivo.ler())
                .as("nada pode ficar gravado quando o envio falhou")
                .isEmpty();
    }

    @Test
    void depoisDeUmaFalhaODisparoSeguinteTentaDeNovo() {
        willThrow(new ClienteTelegram.FalhaDeEnvio("falhou", List.of(), null))
                .given(telegram).enviar(any());
        assertThatThrownBy(() -> servico.disparar(DIA, List.of(), "alegre", true))
                .isInstanceOf(ClienteTelegram.FalhaDeEnvio.class);

        // willReturn().given() em vez de given(mock.metodo()): a segunda forma
        // chamaria enviar(), que neste ponto ainda esta stubado para lancar
        org.mockito.BDDMockito.willReturn(List.of("desjejum", "almoco", "jantar"))
                .given(telegram).enviar(any());
        cardapio.limparCache();

        DisparoResposta r = servico.disparar(DIA, List.of(), "alegre", true);

        assertThat(r.enviadas()).containsExactly("Desjejum", "Almoço", "Jantar");
        assertThat(arquivo.ler()).hasSize(3);
    }

    @Test
    void diaSemCardapioComSilencioNaoFalaNada() {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.VAZIA));
        cardapio.limparCache();

        DisparoResposta r = servico.disparar(DIA, List.of(), "alegre", true);

        assertThat(r.publicado()).isFalse();
        assertThat(r.enviadas()).isEmpty();
        verify(telegram, never()).enviar(any());
    }

    @Test
    void diaSemCardapioSemSilencioAvisaQueNaoTem() {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.VAZIA));
        cardapio.limparCache();

        servico.disparar(DIA, List.of(), "alegre", false);

        verify(telegram).enviar(argQueContem("Sem cardápio publicado"));
    }

    @Test
    void envioParcialGravaSoAsRefeicoesConfirmadas() {
        // a primeira mensagem foi aceita, a segunda nao: o que chegou ao grupo
        // fica registrado, para nao voltar la no disparo seguinte
        willThrow(new ClienteTelegram.FalhaDeEnvio("Telegram respondeu 400",
                List.of("desjejum", "almoco"), null)).given(telegram).enviar(any());

        assertThatThrownBy(() -> servico.disparar(DIA, List.of(), "alegre", true))
                .isInstanceOf(ClienteTelegram.FalhaDeEnvio.class);

        assertThat(arquivo.ler()).containsOnlyKeys(
                ArquivoDeEstado.chave(DIA, "desjejum"),
                ArquivoDeEstado.chave(DIA, "almoco"));
    }

    @Test
    void disparoSeguinteAposFalhaParcialSoMandaOQueFaltou() {
        willThrow(new ClienteTelegram.FalhaDeEnvio("Telegram respondeu 400",
                List.of("desjejum", "almoco"), null)).given(telegram).enviar(any());
        assertThatThrownBy(() -> servico.disparar(DIA, List.of(), "alegre", true))
                .isInstanceOf(ClienteTelegram.FalhaDeEnvio.class);

        org.mockito.BDDMockito.willReturn(List.of("jantar")).given(telegram).enviar(any());
        cardapio.limparCache();

        DisparoResposta r = servico.disparar(DIA, List.of(), "alegre", true);

        assertThat(r.enviadas()).containsExactly("Jantar");
        assertThat(r.omitidas()).containsExactly("Desjejum", "Almoço");
    }

    private static List<Peca> argQueContem(String trecho) {
        return org.mockito.ArgumentMatchers.argThat(pecas ->
                pecas.stream().anyMatch(p -> p.texto().contains(trecho)));
    }

    private static List<Peca> argQueNaoContem(String trecho) {
        return org.mockito.ArgumentMatchers.argThat(pecas ->
                pecas.stream().noneMatch(p -> p.texto().contains(trecho)));
    }
}
