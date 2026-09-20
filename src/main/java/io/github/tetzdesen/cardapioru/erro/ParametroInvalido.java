package io.github.tetzdesen.cardapioru.erro;

/** Parametro que o cliente mandou errado: 400, nomeando o parametro recusado. */
public class ParametroInvalido extends RuntimeException {

    private final String parametro;
    private final transient Object aceitos;

    public ParametroInvalido(String parametro, String mensagem, Object aceitos) {
        super(mensagem);
        this.parametro = parametro;
        this.aceitos = aceitos;
    }

    public ParametroInvalido(String parametro, String mensagem) {
        this(parametro, mensagem, null);
    }

    public String parametro() {
        return parametro;
    }

    public Object aceitos() {
        return aceitos;
    }
}
