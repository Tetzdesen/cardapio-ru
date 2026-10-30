package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.DiaSemRu;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.AvisoSemRu;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Classificacao;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Situacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import io.github.tetzdesen.cardapioru.servico.ServicoHorario;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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
 *
 * <p>Junto com o cardapio vai o aviso dos dias sem RU que os avisos de horario
 * especial anunciam: uma vez com antecedencia e outra no proprio dia, cada uma
 * so ate o canal confirmar.
 */
@Service
public class DisparoTransacional {

    private static final Logger log = LoggerFactory.getLogger(DisparoTransacional.class);

    private final ServicoCardapio cardapio;
    private final ServicoEstado estado;
    private final ServicoHorario horarios;
    private final List<Canal> canais;

    public DisparoTransacional(ServicoCardapio cardapio, ServicoEstado estado,
            ServicoHorario horarios, List<Canal> canais) {
        this.cardapio = cardapio;
        this.estado = estado;
        this.horarios = horarios;
        this.canais = List.copyOf(canais);
    }

    /** Prefixo da peca de dia sem RU, que o canal devolve entre as confirmadas. */
    private static final String PECA_SEM_RU = "sem-ru|";

    /** O que um canal fez, pelo nome da refeicao como aparece no site. */
    private record Envio(Set<String> enviadas, Set<String> alteradas, Set<String> omitidas,
            Set<LocalDate> semRu) {
    }

    public DisparoResposta executar(LocalDate data, List<String> refeicoes, String campus,
            boolean silencioso) {
        return executar(data, refeicoes, campus, silencioso, false);
    }

    /**
     * Com {@code soAlteracoes}, so volta ao grupo a refeicao que ja foi anunciada
     * e mudou depois disso. A inedita fica de fora -- ela tem o seu horario -- e
     * dia sem cardapio nao avisa nada. E o que deixa o agendador reconferir o
     * site varias vezes ao dia sem antecipar o anuncio de ninguem. Pelo mesmo
     * motivo, o aviso de dia sem RU espera o proximo anuncio.
     */
    public DisparoResposta executar(LocalDate data, List<String> refeicoes, String campus,
            boolean silencioso, boolean soAlteracoes) {
        List<DiaSemRu> fechados = horarios.diasSemRu(data, campus);
        List<DiaSemRu> semRu = soAlteracoes ? List.of() : fechados;
        ServicoCardapio.Resultado resultado = cardapio.consultar(data, refeicoes, campus,
                DiaSemRu.contem(fechados, data));

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
                envios.add(enviarPor(canal, data, campus, resultado, semRu,
                        silencioso || soAlteracoes, soAlteracoes));
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
        List<LocalDate> diasSemRu = semRu.stream()
                .map(DiaSemRu::dia)
                .filter(d -> envios.stream().anyMatch(e -> e.semRu().contains(d)))
                .toList();
        return new DisparoResposta(data, resultado.publicado(), enviadas, alteradas, omitidas,
                diasSemRu, resultado.avisos());
    }

