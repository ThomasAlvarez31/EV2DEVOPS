package com.pedidos360.carrito.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Nombres de las rutas de mensajeria RabbitMQ que publica este microservicio.
 *
 * TODOS los nombres (exchange y routing keys) viven en application.yml bajo la
 * clave pedidos360.messaging.eventos. No hay nombres sueltos en el codigo:
 * esta clase solo los expone tipados para que {@link RabbitConfig} declare el
 * bean del exchange y {@code EventoNegocioPublisher} publique.
 *
 * El productor solo declara el EXCHANGE: la cola, la DLQ y los bindings de
 * cada dominio los declara el consumidor correspondiente (ms-email,
 * ms-productos, ms-ordenes y ms-despachos), que es quien es dueño de su
 * topologia. Los valores tienen que ser identicos en ambos lados para no
 * provocar PRECONDITION_FAILED en RabbitMQ.
 */
@ConfigurationProperties(prefix = "pedidos360.messaging.eventos")
public record EventosMessagingProperties(

        /** Exchange directo por el que salen todos los eventos de negocio. */
        String exchange,

        /** Tipo de evento -> routing key. Claves en minusculas y guiones. */
        Map<String, String> routingKeys
) {

    /**
     * Routing key de un tipo de evento (p. ej. PEDIDO_REGISTRADO).
     *
     * @param tipo constante de tipo de evento, en mayusculas y con guiones
     *             bajos (PEDIDO_REGISTRADO)
     * @return la routing key configurada o null si el tipo no esta definido
     */
    public String routingKeyDe(String tipo) {
        if (tipo == null || routingKeys == null) {
            return null;
        }
        return routingKeys.get(tipo.toLowerCase().replace('_', '-'));
    }
}
