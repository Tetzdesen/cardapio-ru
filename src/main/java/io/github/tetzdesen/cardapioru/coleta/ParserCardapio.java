package io.github.tetzdesen.cardapioru.coleta;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.colapsar;
import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.dominio.Analise;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Porte do {@code analisar} do ru_bot.py.
 *
 * <p>Nao depende de classe CSS de proposito: o site e um Plone da UFES que muda
 * a marcacao de vez em quando. Os cabecalhos saem por regex e as secoes pela
 * lista de rotulos conhecidos.
 */
@Component
public class ParserCardapio {

    private static final Logger log = LoggerFactory.getLogger(ParserCardapio.class);

    private static final List<String> SELETORES_DE_CONTEUDO =
            List.of("#content-core", "#content", "#conteudo", "main", "article");

    private final CardapioProperties props;

    public ParserCardapio(CardapioProperties props) {
        this.props = props;
    }

    /** A pagina depois de limpa: linhas de texto e pistas sobre a estrutura. */
    record Pagina(List<String> linhas, Set<String> rotulos, int tamanho) {
    }

    public Analise analisar(String html) {
        Pagina pagina = lerPagina(html);
        List<BlocoRefeicao> blocos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        String refeicao = null;
        String campus = null;
        String data = null;
        List<Secao> secoes = null;
        boolean esperandoData = false;
        boolean viuCabecalho = false;

        for (String linha : pagina.linhas()) {
            Matcher cabecalho = Vocabulario.CABECALHO.matcher(linha);
            if (cabecalho.matches()) {
                if (secoes != null) {
                    blocos.add(new BlocoRefeicao(refeicao, campus, data, secoes));
                }
                viuCabecalho = true;
                refeicao = cabecalho.group("refeicao").strip();
                campus = cabecalho.group("campus").strip();
                data = cabecalho.group("data").strip();
                secoes = new ArrayList<>();
                // o site quebra o cabecalho em duas linhas: a data vem na seguinte
                esperandoData = data.isEmpty();
                continue;
            }

            if (secoes == null) {
                continue;
            }

            if (esperandoData) {
                esperandoData = false;
                if (Vocabulario.DATA.matcher(linha).find()) {
                    data = linha;
                    continue;
                }
                // nao era data: segue o fluxo normal e trata como rotulo/item
            }

            if (Vocabulario.FIM_BLOCO.matcher(linha).find()) {
                blocos.add(new BlocoRefeicao(refeicao, campus, data, secoes));
                secoes = null;
                continue;
            }

            Optional<String> categoria = Vocabulario.categoriaDe(linha);
            if (categoria.isEmpty() && pagina.rotulos().contains(normalizar(linha))) {
                // o site marcou como rotulo, mas nao esta em CATEGORIAS: abre a secao
                // mesmo assim (o conteudo continua saindo) e avisa, para a lista ser
                // atualizada antes que alguem note a falta pelo grupo do Telegram.
                categoria = Optional.of(linha);
                String aviso = "rotulo de secao desconhecido: '" + linha
                        + "' (adicione em CATEGORIAS)";
                avisos.add(aviso);
                log.warn(aviso);
            }

            if (categoria.isPresent()) {
                secoes.add(Secao.vazia(categoria.get()));
            } else if (!secoes.isEmpty()) {
                int ultima = secoes.size() - 1;
                secoes.set(ultima, secoes.get(ultima).com(linha));
            } else {
                // item antes de qualquer rotulo
                secoes.add(new Secao("", List.of(linha)));
            }
        }
        if (secoes != null) {
            blocos.add(new BlocoRefeicao(refeicao, campus, data, secoes));
        }

        // descarta blocos vazios (cabecalho sem itens)
        List<BlocoRefeicao> comItens = blocos.stream().filter(BlocoRefeicao::temItens).toList();

        boolean reconhecida = viuCabecalho
                || Vocabulario.SEM_CARDAPIO.matcher(String.join(" ", pagina.linhas())).find()
                || pagina.tamanho() < props.limiarConteudo();

        return new Analise(comItens, reconhecida, avisos);
    }

    Pagina lerPagina(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript").remove();

        Element area = null;
        for (String seletor : SELETORES_DE_CONTEUDO) {
            Element candidato = doc.selectFirst(seletor);
            if (candidato != null && candidato.text().length() > 100) {
                area = candidato;
                break;
            }
        }
        if (area == null) {
            area = doc.body() != null ? doc.body() : doc;
        }

        List<String> linhas = new ArrayList<>();
        for (String bruta : textoComQuebras(area).split("\n")) {
            String limpa = colapsar(bruta);
            if (!limpa.isEmpty()) {
                linhas.add(limpa);
            }
        }

        return new Pagina(linhas, rotulosMarcados(area), area.text().replace(" ", "").length());
    }

    /**
     * Textos que o HTML apresenta como rotulo de secao.
     *
     * <p>No site, rotulo e {@code <p><strong>Entrada</strong></p>} e item e
     * {@code <p>Pirao</p>}. Usamos essa marcacao -- e nao o formato do texto --
     * porque itens e rotulos sao lexicalmente identicos ("Pirao", "Laranja" tem
     * a mesma cara de rotulo).
     */
    Set<String> rotulosMarcados(Element area) {
        Set<String> marcados = new HashSet<>();
        for (Element forte : area.select("strong, b")) {
            String texto = colapsar(forte.text());
            if (texto.isEmpty()) {
                continue;
            }
            Element pai = forte.parent();
            if (pai != null && colapsar(pai.text()).equals(texto)) {
                marcados.add(normalizar(texto));
            }
        }
        return marcados;
    }

    /** Equivalente ao {@code get_text("\n")} do BeautifulSoup. */
    private static String textoComQuebras(Element area) {
        Element copia = area.clone();
        copia.select("br").after("\\n");
        copia.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, td, th").before("\\n");
        return copia.text().replace("\\n", "\n");
    }
}
