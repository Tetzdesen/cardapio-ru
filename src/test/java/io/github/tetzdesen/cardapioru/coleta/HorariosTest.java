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
