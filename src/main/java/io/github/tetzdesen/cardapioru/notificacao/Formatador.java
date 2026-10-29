package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.coleta.Vocabulario;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

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

    private Formatador() {
    }

    /** Equivalente ao {@code html.escape(s, quote=True)} do Python. */
    static String escapar(String texto) {
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
     * A mensagem com o horario de cada unidade logo abaixo do campus. O horario
     * tambem fica fora da assinatura: aviso de horario novo nao e cardapio novo.
     */
    public static String mensagem(BlocoRefeicao bloco, List<Horario> horarios) {
        String icone = Vocabulario.emojiDe(bloco.refeicao());
        List<String> partes = new ArrayList<>();
        partes.add(icone + " <b>" + escapar(bloco.refeicao()) + "</b> — "
                + escapar(bloco.data()) + "\n📍 <i>" + escapar(bloco.campus()) + "</i>");
        for (Horario horario : horarios) {
            partes.add(horario(horario));
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
                String marcador = Vocabulario.emojiDoItem(secao.rotulo(), item).orElse("•");
                partes.add(marcador + " " + escapar(item));
            }
        }
        return String.join("\n", partes);
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
