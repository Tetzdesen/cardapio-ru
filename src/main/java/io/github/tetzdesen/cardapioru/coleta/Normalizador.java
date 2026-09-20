package io.github.tetzdesen.cardapioru.coleta;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Porte do {@code normalizar} do ru_bot.py: minusculo, sem acento, sem pontuacao. */
public final class Normalizador {

    private static final Pattern COMBINANTES = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private Normalizador() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String t = Normalizer.normalize(texto, Normalizer.Form.NFKD);
        t = COMBINANTES.matcher(t).replaceAll("");
        return NAO_ALFANUMERICO.matcher(t.toLowerCase()).replaceAll("").strip();
    }

    /** Colapsa espaco em branco, como o {@code re.sub(r"\\s+", " ", ...)} do original. */
    public static String colapsar(String texto) {
        return texto == null ? "" : ESPACOS.matcher(texto).replaceAll(" ").strip();
    }
}
