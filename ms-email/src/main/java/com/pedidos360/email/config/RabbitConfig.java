package com.pedidos360.email.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Clase UNICA de configuracion de mensajeria RabbitMQ del microservicio
 * (lado consumidor).
 *
 * Declara los beans de Queue, Exchange y Binding, uno por cada ruta de
 * mensajeria del dominio de email. Los nombres NO estan escritos en el
 * codigo: se leen de application.yml (pedidos360.messaging.email) a traves de
 * {@link EmailMessagingProperties}, de forma que la centralizacion es unica y
 * no hay valores sueltos en las clases de negocio.
 *
 * Declarar la cola y los bindings tambien del lado consumidor hace que el
 * sistema funcione en cualquier orden de arranque.
 *
 * Esta clase NO contiene logica de negocio: solo configuracion.
 */
@Configuration
@EnableConfigurationProperties(EmailMessagingProperties.class)
public class RabbitConfig {

    private final EmailMessagingProperties rutas;

    public RabbitConfig(EmailMessagingProperties rutas) {
        if (rutas == null || rutas.exchange() == null || rutas.exchange().isBlank()
                || rutas.queue() == null || rutas.queue().isBlank()
                || rutas.dlq() == null || rutas.dlq().isBlank()) {
            throw new IllegalStateException(
                    "Falta la configuracion de mensajeria: defina pedidos360.messaging.email.exchange, "
                            + ".queue y .dlq en application.yml antes de arrancar.");
        }
        this.rutas = rutas;
    }

    /**
     * Routing key de una ruta. Si falta en application.yml el sistema no
     * arranca: mejor fallar al iniciar que publicar a un exchange sin binding.
     */
    private String routingKey(String tipo) {
        String routingKey = rutas.routingKeyDe(tipo);
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta la routing key del tipo " + tipo
                            + " en pedidos360.messaging.email.routing-keys (application.yml).");
        }
        return routingKey;
    }

    // ------------------------------------------------------------------
    //  QUEUES
    // ------------------------------------------------------------------

    /** Cola de entrega de eventos de email (cola principal del dominio). */
    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(rutas.queue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", rutas.dlq())
                .build();
    }

    /** Cola de respaldo (DLQ): recibe los mensajes que no se pudieron entregar. */
    @Bean
    Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(rutas.dlq()).build();
    }

    // ------------------------------------------------------------------
    //  EXCHANGE
    // ------------------------------------------------------------------

    /**
     * Exchange directo de eventos de email. Cada evento entra con su routing
     * key y el binding correspondiente lo enlaza a la cola.
     */
    @Bean
    DirectExchange emailExchange() {
        return new DirectExchange(rutas.exchange(), true, false);
    }

    // ------------------------------------------------------------------
    //  BINDINGS (una por ruta de mensajeria)
    // ------------------------------------------------------------------

    /** Ruta: evento de pedido registrado -> cola de emails. */
    @Bean
    Binding bindingPedidoRegistrado() {
        return BindingBuilder.bind(emailQueue())
                .to(emailExchange())
                .with(routingKey("PEDIDO_REGISTRADO"));
    }

    /** Ruta: pago aprobado -> cola de emails. */
    @Bean
    Binding bindingPagoAprobado() {
        return BindingBuilder.bind(emailQueue())
                .to(emailExchange())
                .with(routingKey("PAGO_APROBADO"));
    }

    /** Ruta: pago rechazado -> cola de emails. */
    @Bean
    Binding bindingPagoRechazado() {
        return BindingBuilder.bind(emailQueue())
                .to(emailExchange())
                .with(routingKey("PAGO_RECHAZADO"));
    }

    /** Ruta: cambio de estado del pedido -> cola de emails. */
    @Bean
    Binding bindingCambioEstado() {
        return BindingBuilder.bind(emailQueue())
                .to(emailExchange())
                .with(routingKey("CAMBIO_ESTADO"));
    }
}
