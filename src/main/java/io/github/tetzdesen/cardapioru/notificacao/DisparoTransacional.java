package io.github.tetzdesen.cardapioru.notificacao;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Classificacao;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Situacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O miolo do disparo: coleta, decide o que e novidade, envia e grava.
 *
 * <p>A ordem e <b>enviar, depois gravar</b>, e grava-se apenas o que o Telegram
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
    private final ClienteTelegram telegram;

    public DisparoTransacional(ServicoCardapio cardapio, ServicoEstado estado,
            ClienteTelegram telegram) {
        this.cardapio = cardapio;
        this.estado = estado;
        this.telegram = telegram;
    }

    public DisparoResposta executar(LocalDate data, List<String> refeicoes, String campus,
            boolean silencioso) {
        ServicoCardapio.Resultado resultado = cardapio.consultar(data, refeicoes, campus);
        List<Classificacao> classificadas = estado.classificar(data, resultado.blocos());

        List<String> omitidas = classificadas.stream()
                .filter(c -> !c.precisaEnviar())
                .map(c -> c.bloco().refeicao())
                .toList();
        List<Classificacao> aEnviar = classificadas.stream()
                .filter(Classificacao::precisaEnviar)
                .toList();

        if (aEnviar.isEmpty()) {
            if (resultado.blocos().isEmpty() && !silencioso) {
                telegram.enviar(List.of(Peca.avulsa(Formatador.semCardapio())));
                log.info("nada publicado para {}; avisado no grupo", data);
            } else {
                log.info("nada a enviar para {} ({} omitida(s))", data, omitidas.size());
            }
            return new DisparoResposta(data, resultado.publicado(), List.of(), List.of(),
                    omitidas, resultado.avisos());
        }

        boolean alguemMudou = aEnviar.stream().anyMatch(c -> c.situacao() == Situacao.MUDOU);
        List<Peca> pecas = montar(aEnviar, cardapio.origemDe(data), alguemMudou);

        // Por refeicao normalizada: e nesse nome que o Telegram devolve o que
        // confirmou, e e ele que vira chave no arquivo de estado.
        Map<String, Classificacao> porRefeicao = new LinkedHashMap<>();
        for (Classificacao c : aEnviar) {
            porRefeicao.put(c.refeicaoNormalizada(), c);
        }

        List<String> confirmadas;
        try {
            confirmadas = telegram.enviar(pecas);
        } catch (ClienteTelegram.FalhaDeEnvio e) {
            // o que o Telegram chegou a aceitar antes de falhar fica gravado, para
            // nao voltar ao grupo no disparo seguinte; a falha continua subindo
            try {
                gravar(data, porRefeicao, e.confirmadas());
            } catch (RuntimeException falhaAoGravar) {
                // a recusa do Telegram e a causa primeira: ela sobe, e a falha de
                // gravacao vai junto para nao se perder
                e.addSuppressed(falhaAoGravar);
            }
            throw e;
        }

        gravar(data, porRefeicao, confirmadas);

        List<String> enviadas = aEnviar.stream().map(c -> c.bloco().refeicao()).toList();
        List<String> alteradas = aEnviar.stream()
                .filter(c -> c.situacao() == Situacao.MUDOU)
                .map(c -> c.bloco().refeicao())
                .toList();
        log.info("enviado para {}: {} refeicao(oes){}", data, confirmadas.size(),
                alguemMudou ? " (cardapio alterado)" : "");

        return new DisparoResposta(data, resultado.publicado(), enviadas, alteradas, omitidas,
                resultado.avisos());
    }

    private void gravar(LocalDate data, Map<String, Classificacao> porRefeicao,
            List<String> confirmadas) {
        List<Classificacao> gravaveis = confirmadas.stream()
                .map(porRefeicao::get)
                .filter(c -> c != null)
                .toList();
        estado.registrar(data, gravaveis);
    }

    static List<Peca> montar(List<Classificacao> blocos, String origem, boolean aviso) {
        List<Peca> pecas = new ArrayList<>();
        if (aviso) {
            pecas.add(Peca.avulsa(Formatador.AVISO_MUDANCA));
        }
        for (Classificacao c : blocos) {
            pecas.add(new Peca(c.refeicaoNormalizada(), Formatador.bloco(c.bloco())));
        }
        pecas.add(Peca.avulsa(Formatador.linkDaOrigem(origem)));
        return List.copyOf(pecas);
    }
}
