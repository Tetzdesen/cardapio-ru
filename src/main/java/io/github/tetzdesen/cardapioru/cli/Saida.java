package io.github.tetzdesen.cardapioru.cli;

/**
 * Os codigos de saida que o agendador le. Sao distintos porque o log do Actions
 * mostra o codigo antes de qualquer corpo, e "site reformado" e "site fora do
 * ar" pedem reacoes diferentes.
 *
 * <p>Sucesso inclui o disparo em que nada precisava ser enviado e o dia que o
 * site declara sem cardapio: nenhum dos dois e falha, e fazer o job ficar
 * vermelho por isso seria treinar quem olha a ignorar o vermelho.
 */
public final class Saida {

    /** Inclusive "nada a enviar" e "dia sem cardapio publicado". */
    public static final int OK = 0;
    /** Argumento ou parametro invalido. */
    public static final int USO = 2;
    /** O Telegram recusou a mensagem. */
    public static final int TELEGRAM = 3;
    /** A pagina baixou mas nao e reconhecivel como cardapio. */
    public static final int ESTRUTURA = 4;
    /** O site da UFES nao respondeu depois de esgotadas as tentativas. */
    public static final int ORIGEM = 5;
    /** O estado nao pode ser gravado depois de um envio confirmado. */
    public static final int ESTADO = 6;

    private Saida() {
    }
}
