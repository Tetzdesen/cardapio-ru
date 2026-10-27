package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Classificacao;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Situacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O miolo do disparo: coleta, decide o que e novidade, envia e grava.
 *
 * <p>O envio passa por cada {@link Canal} configurado -- Telegram, WhatsApp --
 * sem saber qual e qual. Cada canal tem o seu estado e falha sozinho: o WhatsApp
 * fora do ar nao impede o Telegram de receber, e o disparo seguinte manda so ao
 * canal que ficou para tras. A primeira falha sobe depois de todos tentarem.
 *
 * <p>A ordem e <b>enviar, depois gravar</b>, e grava-se apenas o que o canal
 * confirmou. Sem banco nao ha transacao para desfazer o que a falha seguinte
 * impediu de enviar; a garantia de "nao gravar o que nao foi enviado" passa a
 * vir da ordem de escrita -- ver design.md, "'Nao gravar o que nao foi enviado'
 * passa a vir da ordem de escrita".
 *
 * <p>Envio parcial registra as refeicoes ja aceitas, como o {@code
 * FalhaDeEnvio.confirmadas} do ru_bot.py fazia: reenviar ao grupo uma refeicao
 * que ja chegou e pior do que deixar a falha visivel no resultado do disparo.
 */
@Service
public class DisparoTransacional {

    private static final Logger log = LoggerFactory.getLogger(DisparoTransacional.class);

    private final ServicoCardapio cardapio;
    private final ServicoEstado estado;
    private final List<Canal> canais;

    public DisparoTransacional(ServicoCardapio cardapio, ServicoEstado estado,
            List<Canal> canais) {
        this.cardapio = cardapio;
        this.estado = estado;
        this.canais = List.copyOf(canais);
    }

    /** O que um canal fez, pelo nome da refeicao como aparece no site. */
    private record Envio(Set<String> enviadas, Set<String> alteradas, Set<String> omitidas) {
    }

    public DisparoResposta executar(LocalDate data, List<String> refeicoes, String campus,
            boolean silencioso) {
        ServicoCardapio.Resultado resultado = cardapio.consultar(data, refeicoes, campus);

        List<Canal> ativos = canais.stream().filter(Canal::configurado).toList();
        if (ativos.isEmpty()) {
            // a mesma causa de quando o Telegram era o unico canal e faltava configura-lo
            throw new FalhaDeEnvio(FalhaDeOrigem.Causa.TELEGRAM_RECUSOU,
                    "nenhum canal configurado: defina TELEGRAM_TOKEN e TELEGRAM_CHAT_ID, "
                            + "ou WHATSAPP_URL, WHATSAPP_API_KEY e WHATSAPP_CHAT_ID",
                    List.of(), null);
        }

        List<Envio> envios = new ArrayList<>();
        RuntimeException primeiraFalha = null;
        for (Canal canal : ativos) {
            try {
                envios.add(enviarPor(canal, data, resultado, silencioso));
            } catch (RuntimeException e) {
                log.error("{}: o envio falhou: {}", canal.nome(), e.getMessage());
                if (primeiraFalha == null) {
                    primeiraFalha = e;
                } else {
                    primeiraFalha.addSuppressed(e);
                }
            }
        }
        if (primeiraFalha != null) {
            throw primeiraFalha;
        }

        // enviada se chegou a algum canal; omitida so se nenhum precisou dela
        List<String> nomes = resultado.blocos().stream().map(b -> b.refeicao()).toList();
        List<String> enviadas = nomes.stream()
                .filter(n -> envios.stream().anyMatch(e -> e.enviadas().contains(n)))
                .toList();
        List<String> alteradas = nomes.stream()
                .filter(n -> envios.stream().anyMatch(e -> e.alteradas().contains(n)))
                .toList();
        List<String> omitidas = nomes.stream()
                .filter(n -> envios.stream().allMatch(e -> e.omitidas().contains(n)))
                .toList();
        return new DisparoResposta(data, resultado.publicado(), enviadas, alteradas, omitidas,
                resultado.avisos());
    }

    private Envio enviarPor(Canal canal, LocalDate data, ServicoCardapio.Resultado resultado,
            boolean silencioso) {
        List<Classificacao> classificadas =
                estado.classificar(data, canal.nome(), resultado.blocos());

        Set<String> omitidas = classificadas.stream()
                .filter(c -> !c.precisaEnviar())
                .map(c -> c.bloco().refeicao())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Classificacao> aEnviar = classificadas.stream()
                .filter(Classificacao::precisaEnviar)
                .toList();

        if (aEnviar.isEmpty()) {
            if (resultado.blocos().isEmpty() && !silencioso) {
                canal.enviar(List.of(Peca.avulsa(Formatador.semCardapio())));
                log.info("{}: nada publicado para {}; avisado no grupo", canal.nome(), data);
            } else {
                log.info("{}: nada a enviar para {} ({} omitida(s))", canal.nome(), data,
                        omitidas.size());
            }
            return new Envio(Set.of(), Set.of(), omitidas);
        }

        boolean alguemMudou = aEnviar.stream().anyMatch(c -> c.situacao() == Situacao.MUDOU);
        List<Peca> pecas = montar(aEnviar, cardapio.origemDe(data), alguemMudou);

        // Por refeicao normalizada: e nesse nome que o canal devolve o que
        // confirmou, e e ele que vira chave no arquivo de estado.
        Map<String, Classificacao> porRefeicao = new LinkedHashMap<>();
        for (Classificacao c : aEnviar) {
            porRefeicao.put(c.refeicaoNormalizada(), c);
        }

        List<String> confirmadas;
        try {
            confirmadas = canal.enviar(pecas);
        } catch (FalhaDeEnvio e) {
            // o que o canal chegou a aceitar antes de falhar fica gravado, para
            // nao voltar ao grupo no disparo seguinte; a falha continua subindo
            try {
                gravar(data, canal, porRefeicao, e.confirmadas());
            } catch (RuntimeException falhaAoGravar) {
                // a recusa do canal e a causa primeira: ela sobe, e a falha de
                // gravacao vai junto para nao se perder
                e.addSuppressed(falhaAoGravar);
            }
            throw e;
        }

        gravar(data, canal, porRefeicao, confirmadas);
        log.info("{}: enviado para {}: {} refeicao(oes){}", canal.nome(), data,
                confirmadas.size(), alguemMudou ? " (cardapio alterado)" : "");

        Set<String> enviadas = aEnviar.stream()
                .map(c -> c.bloco().refeicao())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> alteradas = aEnviar.stream()
                .filter(c -> c.situacao() == Situacao.MUDOU)
                .map(c -> c.bloco().refeicao())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Envio(enviadas, alteradas, omitidas);
    }

    private void gravar(LocalDate data, Canal canal, Map<String, Classificacao> porRefeicao,
            List<String> confirmadas) {
        List<Classificacao> gravaveis = confirmadas.stream()
                .map(porRefeicao::get)
                .filter(c -> c != null)
                .toList();
        estado.registrar(data, canal.nome(), gravaveis);
    }

    static List<Peca> montar(List<Classificacao> blocos, String origem, boolean aviso) {
        List<Peca> pecas = new ArrayList<>();
        if (aviso) {
            pecas.add(Peca.avulsa(Formatador.AVISO_MUDANCA));
        }
        for (Classificacao c : blocos) {
            pecas.add(new Peca(c.refeicaoNormalizada(), Formatador.mensagem(c.bloco())));
        }
        pecas.add(Peca.avulsa(Formatador.linkDaOrigem(origem)));
        return List.copyOf(pecas);
    }
}
