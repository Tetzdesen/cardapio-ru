package io.github.tetzdesen.cardapioru.notificacao;

/**
 * Um pedaco da mensagem e a refeicao que ele leva, se houver. Rastrear isso e o
 * que permite saber, quando um envio falha no meio, quais refeicoes ja chegaram.
 */
public record Peca(String refeicao, String texto) {

    public static Peca avulsa(String texto) {
        return new Peca(null, texto);
    }
}
