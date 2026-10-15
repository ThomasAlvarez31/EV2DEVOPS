package com.pedidos360.productos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clase UNICA de configuracion de mensajeria RabbitMQ del microservicio
 * (lado consumidor de stock).
 *
 * Declara los beans de Queue, Exchange y Binding, uno por cada ruta de
 * mensajeria del dominio de stock. Los nombres NO estan escritos en el
 * codigo: se leen de application.yml (pedidos360.messaging.stock) a traves de
 * {@link StockMessagingProperties}, de forma que la centralizacion es unica y
 * no hay valores sueltos en las clases de negocio.
 *
 * Declarar la cola y los bindings tambien del lado consumidor hace que el
 * sistema funcione en cualquier orden de arranque.
 *
 * Esta clase NO contiene logica de negocio: solo configuracion.
 */
@Configuration
@EnableConfigurationProperties(StockMessagingProperties.class)
public class RabbitConfig {

    private final StockMessagingProperties rutas;

    public RabbitConfig(StockMessagingProperties rutas) {
        if (rutas == null || rutas.exchange() == null || rutas.exchange().isBlank()
                || rutas.queue() == null || rutas.queue().isBlank()
                || rutas.dlq() == null || rutas.dlq().isBlank()) {
            throw new IllegalStateException(
                    "Falta la configuracion de mensajeria: defina pedidos360.messaging.stock.exchange, "
                            + ".queue y .dlq en application.yml antes de arrancar.");
        }
        this.rutas = rutas;
    }

    /**
     * Routing key de una ruta. Si falta en application.yml el sistema no
     * arranca: mejor fallar al iniciar que escuchar una cola sin binding.
     */
    private String routingKey(String tipo) {
        String routingKey = rutas.routingKeyDe(tipo);
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta la routing key del tipo " + tipo
                            + " en pedidos360.messaging.stock.routing-keys (application.yml).");
        }
        return routingKey;
    }

    // ------------------------------------------------------------------
    //  QUEUES
    // ------------------------------------------------------------------

    /** Cola de eventos de stock (cola principal del dominio). */
    @Bean
    Queue stockQueue() {
        return QueueBuilder.durable(rutas.queue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", rutas.dlq())
                .build();
    }

    /** Cola de respaldo (DLQ): recibe los eventos que no se pudieron aplicar. */
    @Bean
    Queue stockDeadLetterQueue() {
        return QueueBuilder.durable(rutas.dlq()).build();
    }

    // ------------------------------------------------------------------
    //  EXCHANGE
    // ------------------------------------------------------------------

    /**
     * Exchange directo de eventos de negocio. Es el MISMO exchange que declara
     * el productor (ms-carrito) con los mismos argumentos.
     */
    @Bean
    DirectExchange eventosExchange() {
        return new DirectExchange(rutas.exchange(), true, false);
    }

    // ------------------------------------------------------------------
    //  BINDINGS (una por ruta de mensajeria)
    // ------------------------------------------------------------------

    /** Ruta: pedido registrado -> cola de stock (descuento asincrono). */
    @Bean
    Binding bindingPedidoRegistrado() {
        return BindingBuilder.bind(stockQueue())
                .to(eventosExchange())
                .with(routingKey("PEDIDO_REGISTRADO"));
    }

    /** Ruta: pago rechazado -> cola de stock (reposicion del inventario). */
    @Bean
    Binding bindingPagoRechazado() {
        return BindingBuilder.bind(stockQueue())
                .to(eventosExchange())
                .with(routingKey("PAGO_RECHAZADO"));
    }

    // ------------------------------------------------------------------
    //  SERIALIZACION
    // ------------------------------------------------------------------

    /**
     * Los eventos llegan como JSON y se parsean con el DTO propio del dominio
     * (EventoStock): solo se intercambia el formato, nunca clases compartidas.
     */
    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
