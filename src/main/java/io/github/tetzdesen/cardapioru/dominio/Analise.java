package io.github.tetzdesen.cardapioru.dominio;

import java.util.List;

/**
 * Resultado do parse.
 *
 * <p>{@code reconhecida} e false so quando a pagina baixou, tem volume de texto
 * de pagina de conteudo, nao diz que esta sem cardapio, e mesmo assim nenhum
 * cabecalho de refeicao casou -- ou seja, o site mudou e o parser ficou cego.
 * Dia sem cardapio publicado continua sendo {@code reconhecida=true} com
 * {@code blocos} vazio: silencio legitimo, nao falha.
 */
public record Analise(List<BlocoRefeicao> blocos, boolean reconhecida, List<String> avisos) {

    public Analise {
        blocos = List.copyOf(blocos);
        avisos = List.copyOf(avisos);
    }
}
