package io.github.tetzdesen.cardapioru.estado;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.notificacao.Formatador;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decide, refeicao a refeicao, o que e novidade. Porte da logica de estado do
 * ru_bot.py, sobre o mesmo arquivo JSON que ele gravava.
 */
@Service
public class ServicoEstado {

    public enum Situacao {
        /** Nunca anunciada para esta data. */
        INEDITA,
        /** Ja anunciada, com conteudo diferente do anunciado. */
        MUDOU,
        /** Ja anunciada, sem alteracao: fica de fora. */
        INALTERADA
    }

    public record Classificacao(BlocoRefeicao bloco, String refeicaoNormalizada,
            String assinatura, Situacao situacao) {

        public boolean precisaEnviar() {
            return situacao != Situacao.INALTERADA;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ServicoEstado.class);

    private final ArquivoDeEstado arquivo;

    public ServicoEstado(ArquivoDeEstado arquivo) {
        this.arquivo = arquivo;
    }

    /**
     * Le o estado da data e classifica cada bloco.
     *
     * <p>Entrada que o sistema nao consegue interpretar e descartada em vez de
     * provocar erro -- a refeicao correspondente volta a ser tratada como
     * inedita. Arquivo ausente ou ilegivel vale como estado vazio: e o caso
     * normal da primeira execucao, e nao ha o que duplicar no grupo.
     */
    public List<Classificacao> classificar(LocalDate data, List<BlocoRefeicao> blocos) {
        Map<String, String> conhecidas = arquivo.ler();

        List<Classificacao> classificadas = new ArrayList<>();
        for (BlocoRefeicao bloco : blocos) {
            String refeicao = normalizar(bloco.refeicao());
            String assinatura = Formatador.assinatura(bloco);
            String anterior = conhecidas.get(ArquivoDeEstado.chave(data, refeicao));

            Situacao situacao;
            if (anterior == null) {
                situacao = Situacao.INEDITA;
            } else if (anterior.equals(assinatura)) {
                situacao = Situacao.INALTERADA;
                log.debug("{}|{} sem mudanca desde o ultimo envio", data, refeicao);
            } else {
                situacao = Situacao.MUDOU;
            }
            classificadas.add(new Classificacao(bloco, refeicao, assinatura, situacao));
        }
        return List.copyOf(classificadas);
    }

    /**
     * Grava as refeicoes que o Telegram confirmou, e so elas. A poda por retencao
     * vai junto -- o arquivo so e reescrito quando algo foi enviado, entao nao ha
     * gravacao sem novidade.
     *
     * @throws EstadoIndisponivel se o arquivo nao puder ser gravado
     */
    public void registrar(LocalDate data, Collection<Classificacao> confirmadas) {
        if (confirmadas.isEmpty()) {
            return;
        }
        Map<String, String> estado = arquivo.ler();
        for (Classificacao c : confirmadas) {
            estado.put(ArquivoDeEstado.chave(data, c.refeicaoNormalizada()), c.assinatura());
        }
        arquivo.gravar(estado);
        log.info("estado: {} entrada(s) gravada(s) em {}", confirmadas.size(), arquivo.caminho());
    }
}
