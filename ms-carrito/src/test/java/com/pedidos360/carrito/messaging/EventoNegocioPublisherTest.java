package com.pedidos360.carrito.messaging;

import com.pedidos360.carrito.config.EventosMessagingProperties;
import com.pedidos360.carrito.model.EstadoPago;
import com.pedidos360.carrito.model.EstadoPedido;
import com.pedidos360.carrito.model.ItemPedido;
import com.pedidos360.carrito.model.MetodoPago;
import com.pedidos360.carrito.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del productor: comprueba que cada evento de negocio se publica en
 * el EXCHANGE configurado con la routing key de su binding (y no directamente
 * en una cola), que el payload trae el detalle del pedido (pedidoId, items,
 * estado, total, fecha) que consumen ms-productos y ms-ordenes, y que un
 * fallo de RabbitMQ nunca rompe la compra.
 */
class EventoNegocioPublisherTest {

    private static final String EXCHANGE = "pedidos360.eventos.topic";

    private RabbitTemplate rabbitTemplate;
    private EventoNegocioPublisher publisher;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);

        EventosMessagingProperties rutas = new EventosMessagingProperties(
                EXCHANGE,
                Map.of(
                        "pedido-registrado", "pedido.registrado",
                        "pago-aprobado", "pago.aprobado",
                        "pago-rechazado", "pago.rechazado",
                        "cambio-estado", "pedido.cambio_estado"));

        publisher = new EventoNegocioPublisher(rabbitTemplate, rutas);

        pedido = new Pedido();
        pedido.setId(7L);
        pedido.setUsuarioId("usuario-azure-42");
        pedido.setTotal(1500.0);
        pedido.setEstado(EstadoPedido.PENDIENTE_PAGO);
        pedido.setEstadoPago(EstadoPago.PENDIENTE);
        pedido.setMetodoPago(MetodoPago.TARJETA_CREDITO);
        pedido.setFecha(LocalDateTime.of(2026, 10, 8, 12, 30));
        pedido.setItems(List.of(
                new ItemPedido(1L, "Monitor 27\"", 249.99, 2),
                new ItemPedido(2L, "Teclado mecanico", 50.02, 1)));
    }

    @Test
    void publicarPedidoRegistrado_deberiaUsarElExchangeYSuRoutingKey() {
        publisher.publicarPedidoRegistrado("cliente@pedidos360.cl", "Ana", pedido);

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE), eq("pedido.registrado"), any(EventoNegocio.class));
    }

    @Test
    void publicarPagoAprobado_deberiaUsarElExchangeYSuRoutingKey() {
        publisher.publicarPagoAprobado("cliente@pedidos360.cl", "Ana", pedido);

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE), eq("pago.aprobado"), any(EventoNegocio.class));
    }

    @Test
    void publicarPagoRechazado_deberiaUsarElExchangeYSuRoutingKey() {
        publisher.publicarPagoRechazado("cliente@pedidos360.cl", "Ana", pedido);

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE), eq("pago.rechazado"), any(EventoNegocio.class));
    }

    @Test
    void publicarCambioEstado_deberiaUsarElExchangeYSuRoutingKey() {
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        publisher.publicarCambioEstado("cliente@pedidos360.cl", "Ana", pedido);

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE), eq("pedido.cambio_estado"), any(EventoNegocio.class));
    }

    @Test
    void elPayload_deberiaIncluirPedidoIdEstadoTotalFechaEItems() {
        publisher.publicarPedidoRegistrado("cliente@pedidos360.cl", "Ana", pedido);

        ArgumentCaptor<EventoNegocio> evento = ArgumentCaptor.forClass(EventoNegocio.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("pedido.registrado"), evento.capture());

        EventoNegocio publicado = evento.getValue();
        assertThat(publicado.pedidoId()).isEqualTo(7L);
        assertThat(publicado.usuarioId()).isEqualTo("usuario-azure-42");
        assertThat(publicado.estado()).isEqualTo("PENDIENTE_PAGO");
        assertThat(publicado.metodoPago()).isEqualTo("TARJETA_CREDITO");
        assertThat(publicado.total()).isEqualTo(1500.0);
        assertThat(publicado.fecha()).isEqualTo("2026-10-08T12:30");
        assertThat(publicado.tipo()).isEqualTo("PEDIDO_REGISTRADO");
        assertThat(publicado.destino()).isEqualTo("cliente@pedidos360.cl");
        assertThat(publicado.asunto()).contains("Pedido #7");
        assertThat(publicado.cuerpo()).isNotBlank();

        // Lo que consumen ms-productos (stock) y ms-ordenes (orden formal).
        assertThat(publicado.items()).hasSize(2);
        assertThat(publicado.items().get(0).productoId()).isEqualTo(1L);
        assertThat(publicado.items().get(0).nombre()).contains("Monitor");
        assertThat(publicado.items().get(0).cantidad()).isEqualTo(2);
        assertThat(publicado.items().get(0).precioUnitario()).isEqualTo(249.99);
    }

    @Test
    void noDeberiaPublicar_siElTokenNoIncluyeElEmailDeDestino() {
        publisher.publicarPagoAprobado("   ", "Ana", pedido);

        verify(rabbitTemplate, never()).convertAndSend(
                eq(EXCHANGE), eq("pago.aprobado"), any(EventoNegocio.class));
    }

    @Test
    void noDeberiaPublicar_siFaltaLaRoutingKeyConfigurada() {
        EventosMessagingProperties sinRutas = new EventosMessagingProperties(EXCHANGE, Map.of());
        EventoNegocioPublisher sinBindings = new EventoNegocioPublisher(rabbitTemplate, sinRutas);

        sinBindings.publicarPagoAprobado("cliente@pedidos360.cl", "Ana", pedido);

        verify(rabbitTemplate, never()).convertAndSend(
                eq(EXCHANGE), eq("pago.aprobado"), any(EventoNegocio.class));
    }

    @Test
    void noDeberiaLanzarExcepcion_siRabbitMqEstaCaido() {
        doThrow(new AmqpConnectException("Connection refused", new java.net.ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(
                        eq(EXCHANGE), eq("pago.aprobado"), any(EventoNegocio.class));

        assertThatCode(() -> publisher.publicarPagoAprobado("cliente@pedidos360.cl", "Ana", pedido))
                .doesNotThrowAnyException();

        // La compra no debe fallar: el error queda en el log.
        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE), eq("pago.aprobado"), any(EventoNegocio.class));
    }
}
