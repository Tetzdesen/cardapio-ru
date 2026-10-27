package io.github.tetzdesen.cardapioru.api;

/**
 * Corpo do disparo. Os mesmos filtros da consulta, mais o silencio para dia vazio
 * e o {@code soAlteracoes}, que so reanuncia o que ja foi anunciado e mudou.
 */
public record DisparoPedido(
        String data,
        String refeicoes,
        String campus,
        Boolean silencioso,
        Boolean soAlteracoes) {

    public boolean silenciosoOuPadrao() {
        return silencioso == null || silencioso;
    }

    public boolean soAlteracoesOuPadrao() {
        return soAlteracoes != null && soAlteracoes;
    }
}
