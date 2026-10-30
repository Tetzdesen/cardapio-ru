package io.github.tetzdesen.cardapioru.servico;

import io.github.tetzdesen.cardapioru.coleta.ColetorDaUfes;
import io.github.tetzdesen.cardapioru.coleta.LeitorDeAvisos;
import io.github.tetzdesen.cardapioru.coleta.QuadroDeHorarios;
import io.github.tetzdesen.cardapioru.coleta.QuadroDeHorarios.Excecao;
import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.DiaSemRu;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O horario de cada refeicao anunciada: o regular, corrigido pelos avisos de
 * horario especial da pagina inicial.
 *
 * <p>O horario enfeita o anuncio, nao o decide. Site fora do ar ou aviso
 * ilegivel nao pode segurar o cardapio: na falha, vale o horario regular e a
 * falha so vai para o log.
 */
@Service
public class ServicoHorario {

    private static final Logger log = LoggerFactory.getLogger(ServicoHorario.class);

    /** Aviso de horario sai com dias de antecedencia; nao vale baixar a cada disparo. */
    static final Duration VALIDADE = Duration.ofMinutes(30);

    /** Quantos dias adiante avisar que nao vai ter RU. */
    static final int ANTECEDENCIA = 7;

    private final ColetorDaUfes coletor;
    private final CardapioProperties props;

    private List<Excecao> excecoes = List.of();
    private Long lidoEm;

    public ServicoHorario(ColetorDaUfes coletor, CardapioProperties props) {
        this.coletor = coletor;
        this.props = props;
    }

    /** O horario das unidades do bloco que passam no {@code campus} pedido. */
    public List<Horario> de(LocalDate dia, BlocoRefeicao bloco, String campus) {
        return QuadroDeHorarios.de(dia, bloco.refeicao(), bloco.campus(), campus, excecoes());
    }

    /**
     * Os dias, de {@code desde} ate {@link #ANTECEDENCIA} adiante, em que alguma
     * unidade do {@code campus} nao vai ter RU, segundo os avisos.
     */
    public List<DiaSemRu> diasSemRu(LocalDate desde, String campus) {
        List<Excecao> lidas = excecoes();
        List<DiaSemRu> dias = new ArrayList<>();
        for (int i = 0; i <= ANTECEDENCIA; i++) {
            LocalDate dia = desde.plusDays(i);
            List<String> fechadas = QuadroDeHorarios.semAtendimento(dia, campus, lidas);
            if (!fechadas.isEmpty()) {
                dias.add(new DiaSemRu(dia, fechadas));
            }
        }
        return List.copyOf(dias);
    }

    /** Descarta os avisos lidos. Usado pelos testes. */
    public synchronized void limparCache() {
        lidoEm = null;
    }

    private synchronized List<Excecao> excecoes() {
        if (lidoEm != null
                && Duration.ofNanos(System.nanoTime() - lidoEm).compareTo(VALIDADE) < 0) {
            return excecoes;
        }
        List<Excecao> lidas = lerAvisos();
        if (lidas != null) {
            excecoes = lidas;
            lidoEm = System.nanoTime();
        }
        // na falha, valem as ultimas lidas, e a proxima consulta tenta de novo
        return excecoes;
    }

    private List<Excecao> lerAvisos() {
        int ano = LocalDate.now(props.fuso()).getYear();
        List<Excecao> lidas = new ArrayList<>();
        try {
            String inicial = coletor.baixarPagina(coletor.urlInicial());
            if (inicial == null) {
                return List.of();
            }
            // da mais antiga para a mais nova: na sobreposicao, o aviso novo vence
            List<String> noticias = new ArrayList<>(
                    LeitorDeAvisos.noticias(inicial, coletor.urlInicial()));
            Collections.reverse(noticias);
            for (String url : noticias) {
                String aviso = coletor.baixarPagina(url);
                if (aviso != null) {
                    List<Excecao> doAviso = LeitorDeAvisos.excecoes(aviso, ano);
                    log.debug("{}: {} excecao(oes) de horario", url, doAviso.size());
                    lidas.addAll(doAviso);
                }
            }
        } catch (RuntimeException e) {
            log.warn("nao foi possivel ler os avisos de horario; valem os ultimos lidos: {}",
                    e.getMessage());
            return null;
        }
        return List.copyOf(lidas);
    }
}
