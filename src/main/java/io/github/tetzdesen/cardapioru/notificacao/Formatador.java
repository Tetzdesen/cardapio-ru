package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.coleta.Vocabulario;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.DiaSemRu;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Porte do {@code formatar_bloco} e do {@code assinatura} do ru_bot.py.
 *
 * <p>O texto produzido aqui nao e so o que vai para o Telegram: e tambem o que
 * entra na assinatura do estado. Mudar um espaco muda a assinatura de todas as
 * refeicoes e provoca um reenvio geral -- por isso o porte e literal, incluindo
 * o escape no estilo do {@code html.escape} do Python.
 */
public final class Formatador {

    public static final String AVISO_MUDANCA =
            "⚠️ <b>O cardápio mudou desde o último aviso.</b>";

    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.of("pt", "BR"));

    private static final DateTimeFormatter DIA_POR_EXTENSO =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.of("pt", "BR"));

    private Formatador() {
    }

    /** Equivalente ao {@code html.escape(s, quote=True)} do Python. */
    public static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * Texto de uma refeicao isolada -- e o que entra na assinatura do estado. Nao
     * e mais o que vai ao grupo (ver {@link #mensagem}), mas fica como o porte
     * literal do Python: mudar um caractere aqui reenvia todas as refeicoes.
     */
    public static String bloco(BlocoRefeicao bloco) {
        String icone = Vocabulario.emojiDe(bloco.refeicao());
        List<String> partes = new ArrayList<>();
        partes.add(icone + " <b>" + escapar(bloco.refeicao()) + "</b> — "
                + escapar(bloco.data()) + "\n<i>" + escapar(bloco.campus()) + "</i>");

        for (Secao secao : bloco.secoes()) {
            if (secao.itens().isEmpty()) {
                continue;
            }
            if (!secao.rotulo().isEmpty()) {
                partes.add("\n<b>" + escapar(secao.rotulo()) + "</b>");
            }
            for (String item : secao.itens()) {
                partes.add("• " + escapar(item));
            }
        }
        return String.join("\n", partes);
    }

    /**
     * O que vai ao grupo: o mesmo conteudo do {@link #bloco}, com icone em cada
     * secao e no sabor dos sucos e frutas. Fica fora da assinatura de proposito
     * -- mexer no visual nao e o cardapio ter mudado, e nao pode disparar o aviso
     * de mudanca nem reenviar o dia.
     */
    public static String mensagem(BlocoRefeicao bloco) {
        return mensagem(bloco, List.of());
    }

    /**
     * Uma refeicao dentro do anuncio: o nome com o horario na mesma linha e as
     * secoes. A data e o campus ficam no {@link #cabecalho}, uma vez so por
     * mensagem. O horario tambem fica fora da assinatura: aviso de horario novo
     * nao e cardapio novo.
     */
    public static String mensagem(BlocoRefeicao bloco, List<Horario> horarios) {
        String icone = Vocabulario.emojiDe(bloco.refeicao());
        String titulo = icone + " <b>" + escapar(bloco.refeicao()) + "</b>";
        List<String> partes = new ArrayList<>();
        if (horarios.size() == 1) {
            partes.add(titulo + " · " + faixa(horarios.get(0)));
        } else {
            // mais de uma unidade: uma linha por unidade, com o nome dela
            partes.add(titulo);
            for (Horario horario : horarios) {
                partes.add(horario(horario));
            }
        }

        for (Secao secao : bloco.secoes()) {
            if (secao.itens().isEmpty()) {
                continue;
            }
            if (!secao.rotulo().isEmpty()) {
                String rotulo = "<b>" + escapar(secao.rotulo()) + "</b>";
                partes.add("\n" + Vocabulario.emojiDaSecao(secao.rotulo())
                        .map(e -> e + " " + rotulo)
                        .orElse(rotulo));
            }
            for (String item : secao.itens()) {
                // o sabor vai no fim: no lugar do marcador, desalinhava a secao
                partes.add("• " + escapar(item) + Vocabulario.emojiDoItem(secao.rotulo(), item)
                        .map(e -> " " + e)
                        .orElse(""));
            }
        }
        return String.join("\n", partes);
    }

    /**
     * O topo do anuncio: o dia e o RU, uma vez so, em vez de repetidos em cada
     * refeicao. {@code unidades} sao as do horario; sem horario, vale o campus
     * que a pagina declara.
     */
    public static String cabecalho(LocalDate data, List<String> unidades) {
        String dia = DIA_POR_EXTENSO.format(data);
        dia = Character.toUpperCase(dia.charAt(0)) + dia.substring(1);
        return "📅 <b>" + dia + "</b>\n📍 <i>RU " + escapar(String.join(" e ", unidades)) + "</i>";
    }

    /** A faixa de uma unidade sem o nome dela, para ir na linha da refeicao. */
    static String faixa(Horario h) {
        if (h.fechado()) {
            return "🚫 <b>sem atendimento hoje</b>";
        }
        if (h.reduzido()) {
            return "⚠️ " + escapar(h.faixa()) + " <b>(horário reduzido)</b>";
        }
        return escapar(h.faixa());
    }

    static String horario(Horario h) {
        String unidade = escapar(h.unidade());
        if (h.fechado()) {
            return "🚫 " + unidade + ": <b>sem atendimento</b>";
        }
        if (h.reduzido()) {
            return "⚠️ " + unidade + ": " + escapar(h.faixa()) + " <b>(horário reduzido)</b>";
        }
        return "🕒 " + unidade + ": " + escapar(h.faixa());
    }

    /** O aviso de um dia sem RU, contado a partir de {@code hoje}. */
    public static String semRu(DiaSemRu dia, LocalDate hoje) {
        String quando;
        if (dia.dia().equals(hoje)) {
            quando = "Hoje não tem RU";
        } else if (dia.dia().equals(hoje.plusDays(1))) {
            quando = "Amanhã não vai ter RU";
        } else {
            quando = "Não vai ter RU";
        }
        return "🚫 <b>" + quando + "</b> — " + DIA.format(dia.dia())
                + "\n📍 <i>" + escapar(String.join(" e ", dia.unidades())) + "</i>";
    }

    /** No lugar do cardapio de uma refeicao que as {@code fechadas} nao vao servir. */
    public static String semRefeicao(BlocoRefeicao bloco, List<Horario> fechadas) {
        String refeicao = bloco.refeicao().toLowerCase(Locale.of("pt", "BR"));
        return "🚫 <b>Sem " + escapar(refeicao) + " hoje</b>\n📍 <i>"
                + escapar(String.join(" e ", fechadas.stream().map(Horario::unidade).toList()))
                + "</i>";
    }

    /**
     * As unidades do cabecalho: as que aparecem no horario das refeicoes, na
     * ordem; sem nenhum horario, o campus que a pagina da primeira declara.
     */
    public static List<String> unidades(List<BlocoRefeicao> blocos,
            Function<BlocoRefeicao, List<Horario>> horarioDe) {
        LinkedHashSet<String> unidades = new LinkedHashSet<>();
        for (BlocoRefeicao bloco : blocos) {
            horarioDe.apply(bloco).forEach(h -> unidades.add(h.unidade()));
        }
        if (unidades.isEmpty() && !blocos.isEmpty()) {
            return List.of(blocos.get(0).campus());
        }
        return List.copyOf(unidades);
    }

    public static String semCardapio() {
        return "🍽️ <b>RU UFES Alegre</b>\n\nSem cardápio publicado para hoje.";
    }

    public static String linkDaOrigem(String url) {
        return "🔗 <a href=\"" + url + "\">ver no site</a>";
    }

    /**
     * Porte do {@code para_texto_puro}: tira a marcacao e desfaz o escape, para o
     * {@code --print} sair legivel no terminal em vez de cheio de {@code <b>}.
     */
    public static String semMarcacao(String textoHtml) {
        return desescapar(textoHtml.replaceAll("<[^>]+>", ""));
    }

    /**
     * O HTML das pecas na marcacao do WhatsApp: negrito e italico viram
     * {@code *} e {@code _}, e o link vira o endereco puro, porque o WhatsApp nao
     * esconde URL atras de texto.
     */
    public static String paraWhatsapp(String textoHtml) {
        String convertido = textoHtml
                .replaceAll("<a href=\"([^\"]*)\">([^<]*)</a>", "$2: $1")
                .replaceAll("</?b>", "*")
                .replaceAll("</?i>", "_");
        return semMarcacao(convertido);
    }

    private static String desescapar(String texto) {
        return texto.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&amp;", "&");
    }

    /** Assina uma refeicao isolada, sem depender das outras do mesmo disparo. */
    public static String assinatura(BlocoRefeicao bloco) {
        return assinatura(bloco(bloco));
    }

    public static String assinatura(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
