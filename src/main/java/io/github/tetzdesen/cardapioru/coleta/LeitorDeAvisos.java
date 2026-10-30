package io.github.tetzdesen.cardapioru.coleta;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.colapsar;
import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import io.github.tetzdesen.cardapioru.coleta.QuadroDeHorarios.Excecao;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Le os avisos de horario especial que o RU publica como noticia. O formato que
 * ele usa e uma lista por unidade e por dia:
 *
 * <pre>
 * 📍 RU Alegre
 * 🗓️ Sexta-feira (23/10/2026)
 * Jantar: Horário reduzido — das 17h15 às 18h30
 * 🗓️ Sábado (24/10/2026)
 * 🚫 Fechado durante todo o dia
 * </pre>
 *
 * <p>Do aviso em paragrafo corrido so entra o dia inteiro sem atendimento, que
 * e o caso comum ("no dia 10 de outubro de 2026 (sabado), nao havera atendimento
 * nos RUs de Alegre e Jeronimo Monteiro"): uma frase que fecha e nao cita
 * refeicao. Refeicao suspensa em texto corrido costuma por a data numa frase e a
 * refeicao em outra ("nesse dia nao sera servido o jantar"), e fica de fora --
 * melhor anunciar o horario regular do que um horario adivinhado.
 */
public final class LeitorDeAvisos {

    /** Titulo de noticia que vale abrir. */
    private static final Pattern TITULO =
            Pattern.compile("horario|funcionamento|atendimento|suspens");

    /** Quantas noticias abrir: as mais novas vem primeiro na pagina inicial. */
    static final int MAXIMO = 5;

    private static final Pattern DIA =
            Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b");

    private static final Pattern REFEICAO = Pattern.compile(
            "^[^\\p{L}]*(?<ref>café da manhã|cafe da manha|desjejum|almoço|almoco|jantar)"
                    + "\\s*[:\\-–—]\\s*(?<desc>.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String HORA = "(\\d{1,2})\\s*[h:]\\s*(\\d{2})?\\s*(?:min)?";
    private static final Pattern FAIXA = Pattern.compile(
            HORA + "\\s*(?:às|as|a|até|ate|-|–)\\s*" + HORA,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern FECHADO = Pattern.compile(
            "sem atendimento|fechad|nao havera|suspens|nao sera servid|nao funcionara");

    /** Frase de texto corrido que fecha o RU. Sobre o texto sem acento, com pontuacao. */
    private static final Pattern FECHA_NO_TEXTO = Pattern.compile(
            "nao havera (?:atendimento|funcionamento|expediente)|sem atendimento"
                    + "|suspensao (?:do|de) (?:atendimento|funcionamento)"
                    + "|nao (?:funcionara|abrira)|(?:estara|permanecera|ficara) fechad");

    private static final Pattern CITA_REFEICAO =
            Pattern.compile("cafe da manha|desjejum|almoco|jantar");

    private static final String NOMES_DOS_MESES = "janeiro|fevereiro|marco|abril|maio|junho"
            + "|julho|agosto|setembro|outubro|novembro|dezembro";

    private static final Pattern MES = Pattern.compile(
            "\\b((?:\\d{1,2}[ºo]?\\s*(?:,|e)\\s*)*\\d{1,2})[ºo]?\\s+de\\s+"
                    + "(" + NOMES_DOS_MESES + ")(?:\\s+de\\s+(\\d{4}))?");

    /**
     * "de 22/12 a 02/01", "entre 23/10 e 26/10". O "e" so e intervalo depois de
     * "entre": sem ele, "30/10 e 31/10" e lista, que os outros padroes ja leem.
     */
    private static final Pattern INTERVALO_NUMERICO = Pattern.compile(
            "(entre\\s+(?:os\\s+dias\\s+)?)?\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?"
                    + "\\s*(a|ate|e)\\s*(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b");

    /** "de 22 de dezembro a 2 de janeiro de 2027", "entre 23 e 26 de outubro". */
    private static final Pattern INTERVALO_POR_EXTENSO = Pattern.compile(
            "(entre\\s+(?:os\\s+dias\\s+)?)?\\b(\\d{1,2})[ºo]?(?:\\s+de\\s+("
                    + NOMES_DOS_MESES + "))?\\s*(a|ate|e)\\s*(\\d{1,2})[ºo]?\\s+de\\s+("
                    + NOMES_DOS_MESES + ")(?:\\s+de\\s+(\\d{4}))?");

    /** Intervalo maior que isso e leitura errada, nao recesso. */
    private static final int INTERVALO_MAXIMO = 62;

    private static final List<String> MESES = List.of("janeiro", "fevereiro", "marco",
            "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro",
            "dezembro");

    private static final Pattern PARENTESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern NUMERO = Pattern.compile("\\d{1,2}");

    /** Linha curta o bastante para ser titulo de unidade ou de dia, e nao paragrafo. */
    private static final int TITULO_CURTO = 60;

    private LeitorDeAvisos() {
    }

    /** Os enderecos das noticias da pagina inicial que podem trazer horario. */
    public static List<String> noticias(String htmlInicial, String base) {
        Document doc = Jsoup.parse(htmlInicial, base);
        Set<String> enderecos = new LinkedHashSet<>();
        for (Element link : doc.select("a[href*=/conteudo/]")) {
            if (TITULO.matcher(normalizar(link.text())).find()) {
                enderecos.add(link.absUrl("href"));
            }
            if (enderecos.size() == MAXIMO) {
                break;
            }
        }
        return List.copyOf(enderecos);
    }

    /** As excecoes de um aviso. {@code anoPadrao} vale para data sem ano. */
    public static List<Excecao> excecoes(String htmlAviso, int anoPadrao) {
        List<Excecao> excecoes = new ArrayList<>();
        String unidade = null;
        LocalDate dia = null;

        for (String linha : linhas(htmlAviso)) {
            String n = normalizar(linha);
            Matcher refeicao = REFEICAO.matcher(linha);

            if (refeicao.matches()) {
                if (dia == null) {
                    continue;
                }
                String chave = QuadroDeHorarios.chaveDe(refeicao.group("ref")).orElseThrow();
                String desc = refeicao.group("desc");
                Matcher faixa = FAIXA.matcher(desc);
                if (faixa.find()) {
                    excecoes.add(new Excecao(dia, unidade, chave, faixa(faixa),
                            normalizar(desc).contains("reduzid")));
                } else if (FECHADO.matcher(normalizar(desc)).find()) {
                    excecoes.add(new Excecao(dia, unidade, chave, null, false));
                }
                // "Funcionamento normal": o regular ja cobre
                continue;
            }

            List<Excecao> doTexto = doTextoCorrido(linha, anoPadrao);
            if (!doTexto.isEmpty()) {
                excecoes.addAll(doTexto);
                continue;
            }

            if (linha.length() > TITULO_CURTO) {
                continue;
            }
            Matcher data = DIA.matcher(linha);
            if (data.find()) {
                dia = data(data, anoPadrao);
                continue;
            }
            Optional<String> citada = unidadeDe(n);
            if (citada != null) {
                unidade = citada.orElse(null);
                dia = null;
                continue;
            }
            if (dia != null && FECHADO.matcher(n).find()) {
                excecoes.add(new Excecao(dia, unidade, null, null, false));
            }
        }
        return excecoes;
    }

    /**
     * Os dias inteiros sem atendimento de um paragrafo corrido, frase a frase.
     * O que esta entre parenteses nao conta como data: e la que o aviso poe o
     * dia da semana e as datas de contexto ("originalmente assinalado a 28 de
     * outubro"), que nao sao dia fechado.
     */
    static List<Excecao> doTextoCorrido(String linha, int anoPadrao) {
        List<Excecao> excecoes = new ArrayList<>();
        for (String frase : semAcento(linha).split("(?<=[.!?;])\\s+")) {
            if (!FECHA_NO_TEXTO.matcher(frase).find() || CITA_REFEICAO.matcher(frase).find()) {
                continue;
            }
            Optional<String> citada = unidadeDe(normalizar(frase));
            String unidade = citada == null ? null : citada.orElse(null);
            String semParenteses = PARENTESES.matcher(frase).replaceAll(" ");

            Set<LocalDate> dias = new LinkedHashSet<>();
            Matcher numerica = DIA.matcher(semParenteses);
            while (numerica.find()) {
                dias.add(data(numerica, anoPadrao));
            }
            Matcher porExtenso = MES.matcher(semParenteses);
            while (porExtenso.find()) {
                int mes = MESES.indexOf(porExtenso.group(2)) + 1;
                int ano = porExtenso.group(3) != null
                        ? Integer.parseInt(porExtenso.group(3)) : anoPadrao;
                Matcher numero = NUMERO.matcher(porExtenso.group(1));
                while (numero.find()) {
                    dias.add(dia(ano, mes, Integer.parseInt(numero.group())));
                }
            }
            dias.addAll(intervalos(semParenteses, anoPadrao));
            dias.remove(null);
            for (LocalDate dia : dias) {
                excecoes.add(new Excecao(dia, unidade, null, null, false));
            }
        }
        return excecoes;
    }

    /** Os dias de dentro dos intervalos da frase; as pontas os outros padroes ja leem. */
    private static List<LocalDate> intervalos(String frase, int anoPadrao) {
        List<LocalDate> dias = new ArrayList<>();
        Matcher n = INTERVALO_NUMERICO.matcher(frase);
        while (n.find()) {
            if (ehIntervalo(n.group(1), n.group(5))) {
                int anoFim = n.group(8) != null ? Integer.parseInt(n.group(8)) : anoPadrao;
                int mesInicio = Integer.parseInt(n.group(3));
                int mesFim = Integer.parseInt(n.group(7));
                // "de 22/12 a 02/01/2027": sem ano, o comeco e do ano anterior
                int anoInicio = n.group(4) != null ? Integer.parseInt(n.group(4))
                        : mesInicio > mesFim ? anoFim - 1 : anoFim;
                preencher(dias, dia(anoInicio, mesInicio, Integer.parseInt(n.group(2))),
                        dia(anoFim, mesFim, Integer.parseInt(n.group(6))));
            }
        }
        Matcher e = INTERVALO_POR_EXTENSO.matcher(frase);
        while (e.find()) {
            if (ehIntervalo(e.group(1), e.group(4))) {
                int mesFim = MESES.indexOf(e.group(6)) + 1;
                int mesInicio = e.group(3) != null ? MESES.indexOf(e.group(3)) + 1 : mesFim;
                int anoFim = e.group(7) != null ? Integer.parseInt(e.group(7)) : anoPadrao;
                // "de 22 de dezembro a 2 de janeiro de 2027": o comeco e do ano anterior
                int anoInicio = mesInicio > mesFim ? anoFim - 1 : anoFim;
                preencher(dias, dia(anoInicio, mesInicio, Integer.parseInt(e.group(2))),
                        dia(anoFim, mesFim, Integer.parseInt(e.group(5))));
            }
        }
        return dias;
    }

    private static boolean ehIntervalo(String entre, String separador) {
        return !separador.equals("e") || entre != null;
    }

    private static void preencher(List<LocalDate> dias, LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)
                || inicio.plusDays(INTERVALO_MAXIMO).isBefore(fim)) {
            return;
        }
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            dias.add(d);
        }
    }

    /** Minusculo e sem acento, mas com a pontuacao que separa frase e data. */
    private static String semAcento(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static LocalDate dia(int ano, int mes, int dia) {
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * A unidade de um titulo como "📍 RU Alegre". Nulo quando a linha nao e
     * titulo de unidade; vazio quando cita as duas.
     */
    private static Optional<String> unidadeDe(String normalizada) {
        boolean alegre = normalizada.contains("alegre");
        boolean jeronimo = normalizada.contains("jeronimo");
        if (!alegre && !jeronimo) {
            return null;
        }
        if (alegre && jeronimo) {
            return Optional.empty();
        }
        return Optional.of(alegre ? QuadroDeHorarios.ALEGRE : QuadroDeHorarios.JERONIMO);
    }

    private static LocalDate data(Matcher m, int anoPadrao) {
        int ano = m.group(3) != null ? Integer.parseInt(m.group(3)) : anoPadrao;
        return dia(ano, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
    }

    private static String faixa(Matcher m) {
        return hora(m.group(1), m.group(2)) + " às " + hora(m.group(3), m.group(4));
    }

    private static String hora(String h, String min) {
        return String.format("%02dh%s", Integer.parseInt(h), min == null ? "00" : min);
    }

    /** O texto do corpo da noticia, um paragrafo por linha. */
    private static List<String> linhas(String html) {
        Document doc = Jsoup.parse(html);
        Element corpo = doc.selectFirst(".field-name-body");
        if (corpo == null) {
            corpo = doc.selectFirst("article, #content, main");
        }
        if (corpo == null) {
            corpo = doc.body();
        }
        List<String> linhas = new ArrayList<>();
        for (Element bloco : corpo.select("p, li, h2, h3, h4, h5, h6")) {
            if (!bloco.tagName().equals("p") && !bloco.select("p").isEmpty()) {
                continue; // o texto sai pelos paragrafos de dentro
            }
            String texto = colapsar(bloco.text());
            if (!texto.isEmpty()) {
                linhas.add(texto);
            }
        }
        return linhas;
    }
}
