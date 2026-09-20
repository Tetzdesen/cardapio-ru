package io.github.tetzdesen.cardapioru.estado;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tetzdesen.cardapioru.config.CardapioProperties;
import io.github.tetzdesen.cardapioru.config.EstadoProperties;
import io.github.tetzdesen.cardapioru.dominio.BlocoRefeicao;
import io.github.tetzdesen.cardapioru.dominio.Secao;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Classificacao;
import io.github.tetzdesen.cardapioru.estado.ServicoEstado.Situacao;
import io.github.tetzdesen.cardapioru.notificacao.Formatador;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServicoEstadoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 16);

    @TempDir
    private Path pasta;

    private ArquivoDeEstado arquivo;
    private ServicoEstado servico;

    private final CardapioProperties props = new CardapioProperties("http://exemplo", "teste",
            Duration.ofSeconds(30), 3, Duration.ofSeconds(2), Duration.ofSeconds(60),
            Duration.ofSeconds(75), 30, ZoneOffset.ofHours(-3), Duration.ofSeconds(60), 400);

    @BeforeEach
    void preparar() {
        arquivo = new ArquivoDeEstado(
                new EstadoProperties(pasta.resolve("ultimo-envio.json")), props);
        servico = new ServicoEstado(arquivo);
    }

    private static BlocoRefeicao bloco(String refeicao, String item) {
        return new BlocoRefeicao(refeicao, "Alegre", "quarta-feira",
                List.of(new Secao("Entrada", List.of(item))));
    }

    private void estadoGravado(String conteudo) throws IOException {
        Files.writeString(pasta.resolve("ultimo-envio.json"), conteudo, StandardCharsets.UTF_8);
    }

    @Test
    void refeicaoIneditaEIneditaMesmoComOutrasJaEnviadas() throws IOException {
        BlocoRefeicao almoco = bloco("Almoço", "Alface");
        BlocoRefeicao jantar = bloco("Jantar", "Rúcula");
        estadoGravado(json(Map.of("2026-09-16|almoco", Formatador.assinatura(almoco))));

        List<Classificacao> c = servico.classificar(DIA, List.of(almoco, jantar));

        assertThat(c.get(0).situacao()).isEqualTo(Situacao.INALTERADA);
        assertThat(c.get(1).situacao()).isEqualTo(Situacao.INEDITA);
    }

    @Test
    void anuncioDaManhaEReconferenciaComConteudoIdenticoNaoRendemNada() throws IOException {
        BlocoRefeicao almoco = bloco("Almoço", "Alface");
        BlocoRefeicao jantar = bloco("Jantar", "Rúcula");
        // a manha gravou desjejum, almoco e jantar; a reconferencia pede dois
        estadoGravado(json(Map.of(
                "2026-09-16|desjejum", "x",
                "2026-09-16|almoco", Formatador.assinatura(almoco),
                "2026-09-16|jantar", Formatador.assinatura(jantar))));

        List<Classificacao> c = servico.classificar(DIA, List.of(almoco, jantar));

        assertThat(c).allMatch(x -> !x.precisaEnviar());
    }

    @Test
    void soUmaRefeicaoMudou() throws IOException {
        BlocoRefeicao almoco = bloco("Almoço", "Alface");
        BlocoRefeicao jantar = bloco("Jantar", "Rúcula");
        estadoGravado(json(Map.of(
                "2026-09-16|almoco", "assinatura-antiga",
                "2026-09-16|jantar", Formatador.assinatura(jantar))));

        List<Classificacao> c = servico.classificar(DIA, List.of(almoco, jantar));

        assertThat(c.get(0).situacao()).isEqualTo(Situacao.MUDOU);
        assertThat(c.get(1).situacao()).isEqualTo(Situacao.INALTERADA);
    }

    @Test
    void entradaDeOutraDataNaoSeMisturaComADePedida() throws IOException {
        BlocoRefeicao almoco = bloco("Almoço", "Alface");
        estadoGravado(json(Map.of("2026-09-15|almoco", Formatador.assinatura(almoco))));

        List<Classificacao> c = servico.classificar(DIA, List.of(almoco));

        assertThat(c).singleElement()
                .extracting(Classificacao::situacao).isEqualTo(Situacao.INEDITA);
    }

    @Test
    void estadoVazioTornaTudoInedito() {
        List<Classificacao> c = servico.classificar(DIA,
                List.of(bloco("Almoço", "Alface"), bloco("Jantar", "Rúcula")));

        assertThat(c).allMatch(x -> x.situacao() == Situacao.INEDITA);
    }

    @Test
    void semBancoDeDadosOEstadoEhLidoEGravadoNormalmente() {
        // nenhuma variavel de banco definida em lugar nenhum: o arquivo basta
        BlocoRefeicao almoco = bloco("Almoço", "Alface");
        List<Classificacao> primeira = servico.classificar(DIA, List.of(almoco));
        servico.registrar(DIA, primeira);

        List<Classificacao> segunda = servico.classificar(DIA, List.of(almoco));

        assertThat(segunda).singleElement()
                .extracting(Classificacao::situacao).isEqualTo(Situacao.INALTERADA);
    }

    @Test
    void registrarNadaNaoCriaArquivo() {
        servico.registrar(DIA, List.of());

        assertThat(pasta.resolve("ultimo-envio.json")).doesNotExist();
    }

    private static String json(Map<String, String> entradas) {
        return ArquivoDeEstado.serializar(new java.util.TreeMap<>(entradas));
    }
}
