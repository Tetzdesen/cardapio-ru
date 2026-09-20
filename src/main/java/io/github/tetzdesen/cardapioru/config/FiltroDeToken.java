package io.github.tetzdesen.cardapioru.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * O chamador e um curl num workflow com um segredo. HMAC do corpo, OAuth ou mTLS
 * seriam cerimonia; o que nao e opcional e a comparacao em tempo constante e o
 * token nunca aparecer em log.
 */
public class FiltroDeToken extends OncePerRequestFilter {

    public static final String AUTORIDADE = "DISPARO";

    private static final Logger log = LoggerFactory.getLogger(FiltroDeToken.class);

    private final byte[] esperado;

    public FiltroDeToken(String token) {
        this.esperado = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta,
            FilterChain corrente) throws ServletException, IOException {
        String cabecalho = pedido.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            byte[] apresentado =
                    cabecalho.substring("Bearer ".length()).strip().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(esperado, apresentado)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("workflow", null,
                                List.of(new SimpleGrantedAuthority(AUTORIDADE))));
            } else {
                // sem ecoar o que foi apresentado: o log e o lugar classico de
                // vazar um segredo que a aplicacao protegeu em todo o resto
                log.warn("credencial de disparo recusada");
            }
        }
        corrente.doFilter(pedido, resposta);
    }
}
