package io.github.tetzdesen.cardapioru.api;

import io.github.tetzdesen.cardapioru.coleta.Datas;
import io.github.tetzdesen.cardapioru.coleta.Filtro;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta do cardapio. Aberta: quem le o cardapio nao precisa de credencial.
 *
 * <p>"Hoje" e a ausencia da data, nao um segmento magico: {@code /cardapios/hoje}
 * conviveria mal com {@code /cardapios/{data}}, porque seria um valor que nao e
 * uma data no mesmo lugar onde datas aparecem.
 */
@RestController
@RequestMapping("/api/v1/cardapios")
public class CardapioController {

    private final ServicoCardapio servico;

    public CardapioController(ServicoCardapio servico) {
        this.servico = servico;
    }

    @GetMapping
    public ResponseEntity<CardapioResposta> hoje(
            @RequestParam(required = false) String refeicoes,
            @RequestParam(required = false) String campus) {
        return responder(servico.hoje(), refeicoes, campus);
    }

    @GetMapping("/{data}")
    public ResponseEntity<CardapioResposta> porData(
            @PathVariable String data,
            @RequestParam(required = false) String refeicoes,
            @RequestParam(required = false) String campus) {
        return responder(Datas.de(data), refeicoes, campus);
    }

    private ResponseEntity<CardapioResposta> responder(LocalDate data, String refeicoes,
            String campus) {
        List<String> pedidas = Filtro.refeicoesPedidas(refeicoes);
        ServicoCardapio.Resultado resultado = servico.consultar(data, pedidas, campus);

        // 200 tambem no dia vazio e no filtro sem correspondencia: ausencia de
        // cardapio nao e falha, e nao pode ficar na mesma faixa que o site reformado.
        return ResponseEntity.ok(CardapioResposta.de(data, resultado.origem(),
                resultado.blocos(), resultado.publicado(), resultado.avisos()));
    }
}
