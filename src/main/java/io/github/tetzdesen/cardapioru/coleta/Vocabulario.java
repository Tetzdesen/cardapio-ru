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

    /** Um icone por rotulo de secao, pela forma normalizada do rotulo. */
    public static final Map<String, String> EMOJI_SECAO = Map.ofEntries(
            Map.entry("entrada", "🥗"),
            Map.entry("salada", "🥗"),
            Map.entry("prato proteico", "🍗"),
            Map.entry("prato principal", "🍛"),
            Map.entry("opcao", "🌱"),
            Map.entry("acompanhamento", "🍚"),
            Map.entry("guarnicao", "🥔"),
            Map.entry("sobremesa", "🍮"),
            Map.entry("suco", "🧃"),
            Map.entry("refresco", "🧃"),
            Map.entry("pao", "🥖"),
            Map.entry("complemento", "🧈"),
            Map.entry("bebida", "🥤"),
            Map.entry("fruta", "🍎"));

    /**
     * Sabores e bebidas que ganham icone proprio no lugar do marcador. So vale
     * nas secoes de {@link #SECOES_COM_SABOR}: em "Frango com Laranja" a laranja
     * nao e o que importa.
     */
    private static final Map<String, String> EMOJI_SABOR = Map.ofEntries(
            Map.entry("morango", "🍓"),
            Map.entry("laranja", "🍊"),
            Map.entry("tangerina", "🍊"),
            Map.entry("mexerica", "🍊"),
            Map.entry("limao", "🍋"),
            Map.entry("abacaxi", "🍍"),
            Map.entry("manga", "🥭"),
            Map.entry("uva", "🍇"),
            Map.entry("melancia", "🍉"),
            Map.entry("melao", "🍈"),
            Map.entry("banana", "🍌"),
            Map.entry("maca", "🍎"),
            Map.entry("pera", "🍐"),
            Map.entry("pessego", "🍑"),
            Map.entry("acerola", "🍒"),
            Map.entry("cereja", "🍒"),
            Map.entry("coco", "🥥"),
            Map.entry("kiwi", "🥝"),
            Map.entry("cafe", "☕"),
            Map.entry("leite", "🥛"),
            Map.entry("cha", "🍵"),
            Map.entry("achocolatado", "🍫"),
            Map.entry("chocolate", "🍫"));

    private static final Set<String> SECOES_COM_SABOR =
            Set.of("suco", "refresco", "bebida", "fruta", "sobremesa");

    private static final Set<String> SECOES_DE_SUCO = Set.of("suco", "refresco");

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

    public static Optional<String> emojiDaSecao(String rotulo) {
        return Optional.ofNullable(EMOJI_SECAO.get(normalizar(rotulo)));
    }

    /**
     * O icone de um item, se a secao for de bebida, fruta ou sobremesa e o item
     * citar um sabor conhecido. Vale o sabor que aparece primeiro, por palavra
     * inteira: "Suco de Laranja com Acerola" e laranja, e "Pão" nao casa "pera".
     * Suco de sabor desconhecido (graviola, maracuja) leva a caixinha.
     */
    public static Optional<String> emojiDoItem(String rotulo, String item) {
        String secao = normalizar(rotulo);
        if (!SECOES_COM_SABOR.contains(secao)) {
            return Optional.empty();
        }
        List<String> palavras = List.of(normalizar(item).split(" "));
        for (String palavra : palavras) {
            String emoji = EMOJI_SABOR.get(palavra);
            if (emoji != null) {
                return Optional.of(emoji);
            }
        }
        if (SECOES_DE_SUCO.contains(secao) || palavras.contains("suco")) {
            return Optional.of("🧃");
        }
        return Optional.empty();
    }
}
