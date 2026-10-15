package com.pedidos360.identidad.config;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jwt.JWTParser;

/**
 * Decoder que acepta JWT de MAS de un emisor de identidad (IDaaS):
 *
 *   1. Microsoft Entra ID  -> spring.security.oauth2.resourceserver.jwt.issuer-uri
 *   2. Amazon Cognito      -> app.auth.cognito-issuer (opcional; vacio = solo Azure)
 *
 * El claim "iss" se lee del payload SIN verificar (solo para elegir el decoder)
 * y el token se delega en un {@link NimbusJwtDecoder} dedicado a ese emisor,
 * que SI verifica firma, fechas y issuer. Si el issuer no esta en la lista
 * blanca, el token se rechaza (401) antes de cualquier otra cosa.
 *
 * Asi la tienda permite iniciar sesion con una cuenta de Microsoft O con una
 * cuenta creada en Cognito, sin duplicar la configuracion de seguridad.
 */
public final class MultiIssuerJwtDecoder implements JwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(MultiIssuerJwtDecoder.class);

    /** Emisor configurado -> emisor canónico (con el que se valida el claim iss). */
    private final Map<String, String> emisoresPermitidos = new LinkedHashMap<>();

    /** Emisor canónico -> decoder perezoso (el discovery se hace una sola vez). */
    private final Map<String, JwtDecoder> decodersPorEmisor = new ConcurrentHashMap<>();

    public MultiIssuerJwtDecoder(String emisorMicrosoft, String emisorCognito) {
        registrar(emisorMicrosoft, "Microsoft Entra ID");
        registrar(emisorCognito, "Amazon Cognito");
        if (emisoresPermitidos.size() == 1) {
            log.info("JWT multi-emisor activo: solo {}", emisoresPermitidos.values());
        } else if (emisoresPermitidos.size() > 1) {
            log.info("JWT multi-emisor activo: {}", emisoresPermitidos.values());
        }
    }

    private void registrar(String emisor, String nombre) {
        if (emisor == null || emisor.isBlank()) {
            return;
        }
        String canonico = sinBarraFinal(emisor.trim());
        emisoresPermitidos.put(canonico.toLowerCase(), canonico);
        log.info("Emisor de identidad aceptado ({}): {}", nombre, canonico);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = issuerSinVerificar(token);
        String permitido = issuer == null ? null : emisoresPermitidos.get(sinBarraFinal(issuer).toLowerCase());

        if (permitido == null) {
            throw new JwtException("Emisor de identidad no admitido: " + issuer);
        }
        return decoderDe(permitido).decode(token);
    }

    /** Crea (una sola vez) el decoder del emisor: discovery OIDC + JWKS. */
    private JwtDecoder decoderDe(String emisor) {
        return decodersPorEmisor.computeIfAbsent(emisor, issuer -> {
            try {
                return NimbusJwtDecoder.withIssuerLocation(issuer).build();
            } catch (Exception ex) {
                throw new JwtException("No se pudo configurar el emisor " + issuer + ": " + ex.getMessage(), ex);
            }
        });
    }

    /** Lee el "iss" del payload sin validar firma (solo para enrutar). */
    private String issuerSinVerificar(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException | RuntimeException ex) {
            throw new JwtException("Token ilegible: no se pudo leer el issuer");
        }
    }

    private static String sinBarraFinal(String valor) {
        String limpio = valor;
        while (limpio.endsWith("/")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        return limpio;
    }
}
