package com.pedidos360.despachos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Nombres y parametros de las rutas de mensajeria RabbitMQ del dominio de
 * DESPACHOS.
 *
 * TODOS los nombres (exchange, cola, DLQ y routing keys) viven en
 * application.yml bajo la clave pedidos360.messaging.despachos: no hay
 * nombres sueltos en el codigo. Esta clase solo los expone tipados para que
 * {@link RabbitConfig} declare los beans de Queue, Exchange y Binding y para
 * que {@code DespachoListener} registre la cola en la que esta escuchando.
 *
 * El exchange es del sistema (lo declara ms-carrito, el productor) y este
 * consumidor usa exactamente el mismo nombre, de forma que no hay
 * PRECONDITION_FAILED en RabbitMQ.
 */
@ConfigurationProperties(prefix = "pedidos360.messaging.despachos")
public record DespachosMessagingProperties(

        /** Exchange directo por el que entran los eventos de negocio. */
        String exchange,

        /** Cola que consume este microservicio. */
        String queue,

        /** Cola de respaldo (dead letter) para mensajes no entregados. */
        String dlq,

        /** Tipo de evento -> routing key. Claves en minusculas y guiones. */
        Map<String, String> routingKeys,

        /** Politica de reintento del consumidor (ACK explicito). */
        Consumer consumer
) {

    /**
     * Politica de reintentos del consumidor.
     *
     * @param maxAttempts  intentos maximos antes de mandar el mensaje a la DLQ
     * @param retryDelayMs espera inicial entre intentos (backoff exponencial)
     */
    public record Consumer(int maxAttempts, long retryDelayMs) {

        /** Evita bucles infinitos si la configuracion viene vacia o negativa. */
        public int intentosEfectivos() {
            return maxAttempts < 1 ? 1 : maxAttempts;
        }
    }

    /**
     * Routing key de un tipo de evento (p. ej. PAGO_APROBADO).
     *
     * @param tipo constante de tipo de evento, en mayusculas y con guiones
     *             bajos (CAMBIO_ESTADO)
     * @return la routing key configurada o null si el tipo no esta definido
     */
    public String routingKeyDe(String tipo) {
        if (tipo == null || routingKeys == null) {
            return null;
        }
        return routingKeys.get(tipo.toLowerCase().replace('_', '-'));
    }
}
