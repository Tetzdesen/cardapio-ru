package io.github.tetzdesen.cardapioru.cli;

import io.github.tetzdesen.cardapioru.api.DisparoResposta;
import io.github.tetzdesen.cardapioru.coleta.Datas;
import io.github.tetzdesen.cardapioru.coleta.Filtro;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.DiaSemRu;
import io.github.tetzdesen.cardapioru.dominio.Horario;
import io.github.tetzdesen.cardapioru.erro.EstadoIndisponivel;
import io.github.tetzdesen.cardapioru.erro.FalhaDeOrigem;
import io.github.tetzdesen.cardapioru.erro.ParametroInvalido;
import io.github.tetzdesen.cardapioru.notificacao.Formatador;
import io.github.tetzdesen.cardapioru.notificacao.ServicoNotificacao;
import io.github.tetzdesen.cardapioru.servico.ServicoCardapio;
import io.github.tetzdesen.cardapioru.servico.ServicoHorario;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * O disparo de uma tacada so pela linha de comando: baixa, formata, envia (ou
 * so imprime), morre. O anuncio de todo dia e do {@code Agendador}; este e o
 * caminho da conferencia e do disparo a mao.
 *
 * <p>Ele nao decide nada por conta propria: chama o mesmo
 * {@link ServicoNotificacao} que o controller chama. Duplicar a decisao aqui
 * seria abrir espaco para o CLI e o HTTP divergirem.
 *
 * <p>Sem nenhuma das nossas opcoes na linha, nao faz nada -- e assim que a
 * aplicacao sobe como servidor (perfil {@code web}) sem disparar nada.
 */
@Component
public class DisparoRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(DisparoRunner.class);

    private final ServicoNotificacao notificacao;
    private final ServicoCardapio cardapio;
    private final ServicoHorario horarios;

    private int codigo = Saida.OK;

    public DisparoRunner(ServicoNotificacao notificacao, ServicoCardapio cardapio,
            ServicoHorario horarios) {
        this.notificacao = notificacao;
        this.cardapio = cardapio;
        this.horarios = horarios;
    }

    @Override
    public int getExitCode() {
        return codigo;
    }

    @Override
    public void run(ApplicationArguments args) {
        codigo = executar(args.getSourceArgs());
    }

    /** Separado do {@code run} para o teste chamar sem subir a aplicacao. */
    int executar(String[] argv) {
        if (Argumentos.quantasNossas(argv) == 0) {
            log.debug("nenhum argumento de disparo; nada a fazer");
            return Saida.OK;
        }

        Argumentos opcoes;
        try {
            opcoes = Argumentos.ler(argv);
        } catch (ParametroInvalido e) {
            log.error("{}", e.getMessage());
            return Saida.USO;
        }

        if (opcoes.enviar() == opcoes.imprimir()) {
            log.error("informe --enviar (manda aos canais) ou --print (so imprime), "
                    + "e nao os dois");
            return Saida.USO;
        }

        try {
            LocalDate data = opcoes.data() == null
                    ? cardapio.hoje()
                    : Datas.de(opcoes.data());
            List<String> refeicoes = Filtro.refeicoesPedidas(opcoes.refeicoes());

            if (opcoes.imprimir()) {
                imprimir(data, refeicoes, opcoes.campus());
            } else {
                relatar(notificacao.disparar(data, refeicoes, opcoes.campus(),
                        opcoes.vazioSilencioso()));
            }
            return Saida.OK;
        } catch (ParametroInvalido e) {
            log.error("{}", e.getMessage());
            return Saida.USO;
        } catch (FalhaDeOrigem e) {
            log.error("{}: {}", e.causa(), e.getMessage());
            return switch (e.causa()) {
                case TELEGRAM_RECUSOU, WHATSAPP_RECUSOU -> Saida.ENVIO;
                case ESTRUTURA_NAO_RECONHECIDA -> Saida.ESTRUTURA;
                case ORIGEM_INACESSIVEL, CONFIANCA_TLS -> Saida.ORIGEM;
            };
        } catch (EstadoIndisponivel e) {
            log.error("{}", e.getMessage());
            return Saida.ESTADO;
        }
    }

    /**
     * Conferencia manual: imprime e pronto. Nao passa pelo disparo, entao nao
     * envia nem encosta no arquivo de estado.
     */
    private void imprimir(LocalDate data, List<String> refeicoes, String campus) {
        List<DiaSemRu> semRu = horarios.diasSemRu(data, campus);
        boolean diaSemRu = DiaSemRu.contem(semRu, data);
        ServicoCardapio.Resultado resultado =
                cardapio.consultar(data, refeicoes, campus, diaSemRu);

        List<String> pecas = new ArrayList<>();
        for (DiaSemRu dia : semRu) {
            pecas.add(Formatador.semRu(dia, data));
        }
        if (resultado.blocos().isEmpty()) {
            if (!diaSemRu) {
                pecas.add(Formatador.semCardapio());
            }
        } else {
            for (BlocoRefeicao bloco : resultado.blocos()) {
                List<Horario> horario = horarios.de(data, bloco, campus);
                if (!Horario.todosFechados(horario)) {
                    pecas.add(Formatador.mensagem(bloco, horario));
                } else if (!diaSemRu) {
                    pecas.add(Formatador.semRefeicao(bloco, horario));
                }
            }
        }
        pecas.add(Formatador.linkDaOrigem(resultado.origem()));

        // o cardapio sai no stdout e o log no stderr, para '--print > arquivo'
        // servir -- mesma divisao do ru_bot.py
        System.out.println(Formatador.semMarcacao(String.join("\n\n", pecas).strip()));
    }

    private void relatar(DisparoResposta r) {
        log.info("data={} publicado={} enviadas={} alteradas={} omitidas={} semRu={}",
                r.data(), r.publicado(), r.enviadas(), r.alteradas(), r.omitidas(), r.semRu());
        for (String aviso : r.avisos()) {
            log.warn("{}", aviso);
        }
    }
}
