package io.github.tetzdesen.cardapioru.servico;

import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.coleta.Filtro;
import io.github.tetzdesen.cardapioru.coleta.ParserCardapio;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.dominio.Analise;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Baixa, parseia e filtra. O cache e curto e por data: a consulta e aberta, e
 * rajada de chamadas aqui nao pode virar rajada no servidor da UFES.
 */
@Service
public class ServicoCardapio {

    private static final Logger log = LoggerFactory.getLogger(ServicoCardapio.class);

    /** O resultado de uma coleta, ja parseado, com o instante em que foi obtido. */
    private record Entrada(Analise analise, long nanos) {
    }

    public record Resultado(List<BlocoRefeicao> blocos, boolean publicado, List<String> avisos,
            String origem) {
    }

    private final ColetorDaUfes coletor;
    private final ParserCardapio parser;
    private final CardapioProperties props;
    private final Map<LocalDate, Entrada> cache = new ConcurrentHashMap<>();

    public ServicoCardapio(ColetorDaUfes coletor, ParserCardapio parser,
            CardapioProperties props) {
        this.coletor = coletor;
        this.parser = parser;
        this.props = props;
    }

    public LocalDate hoje() {
        return LocalDate.now(props.fuso());
    }

    /**
     * Analise da data, do cache quando fresca. Lanca {@link FalhaDeOrigem} com
     * causa ESTRUTURA_NAO_RECONHECIDA quando a pagina baixou mas o parser ficou
     * cego -- que e falha, ao contrario de dia sem cardapio.
     */
    public Analise analisar(LocalDate data) {
        Entrada guardada = cache.get(data);
        if (guardada != null && fresca(guardada)) {
            log.debug("cardapio de {} servido do cache", data);
            return guardada.analise();
        }

        Analise analise = parser.analisar(coletor.baixar(data));
        if (!analise.reconhecida()) {
            log.error("nenhum cabecalho de refeicao reconhecido em {} -- a estrutura do site "
                    + "provavelmente mudou", coletor.urlDe(data));
            throw new FalhaDeOrigem(FalhaDeOrigem.Causa.ESTRUTURA_NAO_RECONHECIDA,
                    "nenhum bloco de refeicao reconhecido em " + coletor.urlDe(data)
                            + " -- a estrutura do site provavelmente mudou");
        }

        podarCache();
        cache.put(data, new Entrada(analise, System.nanoTime()));
        return analise;
    }

    public Resultado consultar(LocalDate data, List<String> refeicoes, String campus) {
        Analise analise = analisar(data);
        List<BlocoRefeicao> filtrados = Filtro.aplicar(analise.blocos(), refeicoes, campus);
        return new Resultado(filtrados, !analise.blocos().isEmpty(), analise.avisos(),
                coletor.urlDe(data));
    }

    public String origemDe(LocalDate data) {
        return coletor.urlDe(data);
    }

    /** Descarta o que estiver guardado. Usado pelos testes e por operacao manual. */
    public void limparCache() {
        cache.clear();
    }

    private boolean fresca(Entrada entrada) {
        return Duration.ofNanos(System.nanoTime() - entrada.nanos()).compareTo(props.cache()) < 0;
    }

    private void podarCache() {
        cache.entrySet().removeIf(e -> !fresca(e.getValue()));
    }
}
