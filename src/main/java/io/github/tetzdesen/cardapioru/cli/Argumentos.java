package io.github.tetzdesen.cardapioru.cli;

import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Os argumentos de linha de comando do disparo, no formato que o ru_bot.py
 * aceitava -- {@code --data}, {@code --refeicoes}, {@code --campus},
 * {@code --enviar}, {@code --print}, {@code --vazio-silencioso} e
 * {@code --estado}.
 *
 * <p>A leitura e feita aqui, e nao pela ligacao de propriedades do Spring,
 * porque o argparse do Python aceita as duas grafias ({@code --data=X} e
 * {@code --data X}) e quem digita a mao usa a segunda. Um argumento
 * desconhecido nao e erro: o Spring recebe a mesma linha e tem opcoes suas.
 */
public record Argumentos(
        String data,
        String refeicoes,
        String campus,
        boolean enviar,
        boolean imprimir,
        boolean vazioSilencioso) {

    /** Opcoes com valor. As demais sao interruptores. */
    private static final Set<String> COM_VALOR =
            Set.of("data", "refeicoes", "campus", "estado");

    private static final Set<String> INTERRUPTORES =
            Set.of("enviar", "print", "vazio-silencioso");

    /** Os padroes do ru_bot.py, para que a comparacao com ele seja direta. */
    static final String REFEICOES_PADRAO = "almoco,jantar";
    static final String CAMPUS_PADRAO = "alegre";

    /**
     * Traduz {@code --estado X} e {@code --estado=X} para a propriedade que a
     * aplicacao liga ({@code estado.caminho}), e deixa o resto intacto.
     *
     * <p>Sem a traducao, um {@code --estado=X} viraria uma propriedade escalar
     * chamada {@code estado}, colidindo com o objeto de mesmo prefixo.
     */
    public static String[] paraSpring(String[] argv) {
        List<String> saida = new ArrayList<>();
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.equals("--estado") && i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                saida.add("--estado.caminho=" + argv[++i]);
            } else if (arg.startsWith("--estado=")) {
                saida.add("--estado.caminho=" + arg.substring("--estado=".length()));
            } else {
                saida.add(arg);
            }
        }
        return saida.toArray(String[]::new);
    }

    /** Quantas das nossas opcoes apareceram na linha. Zero significa "nao e disparo". */
    public static int quantasNossas(String[] argv) {
        int quantas = 0;
        for (String arg : argv) {
            String nome = nomeDe(arg);
            if (nome != null && (COM_VALOR.contains(nome) || INTERRUPTORES.contains(nome)
                    || nome.equals("estado.caminho"))) {
                quantas++;
            }
        }
        return quantas;
    }

    public static Argumentos ler(String[] argv) {
        Map<String, String> valores = new LinkedHashMap<>();
        Set<String> ligados = new java.util.HashSet<>();

        for (int i = 0; i < argv.length; i++) {
            String nome = nomeDe(argv[i]);
            if (nome == null) {
                continue;
            }
            String valorColado = argv[i].contains("=")
                    ? argv[i].substring(argv[i].indexOf('=') + 1)
                    : null;

            if (INTERRUPTORES.contains(nome)) {
                ligados.add(nome);
            } else if (COM_VALOR.contains(nome)) {
                if (valorColado != null) {
                    valores.put(nome, valorColado);
                } else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                    valores.put(nome, argv[++i]);
                } else {
                    throw new ParametroInvalido(nome, "--" + nome + " espera um valor");
                }
            }
        }

        return new Argumentos(
                valores.get("data"),
                valores.getOrDefault("refeicoes", REFEICOES_PADRAO),
                valores.getOrDefault("campus", CAMPUS_PADRAO),
                ligados.contains("enviar"),
                ligados.contains("print"),
                ligados.contains("vazio-silencioso"));
    }

    private static String nomeDe(String arg) {
        if (!arg.startsWith("--")) {
            return null;
        }
        String sem = arg.substring(2);
        int igual = sem.indexOf('=');
        return igual < 0 ? sem : sem.substring(0, igual);
    }
}
