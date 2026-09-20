package io.github.tetzdesen.cardapioru.erro;

/**
 * O estado nao pode ser <b>gravado</b> depois de um envio confirmado. E o unico
 * caso que sobrou: arquivo ausente ou ilegivel vale como estado vazio, porque
 * arquivo que nao existe e o caso normal da primeira execucao.
 *
 * <p>Falhar alto aqui e proposital. A mensagem ja chegou ao grupo e nao da para
 * desfazer; deixar a inconsistencia visivel faz a execucao seguinte reenviar
 * aquela refeicao, que e a falha barata. Ficar mudo seria a cara.
 */
public class EstadoIndisponivel extends RuntimeException {

    public EstadoIndisponivel(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
