package io.github.tetzdesen.cardapioru.api;

/** Corpo do disparo. Os mesmos filtros da consulta, mais o silencio para dia vazio. */
public record DisparoPedido(
        String data,
        String refeicoes,
        String campus,
        Boolean silencioso) {

    public boolean silenciosoOuPadrao() {
        return silencioso == null || silencioso;
    }
}
