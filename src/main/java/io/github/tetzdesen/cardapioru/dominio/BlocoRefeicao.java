package io.github.tetzdesen.cardapioru.dominio;

import java.util.List;

/** Uma refeicao de um campus num dia, com suas secoes na ordem da pagina. */
public record BlocoRefeicao(String refeicao, String campus, String data, List<Secao> secoes) {

    public BlocoRefeicao {
        secoes = List.copyOf(secoes);
    }

    public boolean temItens() {
        return secoes.stream().anyMatch(s -> !s.itens().isEmpty());
    }
}
