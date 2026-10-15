package com.pedidos360.carrito.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de la configuracion de mensajeria del lado productor: comprueba que
 * los nombres centralizados en application.yml se convierten en el bean del
 * exchange (las colas y bindings los declara cada consumidor) y que una
 * configuracion incompleta hace fallar el arranque en lugar de publicar por
 * rutas inexistentes.
 */
class RabbitConfigTest {

    private static final String[] CONFIGURACION_COMPLETA = {
            "pedidos360.messaging.eventos.exchange=pedidos360.eventos.topic",
            "pedidos360.messaging.eventos.routing-keys.pedido-registrado=pedido.registrado",
            "pedidos360.messaging.eventos.routing-keys.pago-aprobado=pago.aprobado",
            "pedidos360.messaging.eventos.routing-keys.pago-rechazado=pago.rechazado",
            "pedidos360.messaging.eventos.routing-keys.cambio-estado=pedido.cambio_estado"
    };

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(RabbitConfig.class);

    @Test
    void deberiaDeclararElExchangeDeEventos() {
        contexto.withPropertyValues(CONFIGURACION_COMPLETA).run(ctx -> {
            assertThat(ctx).hasNotFailed();

            DirectExchange exchange = ctx.getBean(DirectExchange.class);
            assertThat(exchange.getName()).isEqualTo("pedidos360.eventos.topic");

            assertThat(ctx).hasSingleBean(Jackson2JsonMessageConverter.class);
        });
    }

    @Test
    void noDeberiaArrancarSiFaltaElExchange() {
        contexto.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(mensajesDelArbol(ctx.getStartupFailure()))
                    .contains("pedidos360.messaging.eventos.exchange");
        });
    }

    @Test
    void noDeberiaArrancarSiFaltaUnaRoutingKey() {
        String[] sinCambioEstado = {
                "pedidos360.messaging.eventos.exchange=pedidos360.eventos.topic",
                "pedidos360.messaging.eventos.routing-keys.pedido-registrado=pedido.registrado",
                "pedidos360.messaging.eventos.routing-keys.pago-aprobado=pago.aprobado",
                "pedidos360.messaging.eventos.routing-keys.pago-rechazado=pago.rechazado"
        };

        contexto.withPropertyValues(sinCambioEstado).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(mensajesDelArbol(ctx.getStartupFailure()))
                    .contains("CAMBIO_ESTADO")
                    .contains("pedidos360.messaging.eventos.routing-keys");
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
