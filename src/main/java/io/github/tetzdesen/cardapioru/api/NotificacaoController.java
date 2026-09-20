package io.github.tetzdesen.cardapioru.api;

import io.github.tetzdesen.cardapioru.coleta.Datas;
import io.github.tetzdesen.cardapioru.coleta.Filtro;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O recurso criado e a notificacao; o cardapio e insumo. Por isso
 * {@code POST /notificacoes} e nao {@code POST /cardapios/{data}/enviar} -- e o
 * que deixa espaco para um {@code GET /notificacoes} listar o que ja foi enviado,
 * se um dia for util, sem reabrir o desenho.
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
public class NotificacaoController {

    private final ServicoNotificacao servico;
    private final ServicoCardapio cardapio;

    public NotificacaoController(ServicoNotificacao servico, ServicoCardapio cardapio) {
        this.servico = servico;
        this.cardapio = cardapio;
    }

    @PostMapping
    public ResponseEntity<DisparoResposta> disparar(
            @RequestBody(required = false) DisparoPedido pedido) {
        DisparoPedido p = pedido != null
                ? pedido
                : new DisparoPedido(null, null, null, null);

        LocalDate data = p.data() == null || p.data().isBlank()
                ? cardapio.hoje()
                : Datas.de(p.data());
        List<String> refeicoes = Filtro.refeicoesPedidas(p.refeicoes());

        // Um disparo em que nada precisava ser enviado nao e erro: 200 com a lista
        // de enviadas vazia e as omitidas nomeadas.
        return ResponseEntity.ok(
                servico.disparar(data, refeicoes, p.campus(), p.silenciosoOuPadrao()));
    }
}
