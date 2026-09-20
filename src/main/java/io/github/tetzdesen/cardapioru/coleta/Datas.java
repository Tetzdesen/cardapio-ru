package io.github.tetzdesen.cardapioru.coleta;

import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * A data pedida, em AAAA-MM-DD. Mora aqui, e nao no controller, porque o
 * disparo de uma tacada so recusa a mesma data invalida pelos mesmos motivos --
 * e uma regra de entrada, nao de HTTP.
 */
public final class Datas {

    private Datas() {
    }

    public static LocalDate de(String bruto) {
        try {
            return LocalDate.parse(bruto);
        } catch (DateTimeParseException e) {
            throw new ParametroInvalido("data",
                    "data deve estar em AAAA-MM-DD; recebido: '" + bruto + "'",
                    "AAAA-MM-DD");
        }
    }
}
