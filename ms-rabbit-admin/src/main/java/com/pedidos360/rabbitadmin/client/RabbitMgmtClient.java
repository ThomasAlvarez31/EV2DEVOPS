package com.pedidos360.rabbitadmin.client;

import com.pedidos360.rabbitadmin.error.BrokerNoDisponibleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de gestion de RabbitMQ (plugin management, puerto 15672).
 *
 * Se usa EXCLUSIVAMENTE para listar colas, exchanges y bindings: la API AMQP
 * (5672) no tiene comandos de listado, solo de declaracion/borrado. Crear y
 * eliminar siempre pasa por {@code RabbitAdminService} -> AmqpAdmin.
 *
 * Si el plugin de gestion no esta activo o no responde, el listado devuelve
 * 503 con un mensaje claro; crear/borrar sigue funcionando igual.
 */
@Component
public class RabbitMgmtClient {

    private final RestClient restClient;

    public RabbitMgmtClient(
            @Value("${pedidos360.rabbit.management.base-url}") String baseUrl,
            @Value("${pedidos360.rabbit.management.username}") String username,
            @Value("${pedidos360.rabbit.management.password}") String password,
            @Value("${pedidos360.rabbit.management.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${pedidos360.rabbit.management.read-timeout-ms}") long readTimeoutMs) {

        String credenciales = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(sacarBarraFinal(baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credenciales)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(factory)
                .build();
    }

    /**
     * GET {@code /api/<recurso>} de la API de gestion.
     *
     * @param recurso "queues", "exchanges" o "bindings"
     * @return la lista de elementos crudos (mapas JSON) devueltos por RabbitMQ
     * @throws BrokerNoDisponibleException si la API de gestion no responde
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listar(String recurso) {
        try {
            List<?> elementos = restClient.get()
                    .uri("/api/{recurso}", recurso)
                    .retrieve()
                    .body(List.class);
            if (elementos == null) {
                return List.of();
            }
            return (List<Map<String, Object>>) elementos;
        } catch (BrokerNoDisponibleException e) {
            throw e;
        } catch (Exception e) {
            throw new BrokerNoDisponibleException(
                    "No se pudo consultar la API de gestion de RabbitMQ (" + recurso + "). "
                            + "Verifique que el plugin management este activo y que RABBITMQ_MGMT_URL "
                            + "y las credenciales RABBITMQ_MGMT_* sean correctas.", e);
        }
    }

    private static String sacarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
