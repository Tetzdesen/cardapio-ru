package io.github.tetzdesen.cardapioru.dominio;

/**
 * Quando uma unidade serve uma refeicao num dia. {@code faixa} e o texto pronto
 * ("11h00 às 13h30") e fica nula quando a unidade nao atende.
 */
public record Horario(String unidade, String faixa, boolean reduzido) {

    public static Horario fechado(String unidade) {
        return new Horario(unidade, null, false);
    }

    public boolean fechado() {
        return faixa == null;
    }
}
