package io.github.tetzdesen.cardapioru.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** A API local e os testes nao podem falar com o grupo sozinhos. */
@SpringBootTest
@ActiveProfiles("web")
class AgendaDesligadaTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void semAgendaAtivaNaoHaAgendador() {
        assertThat(contexto.getBeanNamesForType(Agendador.class)).isEmpty();
    }
}
