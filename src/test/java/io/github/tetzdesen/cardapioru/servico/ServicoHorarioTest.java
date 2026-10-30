package io.github.tetzdesen.cardapioru.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ServicoHorarioTest {

    private static final String INICIAL = "https://restaurante.alegre.ufes.br/";
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);

    private final ColetorDaUfes coletor = mock(ColetorDaUfes.class);
    private final ServicoHorario servico = new ServicoHorario(coletor, new CardapioProperties(
            "http://x", "t", Duration.ofSeconds(1), 1, Duration.ZERO, Duration.ZERO,
            Duration.ofSeconds(1), 30, ZoneOffset.ofHours(-3), Duration.ZERO, 400));

    @Test
    void siteForaDoArMantemOsAvisosJaLidos() {
        given(coletor.urlInicial()).willReturn(INICIAL);
        given(coletor.baixarPagina(INICIAL)).willReturn(
                "<a href=\"/conteudo/aviso\">Horário Especial de Funcionamento</a>");
        given(coletor.baixarPagina(INICIAL + "conteudo/aviso"))
                .willReturn(Fixtures.ler(Fixtures.AVISO_HORARIO));
        assertThat(servico.diasSemRu(SEXTA, "alegre")).hasSize(1);

        servico.limparCache();
        given(coletor.baixarPagina(anyString())).willThrow(
                new FalhaDeOrigem(FalhaDeOrigem.Causa.ORIGEM_INACESSIVEL, "site fora"));

        assertThat(servico.diasSemRu(SEXTA, "alegre"))
                .as("o sabado fechado continua conhecido com o site fora do ar")
                .hasSize(1);
    }
}
