package io.github.tetzdesen.cardapioru.config;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * O servidor do RU nao envia o certificado intermediario da RNP/ICPEdu, entao a
 * verificacao padrao falha com "unable to get local issuer certificate". Aqui
 * montamos um truststore que soma esse intermediario as ancoras da JVM.
 *
 * <p>Nao existe caminho para desligar a verificacao -- nem por configuracao. A
 * opcao precisa nao existir para nao ser usada as pressas numa manha em que o
 * cardapio nao chegou.
 */
public final class ConfiancaTls {

    public static final String RECURSO_INTERMEDIARIO = "/rnp-icpedu.pem";

    public static final String COMO_OBTER = """
            O servidor da UFES nao envia o certificado intermediario da RNP/ICPEdu.
            Baixe-o e coloque-o em src/main/resources/rnp-icpedu.pem:
              curl -sO http://secure.globalsign.com/cacert/rnpicpedugr46ovtlsca2025.crt
              openssl x509 -inform DER -in rnpicpedugr46ovtlsca2025.crt -out rnp-icpedu.pem""";

    private static final Logger log = LoggerFactory.getLogger(ConfiancaTls.class);

    private ConfiancaTls() {
    }

    /** True quando o PEM do intermediario esta empacotado com a aplicacao. */
    public static boolean intermediarioDisponivel() {
        try (InputStream in = ConfiancaTls.class.getResourceAsStream(RECURSO_INTERMEDIARIO)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ancoras padrao da JVM + o intermediario da RNP/ICPEdu, quando disponivel.
     * Sem o intermediario devolve o truststore padrao: o handshake com a UFES
     * vai falhar, e {@link #COMO_OBTER} explica o conserto.
     */
    public static KeyStore truststore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        int padrao = 0;
        for (X509Certificate ancora : ancorasPadrao()) {
            ks.setCertificateEntry("padrao-" + padrao++, ancora);
        }

        int extra = 0;
        for (X509Certificate cert : intermediarios()) {
            ks.setCertificateEntry("rnp-icpedu-" + extra++, cert);
        }
        log.debug("truststore montado: {} ancoras padrao + {} intermediario(s)", padrao, extra);
        return ks;
    }

    public static SSLContext contexto() throws Exception {
        TrustManagerFactory fabrica =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrica.init(truststore());

        SSLContext contexto = SSLContext.getInstance("TLS");
        contexto.init(null, fabrica.getTrustManagers(), null);
        return contexto;
    }

    static List<X509Certificate> intermediarios() throws Exception {
        try (InputStream in = ConfiancaTls.class.getResourceAsStream(RECURSO_INTERMEDIARIO)) {
            if (in == null) {
                return List.of();
            }
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> lidos =
                    fabrica.generateCertificates(in);
            List<X509Certificate> certs = new ArrayList<>();
            for (java.security.cert.Certificate c : lidos) {
                certs.add((X509Certificate) c);
            }
            return certs;
        }
    }

    static List<X509Certificate> ancorasPadrao() throws Exception {
        TrustManagerFactory fabrica =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrica.init((KeyStore) null);
        for (javax.net.ssl.TrustManager tm : fabrica.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                return List.of(x509.getAcceptedIssuers());
            }
        }
        return List.of();
    }
}
