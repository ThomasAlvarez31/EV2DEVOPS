package com.pedidos360.carrito.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente HTTP hacia ms-pagos.
 *
 * El pago lo decide y lo registra el microservicio de pagos (que es el dueño
 * de esa información); el carrito solo aplica el resultado sobre el pedido
 * (aprobado -> CONFIRMADO, rechazado -> CANCELADO + restaura carrito).
 *
 * Configuracion: app.pagos.url (variable de entorno PAGOS_SERVICE_URL).
 *   local:  http://localhost:8085/pagos
 *   docker: http://ms-pagos:8085/pagos
 *
 * El token del usuario se reenvía para que ms-pagos pueda validar el JWT
 * cuando AUTH_BYPASS=false (producción).
 */
@Component
public class PagoClient {

    private static final Logger log = LoggerFactory.getLogger(PagoClient.class);

    private final RestTemplate rest;
    private final String baseUrl;

    public PagoClient(@Value("${app.pagos.url}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.rest = new RestTemplate(factory);
    }

    /**
     * Registra el pago del pedido en ms-pagos y devuelve el resultado.
     *
     * @param token JWT del usuario (reenviado a ms-pagos; puede ser null en pruebas).
     * @throws IllegalStateException si ms-pagos no responde o rechaza la solicitud.
     */
    public ResultadoPago registrarPago(String token, Long pedidoId, String usuarioId,
                                        Double monto, String metodoPago, boolean simularRechazo) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("Debe indicar el pedido a pagar");
        }

        Map<String, Object> cuerpo = Map.of(
                "pedidoId", pedidoId,
                "usuarioId", usuarioId == null ? "" : usuarioId,
                "monto", monto == null ? 0.0 : monto,
                "metodoPago", metodoPago == null ? "" : metodoPago,
                "simularRechazo", simularRechazo);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (token != null && !token.isBlank()) {
                headers.set("Authorization", "Bearer " + token);
            }

            ResultadoPago resultado = rest.exchange(baseUrl, HttpMethod.POST,
                    new HttpEntity<>(cuerpo, headers), ResultadoPago.class).getBody();

            if (resultado == null) {
                throw new IllegalStateException("ms-pagos no devolvió un resultado de pago");
            }
            return resultado;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // ms-pagos contestó con un error de negocio (400/401/404): se
            // conserva su mensaje para que el usuario vea el motivo real.
            String mensaje = e.getResponseBodyAsString();
            if (mensaje == null || mensaje.isBlank()) {
                mensaje = "ms-pagos rechazó la operación (" + e.getStatusCode() + ")";
            }
            log.warn("ms-pagos rechazó la solicitud ({}): {}", baseUrl, mensaje);
            throw new IllegalStateException(mensaje);
        } catch (RestClientException e) {
            log.error("ms-pagos no responde ({}): {}", baseUrl, e.getMessage());
            throw new IllegalStateException(
                    "No se pudo procesar el pago en ms-pagos. Intenta nuevamente.", e);
        }
    }

    /** Subconjunto de campos que al carrito le interesa del pago. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoPago(Long id, Long pedidoId, String estado, String motivo) {

        public boolean aprobado() {
            return "APROBADO".equalsIgnoreCase(estado);
        }
    }
}
