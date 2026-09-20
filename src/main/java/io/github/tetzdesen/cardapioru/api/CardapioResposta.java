package io.github.tetzdesen.cardapioru.api;

import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import java.time.LocalDate;
import java.util.List;

/**
 * O corpo de uma consulta. {@code publicado} e false no dia sem cardapio -- que e
 * resposta 200 legitima, nao erro, e por isso precisa ser distinguivel de um
 * filtro que simplesmente nao casou.
 */
public record CardapioResposta(
        LocalDate data,
        String origem,
        boolean publicado,
        List<RefeicaoResposta> refeicoes,
        List<String> avisos) {

    public record RefeicaoResposta(
            String refeicao,
            String campus,
            String dataPorExtenso,
            List<SecaoResposta> secoes) {

        static RefeicaoResposta de(BlocoRefeicao bloco) {
            return new RefeicaoResposta(bloco.refeicao(), bloco.campus(), bloco.data(),
                    bloco.secoes().stream()
                            .map(s -> new SecaoResposta(s.rotulo(), s.itens()))
                            .toList());
        }
    }

    public record SecaoResposta(String rotulo, List<String> itens) {
    }

    public static CardapioResposta de(LocalDate data, String origem,
            List<BlocoRefeicao> blocos, boolean publicado, List<String> avisos) {
        return new CardapioResposta(data, origem, publicado,
                blocos.stream().map(RefeicaoResposta::de).toList(), avisos);
    }
}
