package io.github.tetzdesen.cardapioru.api;

import java.time.LocalDate;
import java.util.List;

/**
 * O que o disparo fez. Um disparo em que nada precisava ser enviado nao e erro:
 * responde 200 com {@code enviadas} vazio e as omitidas listadas. {@code semRu}
 * sao os dias sem RU avisados neste disparo.
 */
public record DisparoResposta(
        LocalDate data,
        boolean publicado,
        List<String> enviadas,
        List<String> alteradas,
        List<String> omitidas,
        List<LocalDate> semRu,
        List<String> avisos) {
}
