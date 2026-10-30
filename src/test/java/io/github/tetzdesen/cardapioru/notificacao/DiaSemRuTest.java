package io.github.tetzdesen.cardapioru.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.estado.ArquivoDeEstado;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import io.github.tetzdesen.cardapioru.servico.ServicoHorario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** O aviso de dia sem RU, com o aviso real de horario especial de 23/10 a 26/10. */
@SpringBootTest
class DiaSemRuTest {

    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 24);
    private static final String INICIAL = "https://restaurante.alegre.ufes.br/";
    private static final String AVISO = INICIAL + "conteudo/horario-especial";

    // relogio na SEXTA: com o de parede, a poda descartaria os dias do aviso
    @TestBean
    private Clock relogio;

    static Clock relogio() {
        ZoneOffset fuso = ZoneOffset.ofHours(-3);
        return Clock.fixed(SEXTA.atStartOfDay(fuso).toInstant(), fuso);
    }

    @MockitoBean
    private ColetorDaUfes coletor;

    @MockitoBean
    private ClienteTelegram telegram;

    @MockitoBean
    private ClienteWhatsapp whatsapp;

    @Autowired
    private ServicoNotificacao servico;

    @Autowired
    private ServicoCardapio cardapio;

    @Autowired
    private ServicoHorario horarios;

    @Autowired
    private ArquivoDeEstado arquivo;

    @BeforeEach
    void limpar() throws Exception {
        java.nio.file.Files.deleteIfExists(arquivo.caminho());
        cardapio.limparCache();
        horarios.limparCache();
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.COMPLETA));
        given(coletor.urlDe(any())).willReturn("https://restaurante.alegre.ufes.br/cardapio");
        given(coletor.urlInicial()).willReturn(INICIAL);
        given(coletor.baixarPagina(INICIAL)).willReturn("<a href=\"/conteudo/horario-especial\">"
                + "Horário Especial de Funcionamento dos RUs</a>");
        given(coletor.baixarPagina(AVISO)).willReturn(Fixtures.ler(Fixtures.AVISO_HORARIO));
        given(telegram.configurado()).willReturn(true);
        given(telegram.nome()).willReturn(Canal.TELEGRAM);
        given(telegram.enviar(any())).willAnswer(inv -> {
            List<Peca> pecas = inv.getArgument(0);
            return pecas.stream().map(Peca::refeicao).filter(r -> r != null).toList();
        });
    }

    @Test
    void avisaNaVesperaJuntoComOCardapio() {
        DisparoResposta r = servico.disparar(SEXTA, List.of("Almoço"), "alegre", true);

        assertThat(r.enviadas()).containsExactly("Almoço");
        assertThat(r.semRu()).containsExactly(SABADO);
        verify(telegram).enviar(argThat(pecas -> pecas.get(0).texto()
                .startsWith("🚫 <b>Amanhã não vai ter RU</b> — sábado, 24/10")));
    }

    @Test
    void naoRepeteOAvisoNoMesmoDia() {
        servico.disparar(SEXTA, List.of("Almoço"), "alegre", true);

        DisparoResposta jantar = servico.disparar(SEXTA, List.of("Jantar"), "alegre", true);

        assertThat(jantar.semRu()).isEmpty();
    }

    @Test
    void lembraNoProprioDiaUmaVezSo() {
        servico.disparar(SEXTA, List.of("Almoço"), "alegre", true);

        DisparoResposta noDia = servico.disparar(SABADO, List.of("Almoço"), "alegre", true);
        DisparoResposta depois = servico.disparar(SABADO, List.of("Almoço"), "alegre", true);

        assertThat(noDia.semRu()).containsExactly(SABADO);
        assertThat(depois.semRu()).isEmpty();
        verify(telegram).enviar(argThat(pecas -> pecas.stream()
                .anyMatch(p -> p.texto().startsWith("🚫 <b>Hoje não tem RU</b>"))));
    }

    @Test
    void diaSemRuNaoMandaOCardapioQueNaoVaiSerServido() {
        DisparoResposta r = servico.disparar(SABADO, List.of("Almoço"), "alegre", true);

        assertThat(r.enviadas()).isEmpty();
        assertThat(r.omitidas()).containsExactly("Almoço");
        verify(telegram).enviar(argThat(pecas -> pecas.size() == 1
                && pecas.get(0).texto().startsWith("🚫 <b>Hoje não tem RU</b>")));
        assertThat(arquivo.ler()).doesNotContainKey(ArquivoDeEstado.chave(SABADO, "almoco"));
    }

    @Test
    void refeicaoFechadaNumDiaComRuViraAvisoCurto() {
        // segunda 26/10: jantar sem atendimento, almoco reduzido
        LocalDate segunda = LocalDate.of(2026, 10, 26);

        DisparoResposta r = servico.disparar(segunda, List.of("Jantar"), "alegre", true);

        assertThat(r.enviadas()).isEmpty();
        verify(telegram).enviar(argThat(pecas -> pecas.size() == 1 && pecas.get(0).texto()
                .equals("🚫 <b>Sem jantar hoje</b>\n📍 <i>Alegre</i>")));
    }

    @Test
    void diaSemRuSemCardapioNaoAvisaSemCardapio() {
        given(coletor.baixar(any())).willReturn(Fixtures.ler(Fixtures.VAZIA));
        cardapio.limparCache();

        servico.disparar(SABADO, List.of(), "alegre", false);

        verify(telegram, times(1)).enviar(argThat(pecas -> pecas.stream()
                .noneMatch(p -> p.texto().equals(Formatador.semCardapio()))));
    }

    @Test
    void horarioDoAnuncioSoTemAUnidadeDoFiltro() {
        servico.disparar(SEXTA, List.of("Jantar"), "alegre", true);

        verify(telegram).enviar(argThat(pecas -> pecas.stream().anyMatch(p ->
                p.texto().contains("⚠️ Alegre: 17h15 às 18h30 <b>(horário reduzido)</b>")
                        && !p.texto().contains("Jerônimo Monteiro:"))));
    }

    @Test
    void vigiaNaoAvisaDiaSemRu() {
        DisparoResposta r = servico.disparar(SEXTA, List.of(), "alegre", true, true);

        assertThat(r.semRu()).isEmpty();
        verify(telegram, never()).enviar(any());
    }
}
