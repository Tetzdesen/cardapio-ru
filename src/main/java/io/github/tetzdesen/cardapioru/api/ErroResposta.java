package io.github.tetzdesen.cardapioru.api;

import java.util.List;

/**
 * Corpo de erro. O {@code causa} e o que distingue os casos que dividem a faixa
 * 502 -- "estrutura nao reconhecida" e um canal que recusou a mensagem.
 */
public record ErroResposta(
        int status,
        String causa,
        String mensagem,
        String parametro,
        Object aceitos,
        List<String> avisos) {

    public static ErroResposta de(int status, String causa, String mensagem) {
        return new ErroResposta(status, causa, mensagem, null, null, null);
    }

    public static ErroResposta parametro(String parametro, String mensagem, Object aceitos) {
        return new ErroResposta(400, "PARAMETRO_INVALIDO", mensagem, parametro, aceitos, null);
    }
}
