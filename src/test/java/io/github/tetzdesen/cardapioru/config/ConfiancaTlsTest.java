package io.github.tetzdesen.cardapioru.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiancaTlsTest {

    @Test
    void truststoreTemOIntermediarioDaRnpEUmaAncoraPublica() throws Exception {
        KeyStore ks = ConfiancaTls.truststore();

        List<String> assuntos = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.getCertificate(alias) instanceof X509Certificate cert) {
                assuntos.add(cert.getSubjectX500Principal().getName());
            }
        }

        assertThat(assuntos).anyMatch(s -> s.contains("RNP") || s.contains("ICPEdu"));
        assertThat(ConfiancaTls.ancorasPadrao()).isNotEmpty();
        assertThat(assuntos).hasSizeGreaterThan(ConfiancaTls.intermediarios().size());
    }

    @Test
    void intermediarioEstaEmpacotadoComAAplicacao() throws Exception {
        assertThat(ConfiancaTls.intermediarioDisponivel()).isTrue();
        assertThat(ConfiancaTls.intermediarios()).isNotEmpty();
    }

    @Test
    void contextoTlsEConstruivel() throws Exception {
        assertThat(ConfiancaTls.contexto()).isNotNull();
    }

    @Test
    void mensagemDeAusenciaEnsinaOConserto() {
        assertThat(ConfiancaTls.COMO_OBTER)
                .contains("rnp-icpedu.pem")
                .contains("openssl x509");
    }
}
