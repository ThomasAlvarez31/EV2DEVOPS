package com.pedidos360.email.config;

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
 * fallar el arranque en lugar de dejar rutas sin binding.
 */
class RabbitConfigTest {

    private static final String[] CONFIGURACION_COMPLETA = {
            "pedidos360.messaging.email.exchange=pedidos360.eventos.topic",
            "pedidos360.messaging.email.queue=pedidos360.emails",
            "pedidos360.messaging.email.dlq=pedidos360.emails.dlq",
            "pedidos360.messaging.email.routing-keys.pedido-registrado=pedido.registrado",
            "pedidos360.messaging.email.routing-keys.pago-aprobado=pago.aprobado",
            "pedidos360.messaging.email.routing-keys.pago-rechazado=pago.rechazado",
            "pedidos360.messaging.email.routing-keys.cambio-estado=pedido.cambio_estado",
            "pedidos360.messaging.email.consumer.max-attempts=3",
            "pedidos360.messaging.email.consumer.retry-delay-ms=2000"
    };

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(RabbitConfig.class);

    @Test
    void deberiaDeclararColaExchangeYUnBindingPorRuta() {
        contexto.withPropertyValues(CONFIGURACION_COMPLETA).run(ctx -> {
            assertThat(ctx).hasNotFailed();

            // Exchange centralizado
            DirectExchange exchange = ctx.getBean(DirectExchange.class);
            assertThat(exchange.getName()).isEqualTo("pedidos360.eventos.topic");
            assertThat(exchange.isDurable()).isTrue();

            // Cola principal con dead-letter hacia la DLQ configurada
            Queue cola = ctx.getBean("emailQueue", Queue.class);
            assertThat(cola.getName()).isEqualTo("pedidos360.emails");
            assertThat(cola.getArguments())
                    .containsEntry("x-dead-letter-exchange", "")
                    .containsEntry("x-dead-letter-routing-key", "pedidos360.emails.dlq");

            // DLQ declarada con el nombre centralizado
            Queue dlq = ctx.getBean("emailDeadLetterQueue", Queue.class);
            assertThat(dlq.getName()).isEqualTo("pedidos360.emails.dlq");

            // Un binding por cada ruta de mensajeria
            Map<String, Binding> bindings = ctx.getBeansOfType(Binding.class);
            assertThat(bindings).hasSize(4);
            Set<String> routingKeys = bindings.values().stream()
                    .map(Binding::getRoutingKey)
                    .collect(Collectors.toSet());
            assertThat(routingKeys).containsExactlyInAnyOrder(
                    "pedido.registrado", "pago.aprobado", "pago.rechazado", "pedido.cambio_estado");

            bindings.values().forEach(b -> {
                assertThat(b.getDestination()).isEqualTo("pedidos360.emails");
                assertThat(b.getExchange()).isEqualTo("pedidos360.eventos.topic");
                assertThat(b.isDestinationQueue()).isTrue();
            });
        });
    }

    @Test
    void deberiaLeerLosNombresDesdeApplicationYml() {
        // Simula el application.yml real del microservicio.
        new ApplicationContextRunner()
                .withUserConfiguration(RabbitConfig.class)
                .withPropertyValues(
                        "pedidos360.messaging.email.exchange=otro.exchange",
                        "pedidos360.messaging.email.queue=otra.cola",
                        "pedidos360.messaging.email.dlq=otra.cola.dlq",
                        "pedidos360.messaging.email.routing-keys.pedido-registrado=rk.personalizado",
                        "pedidos360.messaging.email.routing-keys.pago-aprobado=pago.aprobado",
                        "pedidos360.messaging.email.routing-keys.pago-rechazado=pago.rechazado",
                        "pedidos360.messaging.email.routing-keys.cambio-estado=cambio.estado")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(DirectExchange.class).getName()).isEqualTo("otro.exchange");
                    assertThat(ctx.getBean("emailQueue", Queue.class).getName()).isEqualTo("otra.cola");
                    assertThat(ctx.getBeansOfType(Binding.class).values())
                            .extracting(Binding::getRoutingKey)
                            .contains("rk.personalizado");
                });
    }

    @Test
    void noDeberiaArrancarSiFaltanLosNombresDeLasRutas() {
        contexto.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(mensajesDelArbol(ctx.getStartupFailure()))
                    .contains("pedidos360.messaging.email.exchange");
        });
    }

    @Test
    void noDeberiaArrancarSiFaltaAlgunaRoutingKey() {
        contexto.withPropertyValues(
                "pedidos360.messaging.email.exchange=pedidos360.eventos.topic",
                "pedidos360.messaging.email.queue=pedidos360.emails",
                "pedidos360.messaging.email.dlq=pedidos360.emails.dlq",
                "pedidos360.messaging.email.routing-keys.pedido-registrado=pedido.registrado",
                "pedidos360.messaging.email.routing-keys.pago-aprobado=pago.aprobado",
                "pedidos360.messaging.email.routing-keys.pago-rechazado=pago.rechazado")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(mensajesDelArbol(ctx.getStartupFailure()))
                            .contains("CAMBIO_ESTADO");
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
