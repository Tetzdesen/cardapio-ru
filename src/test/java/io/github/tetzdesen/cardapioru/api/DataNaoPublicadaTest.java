package io.github.tetzdesen.cardapioru.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.coleta.ParserCardapio;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comportamento <b>aceito</b>, nao desejavel: para uma data que o site nunca
 * publicou, o Plone devolve a moldura institucional -- texto acima do limiar, sem
 * cabecalho de refeicao, sem dizer que nao ha cardapio. O parser nao distingue
 * isso de um site reformado e responde 502.
 *
 * <p>Este teste existe para que a proxima mudanca no parser nao altere isso sem
 * querer, e para que a proxima pessoa nao trate como defeito recem-introduzido.
 * Corrigir de verdade exige heuristica nova e divergencia deliberada do
 * ru_bot.py, e fica para um change proprio.
 */
@SpringBootTest
@ActiveProfiles("web")
@AutoConfigureMockMvc
class DataNaoPublicadaTest {

    @MockitoBean
    private ColetorDaUfes coletor;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServicoCardapio cardapio;

    @Autowired
    private ParserCardapio parser;

    @BeforeEach
    void limpar() {
        cardapio.limparCache();
        given(coletor.urlDe(any()))
                .willReturn("https://restaurante.alegre.ufes.br/cardapio/2026-09-20");
    }

    @Test
    void consultaAUmaDataDeFimDeSemanaResponde502() throws Exception {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.NAO_PUBLICADA));

        mvc.perform(get("/api/v1/cardapios/2026-09-20"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.causa").value("ESTRUTURA_NAO_RECONHECIDA"));
    }

    @Test
    void comportamentoEIdenticoAoDoRuBotParaAMesmaPagina() throws Exception {
        JsonNode referencia = new ObjectMapper()
                .readTree(getClass().getResourceAsStream("/referencia-ru-bot.json"))
                .get(Fixtures.NAO_PUBLICADA);

        boolean reconhecidaEmJava =
                parser.analisar(Fixtures.ler(Fixtures.NAO_PUBLICADA)).reconhecida();

        assertThat(reconhecidaEmJava)
                .as("o ru_bot.py tambem nao reconhece esta pagina (sai com codigo 4)")
                .isEqualTo(referencia.get("reconhecida").asBoolean())
                .isFalse();
    }

    @Test
    void paginaQueDeclaraAusenciaContinuaRespondendo200() throws Exception {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.VAZIA));

        mvc.perform(get("/api/v1/cardapios/2026-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicado").value(false))
                .andExpect(jsonPath("$.refeicoes.length()").value(0));
    }
}
