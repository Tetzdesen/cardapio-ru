package io.github.tetzdesen.cardapioru.coleta;

import static io.github.tetzdesen.cardapioru.coleta.QuadroDeHorarios.ALEGRE;
import static io.github.tetzdesen.cardapioru.coleta.QuadroDeHorarios.JERONIMO;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.tetzdesen.cardapioru.Fixtures;
import io.github.tetzdesen.cardapioru.coleta.QuadroDeHorarios.Excecao;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HorariosTest {

    private static final String AMBOS = "Alegre e Jerônimo Monteiro";
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 24);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 26);

    private static List<Excecao> doAviso() {
        return LeitorDeAvisos.excecoes(Fixtures.ler(Fixtures.AVISO_HORARIO), 2026);
    }

    @Test
    void diaDeSemanaSemAvisoTemOHorarioRegularDasDuasUnidades() {
        assertThat(QuadroDeHorarios.de(LocalDate.of(2026, 10, 29), "Almoço", AMBOS, List.of()))
                .containsExactly(new Horario(ALEGRE, "11h00 às 13h30", false),
                        new Horario(JERONIMO, "11h30 às 13h00", false));
    }

    @Test
    void sabadoSoTemAlmocoEmHorarioReduzido() {
        LocalDate sabado = LocalDate.of(2026, 10, 31);
        assertThat(QuadroDeHorarios.de(sabado, "Almoço", AMBOS, List.of()))
                .containsExactly(new Horario(ALEGRE, "11h00 às 12h30", true),
                        new Horario(JERONIMO, "11h30 às 12h30", true));
        assertThat(QuadroDeHorarios.de(sabado, "Jantar", AMBOS, List.of())).isEmpty();
    }

    @Test
    void campusDeUmaUnidadeSoMostraEla() {
        assertThat(QuadroDeHorarios.de(LocalDate.of(2026, 10, 29), "Desjejum", "Alegre",
                List.of())).containsExactly(new Horario(ALEGRE, "06h45 às 08h00", false));
    }

    @Test
    void avisoDaHorarioReduzidoPorUnidade() {
        assertThat(QuadroDeHorarios.de(SEXTA, "Jantar", AMBOS, doAviso()))
                .containsExactly(new Horario(ALEGRE, "17h15 às 18h30", true),
                        new Horario(JERONIMO, "17h15 às 18h00", true));
    }

    @Test
    void funcionamentoNormalNoAvisoMantemORegular() {
        assertThat(QuadroDeHorarios.de(SEXTA, "Desjejum", AMBOS, doAviso()))
                .containsExactly(new Horario(ALEGRE, "06h45 às 08h00", false),
                        new Horario(JERONIMO, "07h00 às 08h00", false));
    }

    @Test
    void diaFechadoESemAtendimentoViramSemAtendimento() {
        assertThat(QuadroDeHorarios.de(SABADO, "Almoço", AMBOS, doAviso()))
                .containsExactly(Horario.fechado(ALEGRE), Horario.fechado(JERONIMO));
        assertThat(QuadroDeHorarios.de(SEGUNDA, "Jantar", AMBOS, doAviso()))
                .containsExactly(Horario.fechado(ALEGRE), Horario.fechado(JERONIMO));
        assertThat(QuadroDeHorarios.de(SEGUNDA, "Almoço", AMBOS, doAviso()))
                .containsExactly(new Horario(ALEGRE, "11h00 às 12h30", true),
                        new Horario(JERONIMO, "11h30 às 12h30", true));
    }

    @Test
    void filtroDoDisparoDeixaSoAUnidadePedida() {
        LocalDate quinta = LocalDate.of(2026, 10, 29);
        assertThat(QuadroDeHorarios.de(quinta, "Almoço", AMBOS, "alegre", List.of()))
                .containsExactly(new Horario(ALEGRE, "11h00 às 13h30", false));
        assertThat(QuadroDeHorarios.de(quinta, "Almoço", AMBOS, "jeronimo", List.of()))
                .containsExactly(new Horario(JERONIMO, "11h30 às 13h00", false));
        assertThat(QuadroDeHorarios.de(quinta, "Almoço", AMBOS, "", List.of())).hasSize(2);
    }

    @Test
    void diaFechadoInteiroESemRu() {
        assertThat(QuadroDeHorarios.semAtendimento(SABADO, "", doAviso()))
                .containsExactly(ALEGRE, JERONIMO);
        assertThat(QuadroDeHorarios.semAtendimento(SABADO, "alegre", doAviso()))
                .containsExactly(ALEGRE);
    }

    @Test
    void diaComAlgumaRefeicaoNaoESemRu() {
        // segunda 26/10: sem cafe e sem jantar, mas com almoco reduzido
        assertThat(QuadroDeHorarios.semAtendimento(SEGUNDA, "", doAviso())).isEmpty();
        assertThat(QuadroDeHorarios.semAtendimento(SEXTA, "", doAviso())).isEmpty();
        assertThat(QuadroDeHorarios.semAtendimento(SABADO, "", List.of())).isEmpty();
    }

    @Test
    void domingoFechadoNaoESemRuPorqueNemAbriria() {
        LocalDate domingo = LocalDate.of(2026, 10, 25);
        List<Excecao> fechado = List.of(new Excecao(domingo, null, null, null, false));
        assertThat(QuadroDeHorarios.semAtendimento(domingo, "", fechado)).isEmpty();
    }

    private static List<Excecao> de(String fixture) {
        return LeitorDeAvisos.excecoes(Fixtures.ler(fixture), 2026);
    }

    @Test
    void textoCorridoComDiasPorExtensoFechaOsDoisDias() {
        // "nos dias 30 (sexta-feira) e 31 de outubro (sabado) nao havera atendimento"
        List<Excecao> excecoes = de(Fixtures.AVISO_SEM_ATENDIMENTO);

        assertThat(excecoes).extracting(Excecao::dia).containsOnly(
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31));
        assertThat(excecoes).allMatch(e -> e.unidade() == null && e.refeicao() == null
                && e.faixa() == null);
        assertThat(QuadroDeHorarios.semAtendimento(LocalDate.of(2026, 10, 30), "alegre",
                excecoes)).containsExactly(ALEGRE);
    }

    @Test
    void dataEntreParentesesNaoEDiaFechado() {
        // "(originalmente assinalado a 28 de outubro)" esta na frase da suspensao
        assertThat(de(Fixtures.AVISO_SEM_ATENDIMENTO)).extracting(Excecao::dia)
                .doesNotContain(LocalDate.of(2026, 10, 28));
    }

    @Test
    void suspensaoDeUmDiaNasDuasUnidades() {
        assertThat(de(Fixtures.AVISO_SUSPENSAO)).extracting(Excecao::dia)
                .containsOnly(LocalDate.of(2026, 10, 10));
        assertThat(QuadroDeHorarios.semAtendimento(LocalDate.of(2026, 10, 10), "",
                de(Fixtures.AVISO_SUSPENSAO))).containsExactly(ALEGRE, JERONIMO);
    }

    @Test
    void funcionaraNormalmenteNaoFechaNada() {
        assertThat(de(Fixtures.AVISO_PONTO_FACULTATIVO)).isEmpty();
    }

    @Test
    void refeicaoSuspensaEmTextoCorridoFicaDeFora() {
        // a data numa frase e o "nao sera servido o jantar" em outra: nao se adivinha
        assertThat(de(Fixtures.AVISO_SUSPENSAO_JANTAR)).isEmpty();
    }

    @Test
    void textoCorridoQueCitaUmaUnidadeSoFechaEla() {
        assertThat(LeitorDeAvisos.doTextoCorrido(
                "Informamos que o RU de Jerônimo Monteiro não funcionará no dia 5/11.", 2026))
                .containsExactly(new Excecao(LocalDate.of(2026, 11, 5), JERONIMO, null, null,
                        false));
    }

    @Test
    void intervaloFechaTodosOsDiasDeDentro() {
        assertThat(LeitorDeAvisos.doTextoCorrido(
                "Durante o recesso, de 22/12 a 02/01/2027, não haverá atendimento.", 2026))
                .extracting(Excecao::dia)
                .hasSize(12)
                .contains(LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 31),
                        LocalDate.of(2027, 1, 2));
        assertThat(LeitorDeAvisos.doTextoCorrido(
                "Não haverá atendimento entre os dias 23 e 26 de outubro.", 2026))
                .extracting(Excecao::dia)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24),
                        LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26));
    }

    @Test
    void listaComEContinuaSendoLista() {
        // sem "entre", "30 e 31" sao dois dias, nao um intervalo
        assertThat(LeitorDeAvisos.doTextoCorrido(
                "Nos dias 3 e 6 de novembro não haverá atendimento.", 2026))
                .extracting(Excecao::dia)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6));
    }

    @Test
    void avisoNaoMexeEmDiaQueEleNaoCita() {
        assertThat(doAviso()).allMatch(e -> !e.dia().isBefore(SEXTA) && !e.dia().isAfter(SEGUNDA));
    }

    @Test
    void soAbreNoticiaComCaraDeHorario() {
        String inicial = "<ul>"
                + "<li><a href=\"/conteudo/horario-especial\">Horário Especial de Funcionamento</a></li>"
                + "<li><a href=\"/conteudo/horario-especial\">Horário Especial de Funcionamento</a></li>"
                + "<li><a href=\"/conteudo/biometria\">Cadastramento de Biometria Facial</a></li>"
                + "<li><a href=\"/conteudo/suspensao\">Suspensão do atendimento no RU</a></li>"
                + "<li><a href=\"/cardapio\">Horário do cardápio</a></li>"
                + "</ul>";

        assertThat(LeitorDeAvisos.noticias(inicial, "https://restaurante.alegre.ufes.br/"))
                .containsExactly("https://restaurante.alegre.ufes.br/conteudo/horario-especial",
                        "https://restaurante.alegre.ufes.br/conteudo/suspensao");
    }
}
