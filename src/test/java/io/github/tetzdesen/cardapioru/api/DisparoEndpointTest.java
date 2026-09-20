package io.github.tetzdesen.cardapioru.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.notificacao.ClienteTelegram;
import io.github.tetzdesen.cardapioru.notificacao.Peca;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** O relato do disparo e o que o workflow le; o status e o que o faz ficar vermelho. */
@SpringBootTest(properties = "api.token=segredo")
@ActiveProfiles("web")
@AutoConfigureMockMvc
class DisparoEndpointTest {

    private static final String BEARER = "Bearer segredo";

    @MockitoBean
    private ColetorDaUfes coletor;

    @MockitoBean
    private ClienteTelegram telegram;

    @MockitoBean
    private ServicoEstado estado;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServicoCardapio cardapio;

    @BeforeEach
    void preparar() {
        cardapio.limparCache();
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.COMPLETA));
        given(coletor.urlDe(any())).willReturn("https://restaurante.alegre.ufes.br/cardapio");
        given(telegram.enviar(any())).willAnswer(inv -> {
            List<Peca> pecas = inv.getArgument(0);
            return pecas.stream().map(Peca::refeicao).filter(r -> r != null).toList();
        });
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder disparo(
            String corpo) {
        return post("/api/v1/notificacoes").header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    void nadaMudouResponde200ComEnviadasVaziaEOmitidasListadas() throws Exception {
        given(estado.classificar(any(), any())).willAnswer(inv -> {
            List<io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao> blocos =
                    inv.getArgument(1);
            return blocos.stream()
                    .map(b -> new ServicoEstado.Classificacao(b,
                            b.refeicao().toLowerCase(), "assinatura",
                            ServicoEstado.Situacao.INALTERADA))
                    .toList();
        });

        mvc.perform(disparo("{\"data\":\"2026-09-11\",\"campus\":\"alegre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviadas.length()").value(0))
                .andExpect(jsonPath("$.omitidas.length()").value(3));

        verify(telegram, never()).enviar(any());
    }

    @Test
    void telegramRecusaResponde502ComACausaIdentificada() throws Exception {
        given(estado.classificar(any(), any())).willAnswer(inv -> {
            List<io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao> blocos =
                    inv.getArgument(1);
            return blocos.stream()
                    .map(b -> new ServicoEstado.Classificacao(b, b.refeicao().toLowerCase(),
                            "assinatura", ServicoEstado.Situacao.INEDITA))
                    .toList();
        });
        willThrow(new ClienteTelegram.FalhaDeEnvio("Telegram respondeu 400: Bad Request",
                List.of(), null)).given(telegram).enviar(any());

        mvc.perform(disparo("{\"data\":\"2026-09-11\",\"campus\":\"alegre\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.causa").value("TELEGRAM_RECUSOU"));
    }

    @Test
    void estadoQueNaoPodeSerGravadoResponde503() throws Exception {
        given(estado.classificar(any(), any())).willAnswer(inv -> {
            List<io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao> blocos =
                    inv.getArgument(1);
            return blocos.stream()
                    .map(b -> new ServicoEstado.Classificacao(b, b.refeicao().toLowerCase(),
                            "assinatura", ServicoEstado.Situacao.INEDITA))
                    .toList();
        });
        willThrow(new EstadoIndisponivel("nao foi possivel gravar o estado", null))
                .given(estado).registrar(any(), any());

        // a mensagem ja chegou ao grupo: o 503 e o que deixa a inconsistencia
        // visivel a quem chamou, em vez de fazer o disparo seguinte ficar mudo
        mvc.perform(disparo("{\"data\":\"2026-09-11\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.causa").value("ESTADO_INDISPONIVEL"));
    }

    @Test
    void refeicaoDesconhecidaNoDisparoResponde400() throws Exception {
        mvc.perform(disparo("{\"refeicoes\":\"brunch\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parametro").value("refeicoes"));

        verify(telegram, never()).enviar(any());
    }

    @Test
    void estruturaNaoReconhecidaResponde502ENaoMandaNada() throws Exception {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.QUEBRADA));

        mvc.perform(disparo("{\"data\":\"2026-09-11\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.causa").value("ESTRUTURA_NAO_RECONHECIDA"));

        verify(telegram, never()).enviar(any());
    }
}
