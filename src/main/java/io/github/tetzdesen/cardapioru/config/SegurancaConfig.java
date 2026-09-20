package io.github.tetzdesen.cardapioru.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Consulta e saude abertas; disparo protegido. Nada mais. */
@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    private static final Logger log = LoggerFactory.getLogger(SegurancaConfig.class);

    @Bean
    public SecurityFilterChain corrente(HttpSecurity http, ApiProperties props) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/notificacoes")
                            .hasAuthority(FiltroDeToken.AUTORIDADE)
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (pedido, resposta, erro) -> resposta.sendError(401)))
                // 401 tambem quando ha token mas ele nao confere: para quem chama,
                // credencial ausente e credencial errada sao o mesmo problema
                .anonymous(a -> a.disable());

        if (props.protegida()) {
            http.addFilterBefore(new FiltroDeToken(props.token()),
                    UsernamePasswordAuthenticationFilter.class);
        } else {
            log.warn("API_TOKEN nao definido: o disparo vai recusar toda requisicao");
        }
        return http.build();
    }
}
