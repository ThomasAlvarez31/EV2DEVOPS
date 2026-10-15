package com.pedidos360.carrito.config;

import com.pedidos360.carrito.messaging.EventoNegocioPublisher;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clase UNICA de configuracion de mensajeria RabbitMQ del microservicio
 * (lado productor).
 *
 * El productor es el dueño del EXCHANGE: lo declara aqui, con la configuracion
 * centralizada en application.yml (pedidos360.messaging.eventos). Las colas,
 * las DLQ y los bindings NO se declaran aqui porque pertenecen a cada
 * consumidor:
 *
 *   pedidos360.emails     -> ms-email      (correo Electronico)
 *   pedidos360.stock      -> ms-productos  (descuento de stock)
 *   pedidos360.ordenes    -> ms-ordenes    (orden formal de compra)
 *   pedidos360.despachos  -> ms-despachos  (despacho del pedido)
 *
 * Cada consumidor declara su topologia con EXACTAMENTE los mismos valores de
 * application.yml, de forma que no hay duplicados ni PRECONDITION_FAILED.
 *
 * Esta clase NO contiene logica de negocio: solo configuracion.
 */
@Configuration
@EnableConfigurationProperties(EventosMessagingProperties.class)
public class RabbitConfig {

    /**
     * Tipos de evento que SIEMPRE deben tener routing key: si falta uno, el
     * sistema no arranca en lugar de publicar un evento que nadie escucha.
     */
    private static final String[] RUTAS_OBLIGATORIAS = {
            EventoNegocioPublisher.TIPO_PEDIDO_REGISTRADO,
            EventoNegocioPublisher.TIPO_PAGO_APROBADO,
            EventoNegocioPublisher.TIPO_PAGO_RECHAZADO,
            EventoNegocioPublisher.TIPO_CAMBIO_ESTADO
    };

    private final EventosMessagingProperties rutas;

    public RabbitConfig(EventosMessagingProperties rutas) {
        if (rutas == null || rutas.exchange() == null || rutas.exchange().isBlank()) {
            throw new IllegalStateException(
                    "Falta la configuracion de mensajeria: defina "
                            + "pedidos360.messaging.eventos.exchange en application.yml antes de arrancar.");
        }
        for (String tipo : RUTAS_OBLIGATORIAS) {
            String routingKey = rutas.routingKeyDe(tipo);
            if (routingKey == null || routingKey.isBlank()) {
                throw new IllegalStateException(
                        "Falta la routing key del tipo " + tipo
                                + " en pedidos360.messaging.eventos.routing-keys (application.yml).");
            }
        }
        this.rutas = rutas;
    }

    // ------------------------------------------------------------------
    //  EXCHANGE
    // ------------------------------------------------------------------

    /**
     * Exchange directo de eventos de negocio. Unico punto de entrada de los
     * eventos: cada consumidor los recibe segun su propio binding
     * (pedido.registrado, pago.aprobado, pago.rechazado, pedido.cambio_estado).
     */
    @Bean
    DirectExchange eventosExchange() {
        return new DirectExchange(rutas.exchange(), true, false);
    }

    // ------------------------------------------------------------------
    //  SERIALIZACION
    // ------------------------------------------------------------------

    /**
     * Serializa los eventos como JSON. Cada consumidor hace un parseo
     * directo del cuerpo con su propio DTO: solo se intercambia el formato
     * JSON, nunca clases compartidas.
     */
    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
