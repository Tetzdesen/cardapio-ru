package io.github.tetzdesen.cardapioru.coleta;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import io.github.tetzdesen.cardapioru.dominio.Horario;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * O horario regular do RU, copiado de
 * https://restaurante.alegre.ufes.br/horario-de-funcionamento, e a regra de
 * como os avisos de horario especial mudam ele.
 *
 * <p>O regular fica no codigo e nao e lido da pagina: muda uma vez por semestre,
 * se tanto, e a tabela do site nao tem marcacao em que se confie. O que muda de
 * uma semana para outra -- horario reduzido, dia fechado -- vem dos avisos.
 */
public final class QuadroDeHorarios {

    public static final String ALEGRE = "Alegre";
    public static final String JERONIMO = "Jerônimo Monteiro";
    public static final List<String> UNIDADES = List.of(ALEGRE, JERONIMO);

    /** As tres refeicoes, pela chave que {@link #chaveDe} devolve. */
    public static final String CAFE = "cafe";
    public static final String ALMOCO = "almoco";
    public static final String JANTAR = "jantar";

    private static final Map<String, Map<String, String>> SEMANA = Map.of(
            CAFE, Map.of(ALEGRE, "06h45 às 08h00", JERONIMO, "07h00 às 08h00"),
            ALMOCO, Map.of(ALEGRE, "11h00 às 13h30", JERONIMO, "11h30 às 13h00"),
            JANTAR, Map.of(ALEGRE, "17h15 às 19h30", JERONIMO, "17h15 às 18h30"));

    /** Sabado so tem almoco, e o proprio site o chama de horario reduzido. */
    private static final Map<String, String> SABADO =
            Map.of(ALEGRE, "11h00 às 12h30", JERONIMO, "11h30 às 12h30");

    /**
     * Uma mudanca tirada de um aviso. {@code unidade} e {@code refeicao} nulas
     * valem para todas; {@code faixa} nula e sem atendimento.
     */
    public record Excecao(LocalDate dia, String unidade, String refeicao, String faixa,
            boolean reduzido) {
    }

    private QuadroDeHorarios() {
    }

    /** "cafe", "almoco" ou "jantar", pelo nome da refeicao no site. */
    public static Optional<String> chaveDe(String refeicao) {
        String n = normalizar(refeicao);
        if (n.startsWith("desjejum") || n.startsWith("cafe")) {
            return Optional.of(CAFE);
        }
        if (n.startsWith("almoco")) {
            return Optional.of(ALMOCO);
        }
        if (n.startsWith("jantar")) {
            return Optional.of(JANTAR);
        }
        return Optional.empty();
    }

    /**
     * O horario de cada unidade do {@code campus} naquela refeicao, com as
     * excecoes aplicadas na ordem -- a ultima que casar vale, como num aviso que
     * fala do dia inteiro e depois detalha uma refeicao. Unidade sem horario
     * regular nem excecao (o jantar de sabado) fica de fora.
     */
    public static List<Horario> de(LocalDate dia, String refeicao, String campus,
            List<Excecao> excecoes) {
        Optional<String> chave = chaveDe(refeicao);
        if (chave.isEmpty()) {
            return List.of();
        }

        List<Horario> horarios = new ArrayList<>();
        for (String unidade : unidadesDe(campus)) {
            Horario horario = regular(dia, chave.get(), unidade);
            for (Excecao e : excecoes) {
                if (e.dia().equals(dia)
                        && (e.unidade() == null || e.unidade().equals(unidade))
                        && (e.refeicao() == null || e.refeicao().equals(chave.get()))) {
                    horario = e.faixa() == null
                            ? Horario.fechado(unidade)
                            : new Horario(unidade, e.faixa(), e.reduzido());
                }
            }
            if (horario != null) {
                horarios.add(horario);
            }
        }
        return horarios;
    }

    private static Horario regular(LocalDate dia, String chave, String unidade) {
        DayOfWeek semana = dia.getDayOfWeek();
        if (semana == DayOfWeek.SUNDAY) {
            return null;
        }
        if (semana == DayOfWeek.SATURDAY) {
            return chave.equals(ALMOCO) ? new Horario(unidade, SABADO.get(unidade), true) : null;
        }
        return new Horario(unidade, SEMANA.get(chave).get(unidade), false);
    }

    /** As unidades citadas no campus do cabecalho; nenhuma citada, todas. */
    private static List<String> unidadesDe(String campus) {
        String n = normalizar(campus);
        List<String> citadas = UNIDADES.stream()
                .filter(u -> n.contains(normalizar(u)))
                .toList();
        return citadas.isEmpty() ? UNIDADES : citadas;
    }
}
