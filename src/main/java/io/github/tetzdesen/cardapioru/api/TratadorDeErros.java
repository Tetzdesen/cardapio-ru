package io.github.tetzdesen.cardapioru.api;

import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A traducao dos codigos de saida do ru_bot.py para status HTTP. E o que faz o
 * job do Actions ficar vermelho na hora certa -- ver design.md, "Mapa de status".
 *
 * <p>Nenhuma falha de origem pode virar 200: foi exatamente esse o buraco que o
 * codigo de saida 4 existia para tapar.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(ParametroInvalido.class)
    public ResponseEntity<ErroResposta> parametroInvalido(ParametroInvalido e) {
        return ResponseEntity.badRequest()
                .body(ErroResposta.parametro(e.parametro(), e.getMessage(), e.aceitos()));
    }

    /**
     * Sem conseguir ler o estado nao da para saber o que ja foi enviado, e mandar
     * assim arriscaria duplicar a mensagem no grupo. 503 porque a falha e nossa,
     * nao da origem.
     */
    @ExceptionHandler(EstadoIndisponivel.class)
    public ResponseEntity<ErroResposta> estadoIndisponivel(EstadoIndisponivel e) {
        log.error("estado indisponivel", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErroResposta.de(503, "ESTADO_INDISPONIVEL", e.getMessage()));
    }

    @ExceptionHandler(FalhaDeOrigem.class)
    public ResponseEntity<ErroResposta> falhaDeOrigem(FalhaDeOrigem e) {
        HttpStatus status = switch (e.causa()) {
            case ESTRUTURA_NAO_RECONHECIDA, TELEGRAM_RECUSOU -> HttpStatus.BAD_GATEWAY;
            case ORIGEM_INACESSIVEL, CONFIANCA_TLS -> HttpStatus.GATEWAY_TIMEOUT;
        };
        log.error("{} -> {}", e.causa(), status.value(), e);
        return ResponseEntity.status(status)
                .body(ErroResposta.de(status.value(), e.causa().name(), e.getMessage()));
    }
}
