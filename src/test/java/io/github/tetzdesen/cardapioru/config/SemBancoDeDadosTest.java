package io.github.tetzdesen.cardapioru.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * A aplicacao sobe sem nenhuma variavel de banco definida -- e nao sobe por
 * acaso: nao ha mais banco nenhum no classpath para ela cair em cima.
 */
@SpringBootTest
class SemBancoDeDadosTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void subiuSemNenhumaFonteDeDados() {
        assertThat(contexto.getBeanNamesForType(DataSource.class)).isEmpty();
    }

    @Test
    void oCaminhoDoEstadoEConhecidoNaPartida() {
        assertThat(contexto.getBean(EstadoProperties.class).caminho()).isNotNull();
    }
}
