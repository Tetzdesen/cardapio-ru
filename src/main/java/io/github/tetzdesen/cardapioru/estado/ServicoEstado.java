package io.github.tetzdesen.cardapioru.estado;

import static io.github.tetzdesen.cardapioru.coleta.Normalizador.normalizar;

import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.notificacao.Formatador;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decide, refeicao a refeicao e canal a canal, o que e novidade. Porte da logica
 * de estado do ru_bot.py, sobre o mesmo arquivo JSON que ele gravava.
 *
 * <p>Cada canal tem a sua entrada: uma refeicao que chegou ao Telegram e falhou
 * no WhatsApp volta so para o WhatsApp no disparo seguinte.
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

    /** Ate onde um dia sem RU ja foi avisado ao canal. */
    public enum AvisoSemRu {
        /** Avisado com antecedencia; ainda falta o lembrete no proprio dia. */
        ANTES,
        /** Avisado no proprio dia: nao volta mais. */
        HOJE
    }

    private static final Logger log = LoggerFactory.getLogger(ServicoEstado.class);

    private final ArquivoDeEstado arquivo;

    public ServicoEstado(ArquivoDeEstado arquivo) {
        this.arquivo = arquivo;
    }

    /**
     * Le o estado da data no canal e classifica cada bloco.
     *
     * <p>Entrada que o sistema nao consegue interpretar e descartada em vez de
     * provocar erro -- a refeicao correspondente volta a ser tratada como
     * inedita. Arquivo ausente ou ilegivel vale como estado vazio: e o caso
     * normal da primeira execucao, e nao ha o que duplicar no grupo.
     */
    public List<Classificacao> classificar(LocalDate data, String canal,
            List<BlocoRefeicao> blocos) {
        Map<String, String> conhecidas = arquivo.ler();

        List<Classificacao> classificadas = new ArrayList<>();
        for (BlocoRefeicao bloco : blocos) {
            String refeicao = normalizar(bloco.refeicao());
            String assinatura = Formatador.assinatura(bloco);
            String anterior = conhecidas.get(ArquivoDeEstado.chave(data, refeicao, canal));

            Situacao situacao;
            if (anterior == null) {
                situacao = Situacao.INEDITA;
            } else if (anterior.equals(assinatura)) {
                situacao = Situacao.INALTERADA;
                log.debug("{}|{} sem mudanca desde o ultimo envio ao {}", data, refeicao,
                        canal);
            } else {
                situacao = Situacao.MUDOU;
            }
            classificadas.add(new Classificacao(bloco, refeicao, assinatura, situacao));
        }
        return List.copyOf(classificadas);
    }

    /**
     * Grava as refeicoes que o canal confirmou, e so elas. A poda por retencao
     * vai junto -- o arquivo so e reescrito quando algo foi enviado, entao nao ha
     * gravacao sem novidade.
     *
     * @throws EstadoIndisponivel se o arquivo nao puder ser gravado
     */
    public void registrar(LocalDate data, String canal, Collection<Classificacao> confirmadas) {
        registrar(data, canal, confirmadas, Map.of());
    }

    /**
     * Grava as refeicoes e os avisos de dia sem RU que o canal confirmou, numa
     * gravacao so. Em duas, a falha da primeira deixaria o dia sem RU sem
     * registro, e ele voltaria ao grupo a cada anuncio.
     *
     * @throws EstadoIndisponivel se o arquivo nao puder ser gravado
     */
    public void registrar(LocalDate data, String canal, Collection<Classificacao> confirmadas,
            Map<LocalDate, AvisoSemRu> semRu) {
        if (confirmadas.isEmpty() && semRu.isEmpty()) {
            return;
        }
        Map<String, String> estado = arquivo.ler();
        for (Classificacao c : confirmadas) {
            estado.put(ArquivoDeEstado.chave(data, c.refeicaoNormalizada(), canal),
                    c.assinatura());
        }
        semRu.forEach((dia, aviso) -> estado.put(
                ArquivoDeEstado.chave(dia, ArquivoDeEstado.SEM_RU, canal),
                aviso.name().toLowerCase(Locale.ROOT)));
        arquivo.gravar(estado);
        log.info("estado: {} refeicao(oes) e {} dia(s) sem RU gravado(s) em {}",
                confirmadas.size(), semRu.size(), arquivo.caminho());
    }

    /** Ate onde o dia sem RU ja foi avisado ao canal; nulo se nunca foi. */
    public AvisoSemRu avisoSemRu(LocalDate dia, String canal) {
        String valor = arquivo.ler().get(ArquivoDeEstado.chave(dia, ArquivoDeEstado.SEM_RU, canal));
        if (valor == null) {
            return null;
        }
        try {
            return AvisoSemRu.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null; // valor que nao entende: avisa de novo
        }
    }
}
