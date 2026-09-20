package io.github.tetzdesen.cardapioru;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** As tres paginas reais congeladas, herdadas da suite em Python. */
public final class Fixtures {

    public static final String COMPLETA = "pagina-completa.html";
    public static final String VAZIA = "pagina-vazia.html";
    public static final String QUEBRADA = "pagina-quebrada.html";
    /** Segunda pagina real, capturada ao vivo em 2026-09-16 durante a virada. */
    public static final String AO_VIVO = "pagina-2026-09-16.html";
    /**
     * Data que o site nunca publicou (domingo 2026-09-20): a moldura institucional
     * do Plone, sem cabecalho de refeicao e sem dizer que nao ha cardapio.
     */
    public static final String NAO_PUBLICADA = "pagina-nao-publicada.html";

    private Fixtures() {
    }

    public static String ler(String nome) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + nome)) {
            if (in == null) {
                throw new IllegalStateException("fixture ausente: " + nome);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] bytes(String nome) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + nome)) {
            if (in == null) {
                throw new IllegalStateException("fixture ausente: " + nome);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
