package io.github.tetzdesen.cardapioru.erro;

/**
 * Um sistema do qual dependemos se comportou de forma inaceitavel. A causa
 * distingue os casos que compartilham a mesma faixa de status HTTP -- ver
 * design.md, "Mapa de status HTTP".
 */
public class FalhaDeOrigem extends RuntimeException {

    public enum Causa {
        /** A pagina baixou mas nenhum bloco de refeicao foi reconhecido: 502. */
        ESTRUTURA_NAO_RECONHECIDA,
        /** O site da UFES nao respondeu depois de esgotadas as tentativas: 504. */
        ORIGEM_INACESSIVEL,
        /** A cadeia de certificados nao fecha: 504, com instrucao de conserto. */
        CONFIANCA_TLS,
        /** O Telegram recusou a mensagem: 502. */
        TELEGRAM_RECUSOU
    }

    private final Causa causa;

    public FalhaDeOrigem(Causa causa, String mensagem) {
        super(mensagem);
        this.causa = causa;
    }

    public FalhaDeOrigem(Causa causa, String mensagem, Throwable origem) {
        super(mensagem, origem);
        this.causa = causa;
    }

    public Causa causa() {
        return causa;
    }
}
