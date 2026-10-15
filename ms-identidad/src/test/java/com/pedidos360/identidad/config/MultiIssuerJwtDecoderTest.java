package com.pedidos360.identidad.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Pruebas del decoder multi-emisor (Microsoft / Cognito).
 * Solo se prueban los caminos que NO requieren red: el emisor no admitido
 * y la configuracion vacia. El camino feliz (firma valida) depende del
 * discovery OIDC de Microsoft/Cognito y se valida en el despliegue real.
 */
class MultiIssuerJwtDecoderTest {

    private static final String AZURE = "https://login.microsoftonline.com/9150f81f-b0fc-436d-998d-dcb5cf883d2e/v2.0";
    private static final String COGNITO = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_PRUEBA";

    /** Payload sin firma: solo para comprobar que el issuer NO admitido se rechaza. */
    private static String tokenConIssuer(String issuer) {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"iss\":\"" + issuer + "\",\"sub\":\"abc\",\"exp\":9999999999}").getBytes());
        return header + "." + payload + ".firma";
    }

    @Test
    void debeRechazarElTokenCuandoElEmisorNoEstaEnLaListaBlanca() {
        MultiIssuerJwtDecoder decoder = new MultiIssuerJwtDecoder(AZURE, "");

        assertThatThrownBy(() -> decoder.decode(tokenConIssuer("https://emisor-ajeno.com/")))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("no admitido");
    }

    @Test
    void debeRechazarCognitoCuandoNoEstaConfigurado() {
        MultiIssuerJwtDecoder decoder = new MultiIssuerJwtDecoder(AZURE, "");

        assertThatThrownBy(() -> decoder.decode(tokenConIssuer(COGNITO)))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining(COGNITO);
    }

    @Test
    void debeRechazarCualquierTokenSiNoHayEmisoresConfigurados() {
        MultiIssuerJwtDecoder decoder = new MultiIssuerJwtDecoder("", "");

        assertThatThrownBy(() -> decoder.decode(tokenConIssuer(AZURE)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void debeRechazarUnTokenIlegible() {
        MultiIssuerJwtDecoder decoder = new MultiIssuerJwtDecoder(AZURE, COGNITO);

        assertThatThrownBy(() -> decoder.decode("esto-no-es-un-jwt"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("ilegible");
    }

    @Test
    void debeSeguirRechazandoEmisoresAjenosAunConLosDosEmisoresConfigurados() {
        MultiIssuerJwtDecoder decoder = new MultiIssuerJwtDecoder(AZURE, COGNITO);

        assertThatThrownBy(() -> decoder.decode(tokenConIssuer("https://otro-issuer.com/")))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("no admitido");
    }

    @Test
    void debeNormalizarLaBarraFinalDelIssuer() {
        MultiIssuerJwtDecoder decoder = new MultiIssuerJwtDecoder(AZURE + "/", "");

        assertThatThrownBy(() -> decoder.decode(tokenConIssuer("https://emisor-ajeno.com")))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("no admitido");
    }
}
