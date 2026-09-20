package io.github.tetzdesen.cardapioru.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.time.LocalDate;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** A tabela de status do design.md, linha a linha. */
@SpringBootTest
@ActiveProfiles("web")
class CardapioControllerTest {

    @MockitoBean
    private ColetorDaUfes coletor;

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ServicoCardapio servico;

    private MockMvc mvc;

    @BeforeEach
    void cacheLimpo() {
        // o cache e do bean, que vive o contexto inteiro: sem limpar, um teste
        // serve a pagina que o anterior guardou
        servico.limparCache();
    }

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        }
        return mvc;
    }

    private void paginaServida(String fixture) {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(fixture));
        given(coletor.urlDe(any())).willReturn("https://restaurante.alegre.ufes.br/cardapio");
    }

    @Test
    void dataComCardapioPublicadoResponde200ComAsRefeicoes() throws Exception {
        paginaServida(Fixtures.COMPLETA);

        mvc().perform(get("/api/v1/cardapios/2026-09-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2026-09-11"))
                .andExpect(jsonPath("$.publicado").value(true))
                .andExpect(jsonPath("$.refeicoes.length()").value(3))
                .andExpect(jsonPath("$.origem").value(
                        "https://restaurante.alegre.ufes.br/cardapio"));
    }

    @Test
    void atalhoParaHojeEAAusenciaDaData() throws Exception {
        paginaServida(Fixtures.COMPLETA);

        mvc().perform(get("/api/v1/cardapios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(LocalDate.now(
                        java.time.ZoneOffset.ofHours(-3)).toString()))
                .andExpect(jsonPath("$.refeicoes.length()").value(3));
    }

    @Test
    void filtroPorRefeicaoECampus() throws Exception {
        paginaServida(Fixtures.COMPLETA);

        mvc().perform(get("/api/v1/cardapios/2026-09-11")
                        .param("refeicoes", "almoço").param("campus", "alegre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refeicoes.length()").value(1))
                .andExpect(jsonPath("$.refeicoes[0].refeicao").value("Almoço"));
    }

    @Test
    void secoesSaemNaOrdemDaPaginaComRotuloEItens() throws Exception {
        paginaServida(Fixtures.COMPLETA);

        mvc().perform(get("/api/v1/cardapios/2026-09-11").param("refeicoes", "almoço"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refeicoes[0].secoes[0].rotulo").value("Entrada"))
                .andExpect(jsonPath("$.refeicoes[0].secoes[1].rotulo").value("Prato Proteico"))
                .andExpect(jsonPath("$.refeicoes[0].secoes[0].itens").isArray())
                .andExpect(jsonPath("$.refeicoes[0].dataPorExtenso")
                        .value("sexta-feira, 11 de Setembro de 2026"));
    }

    @Test
    void diaSemCardapioResponde200ComListaVaziaEPublicadoFalso() throws Exception {
        paginaServida(Fixtures.VAZIA);

        mvc().perform(get("/api/v1/cardapios/2026-09-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicado").value(false))
                .andExpect(jsonPath("$.refeicoes.length()").value(0));
    }

    @Test
    void filtroSemCorrespondenciaResponde200ComListaVazia() throws Exception {
        paginaServida(Fixtures.COMPLETA);

        mvc().perform(get("/api/v1/cardapios/2026-09-11").param("campus", "vitoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicado").value(true))
                .andExpect(jsonPath("$.refeicoes.length()").value(0));
    }

    @Test
    void estruturaDaPaginaMudouResponde502ComCausaIdentificada() throws Exception {
        paginaServida(Fixtures.QUEBRADA);

        mvc().perform(get("/api/v1/cardapios/2026-09-11"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.causa").value("ESTRUTURA_NAO_RECONHECIDA"))
                .andExpect(jsonPath("$.mensagem").value(
                        org.hamcrest.Matchers.containsString("estrutura do site")));
    }

    @Test
    void origemForaDoArResponde504() throws Exception {
        given(coletor.urlDe(any())).willReturn("https://restaurante.alegre.ufes.br/cardapio");
        willThrow(new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL,
                "GET falhou em 3 tentativa(s)")).given(coletor).baixar(any());

        mvc().perform(get("/api/v1/cardapios/2026-09-11"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.causa").value("ORIGEM_INACESSIVEL"));
    }

    @Test
    void dataMalFormatadaResponde400NomeandoOParametro() throws Exception {
        mvc().perform(get("/api/v1/cardapios/11-09-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parametro").value("data"))
                .andExpect(jsonPath("$.aceitos").value("AAAA-MM-DD"));
    }

    @Test
    void refeicaoDesconhecidaResponde400ListandoAsAceitas() throws Exception {
        mvc().perform(get("/api/v1/cardapios/2026-09-11").param("refeicoes", "brunch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parametro").value("refeicoes"))
                .andExpect(jsonPath("$.aceitos").isArray())
                .andExpect(jsonPath("$.aceitos").value(
                        org.hamcrest.Matchers.hasItem("Jantar")));
    }

    @Test
    void secaoComRotuloDesconhecidoApareceNaRespostaComAviso() throws Exception {
        String html = Fixtures.ler(Fixtures.COMPLETA)
                .replace("<strong>Suco</strong>", "<strong>Molho</strong>");
        given(coletor.baixar(any())).willReturn(html);
        given(coletor.urlDe(any())).willReturn("https://restaurante.alegre.ufes.br/cardapio");

        mvc().perform(get("/api/v1/cardapios/2026-09-11").param("refeicoes", "almoço"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refeicoes[0].secoes[*].rotulo")
                        .value(org.hamcrest.Matchers.hasItem("Molho")))
                .andExpect(jsonPath("$.avisos").value(
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Molho"))));
    }
}
