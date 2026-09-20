package io.github.tetzdesen.cardapioru.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * Um rotulo e seus itens. O rotulo vazio existe para os itens que aparecem antes
 * de qualquer rotulo na pagina -- acontece, e jogar fora seria perder comida.
 */
public record Secao(String rotulo, List<String> itens) {

    public Secao {
        itens = List.copyOf(itens);
    }

    public static Secao vazia(String rotulo) {
        return new Secao(rotulo, new ArrayList<>());
    }

    public Secao com(String item) {
        List<String> novos = new ArrayList<>(itens);
        novos.add(item);
        return new Secao(rotulo, novos);
    }
}
