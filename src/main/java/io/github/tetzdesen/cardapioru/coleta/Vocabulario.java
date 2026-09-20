package io.github.tetzdesen.cardapioru.coleta;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** As listas e regex que o ru_bot.py mantinha no topo do modulo. */
public final class Vocabulario {

    public static final List<String> REFEICOES =
            List.of("Desjejum", "Café da Manhã", "Cafe da Manha", "Almoço", "Jantar");

    /** Rotulos de secao que aparecem dentro de cada refeicao. */
    public static final List<String> CATEGORIAS = List.of(
            "Entrada", "Salada", "Prato Proteico", "Prato Principal", "Opção",
            "Acompanhamento", "Guarnição", "Sobremesa", "Suco", "Refresco",
            "Pão", "Complemento", "Bebida", "Fruta");

    public static final Map<String, String> EMOJI = Map.of(
            "desjejum", "☕",
            "cafe da manha", "☕",
            "almoco", "🍽️",
            "jantar", "🌙");

    public static final String EMOJI_PADRAO = "🍽️";

    public static final Pattern CABECALHO = Pattern.compile(
            "^(?<refeicao>" + String.join("|", REFEICOES) + ")"
                    + "(?:\\s+\\d{2}/\\d{2})?\\s*"          // "Almoço 10/09" (as vezes tem a data)
                    + "\\((?<campus>[^)]*)\\)\\s*[-–—]\\s*" // "(Alegre e Jerônimo Monteiro) - "
                    + "(?<data>.*)$",                       // data aqui OU na proxima linha
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Reconhece a linha de data quando ela vem separada do cabecalho. */
    public static final Pattern DATA = Pattern.compile(
            "^(segunda|terça|quarta|quinta|sexta)-feira|^(sábado|domingo)|^\\d{1,2} de \\w+ de \\d{4}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static final Pattern FIM_BLOCO = Pattern.compile(
            "(cardápio poderá sofrer|traços de glúten|CG\\s*=|CL\\s*=|^\\*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A pagina diz, em bom portugues, que nao ha cardapio naquela data. */
    public static final Pattern SEM_CARDAPIO = Pattern.compile(
            "(n[ãa]o h[áa] card[áa]pio|sem card[áa]pio|card[áa]pio n[ãa]o (?:está |estara |)"
                    + "(?:dispon|public)|n[ãa]o haver[áa] (?:card[áa]pio|atendimento|refei)|"
                    + "nenhum card[áa]pio)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Map<String, String> CATEGORIAS_NORM = CATEGORIAS.stream()
            .collect(Collectors.toMap(Normalizador::normalizar, c -> c, (a, b) -> a,
                    LinkedHashMap::new));

    public static final Set<String> REFEICOES_NORM =
            REFEICOES.stream().map(Normalizador::normalizar).collect(Collectors.toSet());

    private Vocabulario() {
    }

    /**
     * O rotulo canonico, se a linha <b>inteira</b> for um rotulo de secao.
     * Comparar a linha inteira e o que evita fatiar "Pão Francês" sob "Pão".
     */
    public static Optional<String> categoriaDe(String linha) {
        return Optional.ofNullable(CATEGORIAS_NORM.get(normalizar(linha)));
    }

    public static String emojiDe(String refeicao) {
        return EMOJI.getOrDefault(normalizar(refeicao), EMOJI_PADRAO);
    }
}
