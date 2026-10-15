package com.pedidos360.email.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /**
     * Prueba local sin Azure: AUTH_BYPASS=true (via .env/docker-compose) permite
     * llamar a la API sin JWT. Por defecto false => autenticación completa.
     */
    @Value("${app.auth.bypass:false}")
    private boolean bypass;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS: permite llamadas desde un navegador (otro origen)
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());

        if (bypass) {
            // SOLO PRUEBA LOCAL: acceso abierto, sin JWT de Azure.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        }

        return http.build();
    }

    /**
     * JWT multi-emisor: valida tokens de Microsoft Entra ID (issuer-uri) y,
     * si app.auth.cognito-issuer viene configurado, tambien de Amazon Cognito
     * (cuentas propias creadas en la tienda). El claim "iss" decide a que
     * decoder se delega; cualquier otro emisor se rechaza.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String emisorMicrosoft,
            @Value("${app.auth.cognito-issuer:}") String emisorCognito) {
        return new MultiIssuerJwtDecoder(emisorMicrosoft, emisorCognito);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:4200",
            "http://localhost",
            "http://127.0.0.1*",
            "https://44.200.146.153",  // frontend servido por la IP (solo HTTP)
            "https://n27vhcx1n0.execute-api.us-east-1.amazonaws.com" // frontend HTTPS (API Gateway)
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
