package io.github.tetzdesen.cardapioru.estado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.coleta.Vocabulario;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.EstadoProperties;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Porte do {@code carregar_estado} e do {@code salvar_estado} do ru_bot.py.
 *
 * <p>O formato e o mesmo, de proposito: chave {@code AAAA-MM-DD|refeicao},
 * valor igual a assinatura, objeto ordenado por chave e indentado com dois
 * espacos. E o que torna a virada silenciosa -- o arquivo que ja esta no
 * repositorio continua valendo, e nenhuma refeicao volta a ser inedita.
 *
 * <p>Ler nunca falha: arquivo ausente ou ilegivel vira estado vazio, porque
 * arquivo que nao existe e o caso normal da primeira execucao. Gravar falha
 * alto: {@link EstadoIndisponivel} depois de um envio confirmado deixa a
 * inconsistencia visivel a quem agendou, em vez de silenciar amanha.
 */
@Component
public class ArquivoDeEstado {

    private static final Logger log = LoggerFactory.getLogger(ArquivoDeEstado.class);

    private static final Pattern DATA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final Path caminho;
    private final int retencaoDias;
    private final java.time.ZoneId fuso;
    private final ObjectMapper json = new ObjectMapper();

    public ArquivoDeEstado(EstadoProperties estado, CardapioProperties cardapio) {
        this.caminho = estado.caminho();
        this.retencaoDias = cardapio.retencaoDias();
        this.fuso = cardapio.fuso();
    }

    public Path caminho() {
        return caminho;
    }

    /** A chave de uma entrada. A refeicao ja chega normalizada ("almoco"). */
    public static String chave(LocalDate data, String refeicaoNormalizada) {
        return data + "|" + refeicaoNormalizada;
    }

    /**
     * Porte do {@code _chave_valida}: a data precisa ser uma data e a refeicao
     * precisa ser uma das que o RU serve. Formato anterior e lixo caem aqui.
     */
    static boolean chaveValida(String chave) {
        if (chave == null) {
            return false;
        }
        int barra = chave.indexOf('|');
        if (barra < 0) {
            return false;
        }
        String dia = chave.substring(0, barra);
        String refeicao = chave.substring(barra + 1);
        return DATA_ISO.matcher(dia).matches() && Vocabulario.REFEICOES_NORM.contains(refeicao);
    }

    /** Le o estado inteiro, ignorando o que nao entende. Nunca lanca. */
    public Map<String, String> ler() {
        if (!Files.exists(caminho)) {
            log.debug("estado: {} ainda nao existe; tratando como vazio", caminho);
            return new LinkedHashMap<>();
        }

        JsonNode raiz;
        try {
            raiz = json.readTree(Files.readString(caminho, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("estado em {} ilegivel ({}); tratando como vazio", caminho, e.getMessage());
            return new LinkedHashMap<>();
        }
        if (raiz == null || !raiz.isObject()) {
            log.warn("estado em {} nao e um objeto JSON; tratando como vazio", caminho);
            return new LinkedHashMap<>();
        }

        Map<String, String> valido = new LinkedHashMap<>();
        int total = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = raiz.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entrada = it.next();
            total++;
            JsonNode valor = entrada.getValue();
            if (!chaveValida(entrada.getKey()) || !valor.isTextual()
                    || valor.textValue().isBlank()) {
                continue;
            }
            valido.put(entrada.getKey(), valor.textValue());
        }
        if (valido.size() < total) {
            log.info("estado: {} entrada(s) em formato desconhecido ignorada(s)",
                    total - valido.size());
        }
        return valido;
    }

    /**
     * Grava o estado, descartando o que passou da retencao. A saida e byte a byte
     * a do {@code salvar_estado}: ordenada por chave, indentada com dois espacos,
     * sem escapar acentos, com quebra de linha no fim.
     */
    public void gravar(Map<String, String> estado) {
        String limite = LocalDate.now(fuso).minusDays(retencaoDias).toString();
        Map<String, String> podado = new TreeMap<>();
        for (Map.Entry<String, String> e : estado.entrySet()) {
            if (chaveValida(e.getKey())
                    && e.getKey().substring(0, e.getKey().indexOf('|')).compareTo(limite) >= 0) {
                podado.put(e.getKey(), e.getValue());
            }
        }
        int descartadas = estado.size() - podado.size();
        if (descartadas > 0) {
            log.info("estado: {} entrada(s) anterior(es) a {} removida(s)", descartadas, limite);
        }

        try {
            Path pasta = caminho.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            Files.writeString(caminho, serializar(podado), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EstadoIndisponivel(
                    "nao foi possivel gravar o estado em " + caminho + ": " + e.getMessage(), e);
        }
    }

    /** O que o {@code json.dump(..., ensure_ascii=False, indent=2, sort_keys=True)} produz. */
    static String serializar(Map<String, String> ordenado) {
        if (ordenado.isEmpty()) {
            return "{}\n";
        }
        StringBuilder sb = new StringBuilder("{\n");
        int restantes = ordenado.size();
        for (Map.Entry<String, String> e : ordenado.entrySet()) {
            sb.append("  ").append(aspas(e.getKey())).append(": ").append(aspas(e.getValue()));
            sb.append(--restantes > 0 ? ",\n" : "\n");
        }
        return sb.append("}\n").toString();
    }

    private static String aspas(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
