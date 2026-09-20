package io.github.tetzdesen.cardapioru.coleta;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Porte do {@code filtrar} e da expansao de equivalentes do ru_bot.py. */
public final class Filtro {

    /** "cafe da manha" e "desjejum" sao a mesma refeicao com dois nomes no site. */
    private static final Map<String, List<String>> EQUIVALENTES =
            Map.of("cafe da manha", List.of("Desjejum", "Café da Manhã"));

    private Filtro() {
    }

    /**
     * Expande os nomes pedidos e recusa o que nao existe no cardapio do RU.
     * A CLI aceitava qualquer string e simplesmente nao casava com nada -- errar
     * o nome no workflow viraria silencio permanente, entao aqui recusamos.
     */
    public static List<String> refeicoesPedidas(String bruto) {
        if (bruto == null || bruto.isBlank() || normalizar(bruto).equals("todas")) {
            return List.of();
        }
        Set<String> expandidas = new LinkedHashSet<>();
        for (String parte : bruto.split(",")) {
            String pedida = parte.strip();
            if (pedida.isEmpty()) {
                continue;
            }
            String norm = normalizar(pedida);
            List<String> equivalentes = EQUIVALENTES.get(norm);
            if (equivalentes != null) {
                expandidas.addAll(equivalentes);
                continue;
            }
            if (!Vocabulario.REFEICOES_NORM.contains(norm)) {
                throw new ParametroInvalido("refeicoes",
                        "refeicao desconhecida: '" + pedida + "'",
                        aceitas());
            }
            expandidas.add(pedida);
        }
        return List.copyOf(expandidas);
    }

    public static List<String> aceitas() {
        List<String> nomes = new ArrayList<>(Vocabulario.REFEICOES);
        nomes.add("todas");
        return List.copyOf(nomes);
    }

    public static List<BlocoRefeicao> aplicar(List<BlocoRefeicao> blocos,
            List<String> refeicoes, String campus) {
        Set<String> alvoRefeicao = refeicoes == null || refeicoes.isEmpty()
                ? null
                : refeicoes.stream().map(Normalizador::normalizar)
                        .collect(java.util.stream.Collectors.toSet());
        String alvoCampus = campus == null || campus.isBlank() ? null : normalizar(campus);

        List<BlocoRefeicao> resultado = new ArrayList<>();
        for (BlocoRefeicao b : blocos) {
            if (alvoRefeicao != null && !alvoRefeicao.contains(normalizar(b.refeicao()))) {
                continue;
            }
            if (alvoCampus != null && !normalizar(b.campus()).contains(alvoCampus)) {
                continue;
            }
            resultado.add(b);
        }
        return List.copyOf(resultado);
    }
}