    private Envio enviarPor(Canal canal, LocalDate data, String campus,
            ServicoCardapio.Resultado resultado, List<DiaSemRu> semRu, boolean silencioso,
            boolean soAlteracoes) {
        List<Classificacao> classificadas =
                estado.classificar(data, canal.nome(), resultado.blocos());

        Predicate<Classificacao> vai = soAlteracoes
                ? c -> c.situacao() == Situacao.MUDOU
                : Classificacao::precisaEnviar;
        Set<String> omitidas = classificadas.stream()
                .filter(vai.negate())
                .map(c -> c.bloco().refeicao())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Classificacao> aEnviar = classificadas.stream()
                .filter(vai)
                .toList();

        // Refeicao em que nenhuma unidade pedida atende: o cardapio dela nao vai,
        // para o grupo nao ler o cardapio de uma refeicao que nao vai acontecer.
        // Fica sem gravar -- se o aviso for retirado, ela ainda sai como inedita.
        boolean diaSemRu = DiaSemRu.contem(semRu, data);
        List<Peca> pecas = new ArrayList<>();
        List<Classificacao> fechadas = new ArrayList<>();
        for (Classificacao c : aEnviar) {
            List<Horario> horario = horarios.de(data, c.bloco(), campus);
            if (!Horario.todosFechados(horario)) {
                continue;
            }
            fechadas.add(c);
            omitidas.add(c.bloco().refeicao());
            if (!diaSemRu) {
                pecas.add(Peca.avulsa(Formatador.semRefeicao(c.bloco(), horario)));
            }
        }
        if (!fechadas.isEmpty()) {
            log.info("{}: sem atendimento em {}: {}", canal.nome(), data,
                    fechadas.stream().map(c -> c.bloco().refeicao()).toList());
            aEnviar = aEnviar.stream().filter(c -> !fechadas.contains(c)).toList();
        }

        Map<LocalDate, AvisoSemRu> semRuPendentes = new LinkedHashMap<>();
        int primeira = 0;
        for (DiaSemRu dia : semRu) {
            AvisoSemRu devido = dia.dia().equals(data) ? AvisoSemRu.HOJE : AvisoSemRu.ANTES;
            AvisoSemRu feito = estado.avisoSemRu(dia.dia(), canal.nome());
            if (feito == null || feito.compareTo(devido) < 0) {
                semRuPendentes.put(dia.dia(), devido);
                // o dia sem RU vai na frente de tudo
                pecas.add(primeira++, new Peca(PECA_SEM_RU + dia.dia(),
                        Formatador.semRu(dia, data)));
            }
        }

        if (aEnviar.isEmpty()) {
            // dia sem RU dispensa o "sem cardapio": o grupo ja sabe por que
            if (resultado.blocos().isEmpty() && !silencioso && !diaSemRu) {
                pecas.add(Peca.avulsa(Formatador.semCardapio()));
                log.info("{}: nada publicado para {}; avisando no grupo", canal.nome(), data);
            }
            if (pecas.isEmpty()) {
                log.info("{}: nada a enviar para {} ({} omitida(s))", canal.nome(), data,
                        omitidas.size());
                return new Envio(Set.of(), Set.of(), omitidas, Set.of());
            }
        }

        boolean alguemMudou = aEnviar.stream().anyMatch(c -> c.situacao() == Situacao.MUDOU);
        if (!aEnviar.isEmpty()) {
            pecas.addAll(montar(aEnviar, cardapio.origemDe(data), alguemMudou,
                    bloco -> horarios.de(data, bloco, campus)));
        }

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
                gravar(data, canal, porRefeicao, semRuPendentes, e.confirmadas());
            } catch (RuntimeException falhaAoGravar) {
                // a recusa do canal e a causa primeira: ela sobe, e a falha de
                // gravacao vai junto para nao se perder
                e.addSuppressed(falhaAoGravar);
            }
            throw e;
        }

        gravar(data, canal, porRefeicao, semRuPendentes, confirmadas);
        log.info("{}: enviado para {}: {} refeicao(oes), {} dia(s) sem RU{}", canal.nome(), data,
                aEnviar.size(), semRuPendentes.size(), alguemMudou ? " (cardapio alterado)" : "");

        Set<String> enviadas = aEnviar.stream()
                .map(c -> c.bloco().refeicao())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> alteradas = aEnviar.stream()
                .filter(c -> c.situacao() == Situacao.MUDOU)
                .map(c -> c.bloco().refeicao())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Envio(enviadas, alteradas, omitidas, semRuPendentes.keySet());
    }

    private void gravar(LocalDate data, Canal canal, Map<String, Classificacao> porRefeicao,
            Map<LocalDate, AvisoSemRu> semRuPendentes, List<String> confirmadas) {
        List<Classificacao> gravaveis = confirmadas.stream()
                .map(porRefeicao::get)
                .filter(c -> c != null)
                .toList();
        Map<LocalDate, AvisoSemRu> semRuAvisados = new LinkedHashMap<>();
        semRuPendentes.forEach((dia, aviso) -> {
            if (confirmadas.contains(PECA_SEM_RU + dia)) {
                semRuAvisados.put(dia, aviso);
            }
        });
        estado.registrar(data, canal.nome(), gravaveis);
        estado.registrarSemRu(canal.nome(), semRuAvisados);
    }

    static List<Peca> montar(List<Classificacao> blocos, String origem, boolean aviso,
            Function<BlocoRefeicao, List<Horario>> horarioDe) {
        List<Peca> pecas = new ArrayList<>();
        if (aviso) {
            pecas.add(Peca.avulsa(Formatador.AVISO_MUDANCA));
        }
        for (Classificacao c : blocos) {
            pecas.add(new Peca(c.refeicaoNormalizada(), Formatador.mensagem(c.bloco(),
                    horarioDe.apply(c.bloco()))));
        }
        pecas.add(Peca.avulsa(Formatador.linkDaOrigem(origem)));
        return List.copyOf(pecas);
    }
}
