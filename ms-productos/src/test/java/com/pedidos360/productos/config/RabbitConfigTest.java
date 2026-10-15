package com.pedidos360.productos.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de la configuracion de mensajeria: comprueba que los nombres
 * centralizados en application.yml se convierten en los beans de Queue,
 * Exchange y Binding (una por ruta) y que la configuracion incompleta hace
 * fallar el arranque en lugar de escuchar una cola sin binding.
 */
class RabbitConfigTest {

    private static final String[] CONFIGURACION_COMPLETA = {
            "pedidos360.messaging.stock.exchange=pedidos360.eventos.topic",
            "pedidos360.messaging.stock.queue=pedidos360.stock",
            "pedidos360.messaging.stock.dlq=pedidos360.stock.dlq",
            "pedidos360.messaging.stock.routing-keys.pedido-registrado=pedido.registrado",
            "pedidos360.messaging.stock.routing-keys.pago-rechazado=pago.rechazado",
            "pedidos360.messaging.stock.consumer.max-attempts=3",
            "pedidos360.messaging.stock.consumer.retry-delay-ms=1000"
    };

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(RabbitConfig.class);

    @Test
    void deberiaDeclararColaExchangeYUnBindingPorRutaDeStock() {
        contexto.withPropertyValues(CONFIGURACION_COMPLETA).run(ctx -> {
            assertThat(ctx).hasNotFailed();

            // Exchange del sistema: el mismo que declara el productor.
            DirectExchange exchange = ctx.getBean(DirectExchange.class);
            assertThat(exchange.getName()).isEqualTo("pedidos360.eventos.topic");
            assertThat(exchange.isDurable()).isTrue();

            // Cola principal con dead-letter hacia la DLQ configurada
            Queue cola = ctx.getBean("stockQueue", Queue.class);
            assertThat(cola.getName()).isEqualTo("pedidos360.stock");
            assertThat(cola.getArguments())
                    .containsEntry("x-dead-letter-exchange", "")
                    .containsEntry("x-dead-letter-routing-key", "pedidos360.stock.dlq");

            // DLQ declarada con el nombre centralizado
            Queue dlq = ctx.getBean("stockDeadLetterQueue", Queue.class);
            assertThat(dlq.getName()).isEqualTo("pedidos360.stock.dlq");

            // Un binding por cada ruta que consume el inventario
            Map<String, Binding> bindings = ctx.getBeansOfType(Binding.class);
            assertThat(bindings).hasSize(2);
            Set<String> routingKeys = bindings.values().stream()
                    .map(Binding::getRoutingKey)
                    .collect(Collectors.toSet());
            assertThat(routingKeys).containsExactlyInAnyOrder(
                    "pedido.registrado", "pago.rechazado");

            bindings.values().forEach(b -> {
                assertThat(b.getDestination()).isEqualTo("pedidos360.stock");
                assertThat(b.getExchange()).isEqualTo("pedidos360.eventos.topic");
                assertThat(b.isDestinationQueue()).isTrue();
            });
        });
    }

    @Test
    void deberiaLeerLosNombresDesdeApplicationYml() {
        new ApplicationContextRunner()
                .withUserConfiguration(RabbitConfig.class)
                .withPropertyValues(
                        "pedidos360.messaging.stock.exchange=otro.exchange",
                        "pedidos360.messaging.stock.queue=otra.cola",
                        "pedidos360.messaging.stock.dlq=otra.cola.dlq",
                        "pedidos360.messaging.stock.routing-keys.pedido-registrado=pedido.registrado",
                        "pedidos360.messaging.stock.routing-keys.pago-rechazado=pago.rechazado")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(DirectExchange.class).getName()).isEqualTo("otro.exchange");
                    assertThat(ctx.getBean("stockQueue", Queue.class).getName()).isEqualTo("otra.cola");
                    assertThat(ctx.getBeansOfType(Binding.class).values())
                            .extracting(Binding::getRoutingKey)
                            .contains("pedido.registrado", "pago.rechazado");
                });
    }

    @Test
    void noDeberiaArrancarSiFaltanLosNombresDeLasRutas() {
        contexto.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(mensajesDelArbol(ctx.getStartupFailure()))
                    .contains("pedidos360.messaging.stock.exchange");
        });
    }

    @Test
    void noDeberiaArrancarSiFaltaAlgunaRoutingKey() {
        contexto.withPropertyValues(
                "pedidos360.messaging.stock.exchange=pedidos360.eventos.topic",
                "pedidos360.messaging.stock.queue=pedidos360.stock",
                "pedidos360.messaging.stock.dlq=pedidos360.stock.dlq",
                "pedidos360.messaging.stock.routing-keys.pedido-registrado=pedido.registrado")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(mensajesDelArbol(ctx.getStartupFailure()))
                            .contains("PAGO_RECHAZADO")
                            .contains("pedidos360.messaging.stock.routing-keys");
                });
    }

    /** Concatena los mensajes de toda la cadena de causas del fallo. */
    private static String mensajesDelArbol(Throwable fallo) {
        StringBuilder sb = new StringBuilder();
        Throwable actual = fallo;
        int salto = 0;
        while (actual != null && salto++ < 50) {
            sb.append(actual.getMessage()).append('\n');
            if (actual.getCause() == actual) {
                break;
            }
            actual = actual.getCause();
        }
        return sb.toString();
    }
}
