package io.github.tetzdesen.cardapioru.notificacao;

import java.util.ArrayList;
import java.util.List;

/**
 * Junta pecas em mensagens de ate {@code limite} caracteres, sem partir uma peca
 * ao meio -- e o que permite saber, na falha, quais refeicoes ja chegaram. Porte
 * do {@code _agrupar} do ru_bot.py, comum a todos os canais.
 */
final class Agrupador {

    record Mensagem(List<String> refeicoes, String texto) {
    }

    private Agrupador() {
    }

    static List<Mensagem> agrupar(List<Peca> pecas, int limite) {
        List<Mensagem> mensagens = new ArrayList<>();
        List<String> refeicoes = new ArrayList<>();
        String atual = "";

        for (Peca peca : pecas) {
            String candidato = atual.isEmpty() ? peca.texto() : atual + "\n\n" + peca.texto();
            if (!atual.isEmpty() && candidato.length() > limite) {
                mensagens.add(new Mensagem(List.copyOf(refeicoes), atual));
                refeicoes = new ArrayList<>();
                atual = peca.texto();
            } else {
                atual = candidato;
            }
            if (peca.refeicao() != null) {
                refeicoes.add(peca.refeicao());
            }
        }
        if (!atual.isBlank()) {
            mensagens.add(new Mensagem(List.copyOf(refeicoes), atual));
        }
        return mensagens;
    }
}
