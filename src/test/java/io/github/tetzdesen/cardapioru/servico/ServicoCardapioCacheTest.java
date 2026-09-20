package io.github.tetzdesen.cardapioru.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.coleta.ParserCardapio;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A consulta e aberta: rajada de chamadas aqui nao pode virar rajada no servidor
 * da UFES.
 */
class ServicoCardapioCacheTest {

    private final ColetorDaUfes coletor = Mockito.mock(ColetorDaUfes.class);

    private ServicoCardapio servico(Duration cache) {
        CardapioProperties props = new CardapioProperties("http://exemplo", "teste",
                Duration.ofSeconds(30), 3, Duration.ofSeconds(2), Duration.ofSeconds(60),
                Duration.ofSeconds(75), 30, ZoneOffset.ofHours(-3), cache, 400);
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.COMPLETA));
        given(coletor.urlDe(any())).willReturn("http://exemplo/cardapio");
        return new ServicoCardapio(coletor, new ParserCardapio(props), props);
    }

    @Test
    void duasConsultasIdenticasFazemUmaRequisicaoSo() {
        ServicoCardapio servico = servico(Duration.ofSeconds(60));
        LocalDate dia = LocalDate.of(2026, 9, 11);

        servico.consultar(dia, List.of(), null);
        servico.consultar(dia, List.of(), null);

        verify(coletor, times(1)).baixar(dia);
    }

    @Test
    void filtroDiferenteNaMesmaDataNaoRefazADescarga() {
        ServicoCardapio servico = servico(Duration.ofSeconds(60));
        LocalDate dia = LocalDate.of(2026, 9, 11);

        assertThat(servico.consultar(dia, List.of("Almoço"), null).blocos()).hasSize(1);
        assertThat(servico.consultar(dia, List.of("Jantar"), null).blocos()).hasSize(1);

        verify(coletor, times(1)).baixar(dia);
    }

    @Test
    void datasDiferentesSaoDescargasDiferentes() {
        ServicoCardapio servico = servico(Duration.ofSeconds(60));

        servico.consultar(LocalDate.of(2026, 9, 11), List.of(), null);
        servico.consultar(LocalDate.of(2026, 9, 12), List.of(), null);

        verify(coletor, times(2)).baixar(any());
    }

    @Test
    void cacheVencidoRefazADescarga() {
        ServicoCardapio servico = servico(Duration.ZERO);
        LocalDate dia = LocalDate.of(2026, 9, 11);

        servico.consultar(dia, List.of(), null);
        servico.consultar(dia, List.of(), null);

        verify(coletor, times(2)).baixar(dia);
    }
}
