package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.util.List;

/** Falha de envio num canal, com as refeicoes que ele chegou a confirmar. */
public class FalhaDeEnvio extends FalhaDeOrigem {

    private final transient List<String> confirmadas;

    public FalhaDeEnvio(Causa causa, String mensagem, List<String> confirmadas,
            Throwable origem) {
        super(causa, mensagem, origem);
        this.confirmadas = List.copyOf(confirmadas);
    }

    public List<String> confirmadas() {
        return confirmadas;
    }
}
