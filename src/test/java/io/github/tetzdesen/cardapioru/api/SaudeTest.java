package io.github.tetzdesen.cardapioru.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A saude reflete a aplicacao, nao a origem. O site da UFES fora do ar tem que
 * aparecer na consulta do cardapio -- se aparecesse aqui, o host reiniciaria a
 * aplicacao por um problema que nao e dela.
 */
@SpringBootTest
@ActiveProfiles("web")
@AutoConfigureMockMvc
class SaudeTest {

    @MockitoBean
    private ColetorDaUfes coletor;

    @Autowired
    private MockMvc mvc;

    @Test
    void aplicacaoNoArResponde200() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void siteDaUfesForaDoArNaoDerrubaASaude() throws Exception {
        willThrow(new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "fora do ar"))
                .given(coletor).baixar(any());

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        verify(coletor, never()).baixar(any());
    }
}
