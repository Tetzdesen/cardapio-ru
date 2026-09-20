package io.github.tetzdesen.cardapioru.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.config.FiltroDeToken;
import io.github.tetzdesen.cardapioru.estado.ArquivoDeEstado;
import io.github.tetzdesen.cardapioru.notificacao.ClienteTelegram;
import io.github.tetzdesen.cardapioru.notificacao.Peca;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "api.token=segredo-do-workflow")
@ActiveProfiles("web")
@AutoConfigureMockMvc
class SegurancaTest {

    private static final String BEARER = "Bearer segredo-do-workflow";

    @MockitoBean
    private ColetorDaUfes coletor;

    @MockitoBean
    private ClienteTelegram telegram;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServicoCardapio cardapio;

    @Autowired
    private ArquivoDeEstado arquivo;

    @BeforeEach
    void preparar() throws Exception {
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
    void disparoSemCredencialResponde401ENaoMandaNada() throws Exception {
        mvc.perform(post("/api/v1/notificacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(telegram, never()).enviar(any());
    }

    @Test
    void credencialInvalidaResponde401EODeixaOEstadoIntacto() throws Exception {
        mvc.perform(post("/api/v1/notificacoes").header("Authorization", "Bearer errado")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(telegram, never()).enviar(any());
        assertThat(arquivo.ler()).isEmpty();
    }

    @Test
    void consultaContinuaAbertaSemCredencial() throws Exception {
        mvc.perform(get("/api/v1/cardapios/2026-09-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refeicoes.length()").value(3));
    }

    @Test
    void saudeContinuaAbertaSemCredencial() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void comCredencialValidaODisparoPassa() throws Exception {
        mvc.perform(post("/api/v1/notificacoes").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-09-11\",\"campus\":\"alegre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviadas.length()").value(3));
    }

    @Test
    void oTokenNaoVazaEmLogNemEmCorpoDeErro() throws Exception {
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        capturado.setContext(contexto);
        capturado.start();
        ch.qos.logback.classic.Logger raiz = contexto.getLogger("io.github.tetzdesen");
        raiz.addAppender(capturado);
        raiz.setLevel(Level.DEBUG);

        try {
            String resposta = mvc.perform(post("/api/v1/notificacoes")
                            .header("Authorization", "Bearer segredo-do-workflow-errado")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertThat(resposta).doesNotContain("segredo-do-workflow");
            assertThat(capturado.list)
                    .as("nenhuma linha de log pode conter o token, nem o apresentado")
                    .noneMatch(e -> e.getFormattedMessage().contains("segredo-do-workflow"));
        } finally {
            raiz.detachAppender(capturado);
        }
    }

    @Test
    void aAutoridadeDoFiltroEAQueOEndpointExige() {
        assertThat(FiltroDeToken.AUTORIDADE).isEqualTo("DISPARO");
    }
}
