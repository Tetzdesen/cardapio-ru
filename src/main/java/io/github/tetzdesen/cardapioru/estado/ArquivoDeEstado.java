package io.github.tetzdesen.cardapioru.estado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tetzdesen.cardapioru.coleta.Vocabulario;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.EstadoProperties;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.notificacao.Canal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Porte do {@code carregar_estado} e do {@code salvar_estado} do ru_bot.py.
 *
 * <p>O formato e o mesmo, de proposito: chave {@code AAAA-MM-DD|refeicao},
 * valor igual a assinatura, objeto ordenado por chave e indentado com dois
 * espacos. E o que torna a virada silenciosa -- o arquivo que ja esta no
 * repositorio continua valendo, e nenhuma refeicao volta a ser inedita. Os
 * outros canais acrescentam o nome ao fim da chave
 * ({@code AAAA-MM-DD|refeicao|whatsapp}); o Telegram, que veio primeiro, fica
 * sem sufixo.
 *
 * <p>Ler nunca falha: arquivo ausente ou ilegivel vira estado vazio, porque
 * arquivo que nao existe e o caso normal da primeira execucao. Gravar falha
 * alto: {@link EstadoIndisponivel} depois de um envio confirmado deixa a
 * inconsistencia visivel a quem agendou, em vez de silenciar amanha.
 *
 * <p>Com o {@link EspelhoNoGithub} ativo, o arquivo em disco vira copia de
 * trabalho: a primeira leitura o traz do GitHub, e cada gravacao vai para os
 * dois. E o que faz o estado sobreviver ao deploy e ao reinicio do Render.
 */
@Component
public class ArquivoDeEstado {

    private static final Logger log = LoggerFactory.getLogger(ArquivoDeEstado.class);

    private static final Pattern DATA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final Pattern CANAL = Pattern.compile("[a-z]+");

    /** No lugar da refeicao, a chave do aviso de dia sem RU. */
    public static final String SEM_RU = "sem-ru";

    private final Path caminho;
    private final int retencaoDias;
    private final Clock relogio;
    private final ObjectMapper json = new ObjectMapper();
    private final EspelhoNoGithub espelho;

    /** Se o disco ja recebeu o que esta no GitHub. Sem espelho, nao ha o que trazer. */
    private boolean sincronizado;

    @Autowired
    public ArquivoDeEstado(EstadoProperties estado, CardapioProperties cardapio, Clock relogio,
            EspelhoNoGithub espelho) {
        this.caminho = estado.caminho();
        this.retencaoDias = cardapio.retencaoDias();
        this.relogio = relogio;
        this.espelho = espelho != null && espelho.ativo() ? espelho : null;
        this.sincronizado = this.espelho == null;
    }

    /** So o disco, sem espelho. */
    public ArquivoDeEstado(EstadoProperties estado, CardapioProperties cardapio, Clock relogio) {
        this(estado, cardapio, relogio, null);
    }

    /** Com o relogio de parede, no fuso do RU. */
    public ArquivoDeEstado(EstadoProperties estado, CardapioProperties cardapio) {
        this(estado, cardapio, Clock.system(cardapio.fuso()));
    }

    public Path caminho() {
        return caminho;
    }

    /** Onde o estado e espelhado; vazio se so no disco. */
    public Optional<String> espelho() {
        return Optional.ofNullable(espelho).map(EspelhoNoGithub::descricao);
    }

    /**
     * Traz o estado do GitHub para o disco, uma vez por processo. Na falha, o
     * disco segue valendo e a proxima leitura tenta de novo -- sem o GitHub, o
     * pior caso e o de antes do espelho.
     */
    private synchronized void sincronizar() {
        if (sincronizado) {
            return;
        }
        try {
            Optional<String> remoto = espelho.baixar();
            if (remoto.isPresent()) {
                Map<String, String> unido = interpretar(remoto.get(), espelho.descricao());
                // o que o disco tem de mais novo (gravado enquanto o GitHub falhava) vence
                unido.putAll(lerDisco());
                escreverDisco(serializar(new TreeMap<>(unido)));
            }
            sincronizado = true;
            log.info("estado: trazido de {}", espelho.descricao());
        } catch (EstadoIndisponivel e) {
            log.warn("estado: nao foi possivel trazer de {}; vale o disco: {}",
                    espelho.descricao(), e.getMessage());
        }
    }

    /** A chave de uma entrada do Telegram. A refeicao ja chega normalizada ("almoco"). */
    public static String chave(LocalDate data, String refeicaoNormalizada) {
        return data + "|" + refeicaoNormalizada;
    }

    /** A chave de uma entrada de um canal; a do Telegram continua sem sufixo. */
    public static String chave(LocalDate data, String refeicaoNormalizada, String canal) {
        String chave = chave(data, refeicaoNormalizada);
        return Canal.TELEGRAM.equals(canal) ? chave : chave + "|" + canal;
    }

    /**
     * Porte do {@code _chave_valida}: a data precisa ser uma data e a refeicao
     * precisa ser uma das que o RU serve (ou o aviso de dia sem RU), e o canal,
     * se houver, um nome simples.
     * Formato anterior e lixo caem aqui.
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
        int outraBarra = refeicao.indexOf('|');
        if (outraBarra >= 0) {
            String canal = refeicao.substring(outraBarra + 1);
            refeicao = refeicao.substring(0, outraBarra);
            if (!CANAL.matcher(canal).matches()) {
                return false;
            }
        }
        return DATA_ISO.matcher(dia).matches()
                && (Vocabulario.REFEICOES_NORM.contains(refeicao) || refeicao.equals(SEM_RU));
    }

    /** Le o estado inteiro, ignorando o que nao entende. Nunca lanca. */
    public Map<String, String> ler() {
        sincronizar();
        return lerDisco();
    }

    private Map<String, String> lerDisco() {
        if (!Files.exists(caminho)) {
            log.debug("estado: {} ainda nao existe; tratando como vazio", caminho);
            return new LinkedHashMap<>();
        }
        try {
            return interpretar(Files.readString(caminho, StandardCharsets.UTF_8),
                    caminho.toString());
        } catch (IOException e) {
            log.warn("estado em {} ilegivel ({}); tratando como vazio", caminho, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, String> interpretar(String texto, String origem) {
        JsonNode raiz;
        try {
            raiz = json.readTree(texto);
        } catch (IOException e) {
            log.warn("estado em {} ilegivel ({}); tratando como vazio", origem, e.getMessage());
            return new LinkedHashMap<>();
        }
        if (raiz == null || !raiz.isObject()) {
            log.warn("estado em {} nao e um objeto JSON; tratando como vazio", origem);
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
        // sem sincronizar aqui: o mapa recebido foi lido antes, e trazer o GitHub
        // agora faria a gravacao abaixo apagar do ramo o que acabou de chegar
        String limite = LocalDate.now(relogio).minusDays(retencaoDias).toString();
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

        String texto = serializar(podado);
        escreverDisco(texto);
        if (espelho == null) {
            return;
        }
        if (!sincronizado) {
            // mandar agora sobrescreveria o que esta la com um disco que nao o
            // leu. Fica no disco, e a proxima leitura que alcancar o GitHub junta
            // os dois; a falha sobe para nao ficar parada sem ninguem saber.
            throw new EstadoIndisponivel("estado gravado so no disco: "
                    + espelho.descricao() + " ainda nao pode ser lido", null);
        }
        espelho.enviar(texto);
    }

    private void escreverDisco(String texto) {
        try {
            Path pasta = caminho.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            Files.writeString(caminho, texto, StandardCharsets.UTF_8);
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
