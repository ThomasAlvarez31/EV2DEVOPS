package com.pedidos360.carrito.config;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SOLO PARA PRUEBAS LOCALES (app.auth.bypass=true).
 * Si no llega ningun token, inyecta un JWT sintetico con los claims de un
 * "Usuario Local" para que los controllers con @AuthenticationPrincipal Jwt
 * (obtenerCarrito, agregarItem, checkout, listarPedidos) funcionen sin Azure.
 */
public class LocalAuthBypassFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // OJO: AnonymousAuthenticationFilter (anterior en la cadena) ya deja una
        // autenticacion anonima, por eso NO basta comprobar == null: solo se
        // inyecta el JWT sintetico si aun no hay uno real de Azure.
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (!(actual instanceof JwtAuthenticationToken)) {
            Instant ahora = Instant.now();
            Jwt jwt = Jwt.withTokenValue("local-bypass")
                    .header("alg", "none")
                    .claim("sub", "local-user")
                    .claim("oid", "00000000-0000-0000-0000-000000000001")
                    .claim("tid", "local")
                    .claim("name", "Usuario Local")
                    .claim("preferred_username", "diegoxmegalala@gmail.com")
                    .issuedAt(ahora)
                    .expiresAt(ahora.plusSeconds(3600))
                    .build();

            JwtAuthenticationToken autenticacion =
                    new JwtAuthenticationToken(jwt, List.of(), "local-bypass");
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        }
        filterChain.doFilter(request, response);
    }
}
