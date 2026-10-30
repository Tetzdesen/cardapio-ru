package io.github.tetzdesen.cardapioru.api;

import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.DiaSemRu;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * O corpo de uma consulta. {@code publicado} e false no dia sem cardapio -- que e
 * resposta 200 legitima, nao erro, e por isso precisa ser distinguivel de um
 * filtro que simplesmente nao casou. {@code semRu} sao os dias, a partir da data
 * consultada, que os avisos do RU dao como fechados; a propria data, se estiver
 * entre eles, e o dia sem RU.
 */
public record CardapioResposta(
        LocalDate data,
        String origem,
        boolean publicado,
        List<RefeicaoResposta> refeicoes,
        List<DiaSemRu> semRu,
        List<String> avisos) {

    public record RefeicaoResposta(
            String refeicao,
            String campus,
            String dataPorExtenso,
            List<HorarioResposta> horarios,
            List<SecaoResposta> secoes) {

        static RefeicaoResposta de(BlocoRefeicao bloco, List<Horario> horarios) {
            return new RefeicaoResposta(bloco.refeicao(), bloco.campus(), bloco.data(),
                    horarios.stream().map(HorarioResposta::de).toList(),
                    bloco.secoes().stream()
                            .map(s -> new SecaoResposta(s.rotulo(), s.itens()))
                            .toList());
        }
    }

    /** {@code faixa} fica de fora quando a unidade nao atende. */
    public record HorarioResposta(String unidade, String faixa, boolean reduzido,
            boolean fechado) {

        static HorarioResposta de(Horario h) {
            return new HorarioResposta(h.unidade(), h.faixa(), h.reduzido(), h.fechado());
        }
    }

    public record SecaoResposta(String rotulo, List<String> itens) {
    }

    public static CardapioResposta de(LocalDate data, String origem,
            List<BlocoRefeicao> blocos, Function<BlocoRefeicao, List<Horario>> horarioDe,
            boolean publicado, List<DiaSemRu> semRu, List<String> avisos) {
        return new CardapioResposta(data, origem, publicado,
                blocos.stream().map(b -> RefeicaoResposta.de(b, horarioDe.apply(b))).toList(),
                semRu, avisos);
    }
}
