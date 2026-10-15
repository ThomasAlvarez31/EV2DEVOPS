package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.client.RabbitMgmtClient;
import com.pedidos360.rabbitadmin.dto.CrearBindingRequest;
import com.pedidos360.rabbitadmin.dto.CrearColaRequest;
import com.pedidos360.rabbitadmin.dto.CrearExchangeRequest;
import com.pedidos360.rabbitadmin.error.RecursoNoEncontradoException;
import com.pedidos360.rabbitadmin.error.SolicitudInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitMgmtClient gestion;
    private RabbitAdminService servicio;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        gestion = mock(RabbitMgmtClient.class);
        servicio = new RabbitAdminService(amqpAdmin, gestion);
    }

    // ------------------------------------------------------------------
    //  COLAS
    // ------------------------------------------------------------------

    @Test
    void crearCola_deberiaDeclararColaDurable() {
        when(amqpAdmin.getQueueInfo("pedidos360.emails"))
                .thenReturn(new QueueInformation("pedidos360.emails", 0, 0));

        var resultado = servicio.crearCola(
                new CrearColaRequest("pedidos360.emails", true, null));

        assertEquals("pedidos360.emails", resultado.name());
        assertEquals(0, resultado.messages());
        verify(amqpAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void crearCola_deberiaRechazarNombreVacio() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearCola(new CrearColaRequest("   ", true, null)));

        assertTrue(e.getMessage().contains("obligatorio"));
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_deberiaRechazarPrefijoReservado() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearCola(new CrearColaRequest("amq.direct", true, null)));

        assertTrue(e.getMessage().contains("reservado"));
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_deberiaRechazarArgumentoConClaveInvalida() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearCola(new CrearColaRequest("cola-ok", true,
                        Map.of("dead-letter", "otra"))));

        assertTrue(e.getMessage().contains("x-<nombre>"));
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_deberiaRechazarTtlNoNumerico() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearCola(new CrearColaRequest("cola-ok", true,
                        Map.of("x-message-ttl", "muchos"))));

        assertTrue(e.getMessage().contains("x-message-ttl"));
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_deberiaRechazarTtlNegativo() {
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearCola(new CrearColaRequest("cola-ok", true,
                        Map.of("x-message-ttl", -5))));
    }

    @Test
    void obtenerCola_deberiaLanzar404_siNoExiste() {
        when(amqpAdmin.getQueueInfo("no-existe")).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.obtenerCola("no-existe"));
    }

    @Test
    void eliminarCola_deberiaLanzar404_siNoExiste() {
        when(amqpAdmin.getQueueInfo("fantasma")).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.eliminarCola("fantasma", false, false));
        verify(amqpAdmin, never()).deleteQueue(anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void eliminarCola_deberiaBorrar_siExiste() {
        when(amqpAdmin.getQueueInfo("pedidos360.emails"))
                .thenReturn(new QueueInformation("pedidos360.emails", 3, 1));

        servicio.eliminarCola("pedidos360.emails", true, false);

        verify(amqpAdmin).deleteQueue("pedidos360.emails", true, false);
    }

    @Test
    void listarColas_deberiaMapearLaRespuestaDeLaApiDeGestion() {
        when(gestion.listar("queues")).thenReturn(List.of(
                Map.of("name", "pedidos360.emails", "messages", 2, "consumers", 1,
                        "durable", true, "autoDelete", false)));

        var colas = servicio.listarColas();

        assertEquals(1, colas.size());
        assertEquals("pedidos360.emails", colas.get(0).name());
        assertEquals(2, colas.get(0).messages());
        assertEquals(1, colas.get(0).consumers());
        assertTrue(colas.get(0).durable());
    }

    // ------------------------------------------------------------------
    //  EXCHANGES
    // ------------------------------------------------------------------

    @Test
    void crearExchange_deberiaDeclararDirectExchange() {
        var resultado = servicio.crearExchange(
                new CrearExchangeRequest("pedidos360.eventos.topic", "direct", true, false, null));

        assertEquals("pedidos360.eventos.topic", resultado.name());
        assertEquals("direct", resultado.type());
        verify(amqpAdmin).declareExchange(any(DirectExchange.class));
    }

    @Test
    void crearExchange_deberiaDeclararTopicExchange_conAutoDelete() {
        servicio.crearExchange(
                new CrearExchangeRequest("topico", "topic", null, true, null));

        verify(amqpAdmin).declareExchange(any(TopicExchange.class));
    }

    @Test
    void crearExchange_deberiaRechazarTipoInvalido() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearExchange(
                        new CrearExchangeRequest("cualquiera", "rabbit", true, false, null)));

        assertTrue(e.getMessage().contains("direct, topic, fanout o headers"));
        verify(amqpAdmin, never()).declareExchange(any());
    }

    @Test
    void eliminarExchange_deberiaLanzar404_siElBrokerDiceQueNoSeElimino() {
        when(amqpAdmin.deleteExchange("fantasma")).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.eliminarExchange("fantasma"));
    }

    @Test
    void eliminarExchange_deberiaBorrar_siExiste() {
        when(amqpAdmin.deleteExchange("pedidos360.eventos.topic")).thenReturn(true);

        servicio.eliminarExchange("pedidos360.eventos.topic");

        verify(amqpAdmin).deleteExchange("pedidos360.eventos.topic");
    }

    // ------------------------------------------------------------------
    //  BINDINGS
    // ------------------------------------------------------------------

    @Test
    void crearBinding_deberiaDeclararEnlaceColaExchangeRoutingKey() {
        var resultado = servicio.crearBinding(
                new CrearBindingRequest("pedidos360.emails", "pedidos360.eventos.topic",
                        "pedido.registrado", null));

        assertEquals("pedido.registrado", resultado.routingKey());
        assertEquals("pedidos360.emails", resultado.queue());
        assertEquals("pedidos360.eventos.topic", resultado.exchange());

        org.mockito.ArgumentCaptor<Binding> captor =
                org.mockito.ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(captor.capture());
        assertEquals("pedidos360.emails", captor.getValue().getDestination());
        assertEquals("pedidos360.eventos.topic", captor.getValue().getExchange());
        assertEquals("pedido.registrado", captor.getValue().getRoutingKey());
        assertEquals(Binding.DestinationType.QUEUE, captor.getValue().getDestinationType());
    }

    @Test
    void crearBinding_deberiaRechazarRoutingKeyVacia() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearBinding(
                        new CrearBindingRequest("cola", "exchange", "", null)));

        assertTrue(e.getMessage().contains("routing key"));
        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void eliminarBinding_deberiaTraducirNotFoundA404() {
        org.mockito.Mockito.doThrow(new RuntimeException("channel error; 404 - NOT_FOUND"))
                .when(amqpAdmin).removeBinding(any(Binding.class));

        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.eliminarBinding("pedidos360.emails",
                        "pedidos360.eventos.topic", "pedido.registrado"));

        assertTrue(e.getMessage().contains("pedido.registrado"));
    }

    @Test
    void eliminarBinding_deberiaEliminar_siExiste() {
        servicio.eliminarBinding("pedidos360.emails", "pedidos360.eventos.topic",
                "pedido.registrado");

        org.mockito.ArgumentCaptor<Binding> captor =
                org.mockito.ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).removeBinding(captor.capture());
        assertEquals("pedido.registrado", captor.getValue().getRoutingKey());
    }

    @Test
    void listarBindings_deberiaOmitirElExchangePorDefecto() {
        when(gestion.listar("bindings")).thenReturn(List.of(
                Map.of("source", "", "destination", "pedidos360.emails",
                        "destination_type", "queue", "routing_key", ""),
                Map.of("source", "pedidos360.eventos.topic", "destination", "pedidos360.emails",
                        "destination_type", "queue", "routing_key", "pedido.registrado"),
                Map.of("source", "pedidos360.eventos.topic", "destination", "otro-exchange",
                        "destination_type", "exchange", "routing_key", "x")));

        var bindings = servicio.listarBindings();

        assertEquals(1, bindings.size());
        assertEquals("pedidos360.eventos.topic", bindings.get(0).exchange());
        assertEquals("pedido.registrado", bindings.get(0).routingKey());
    }

    // ------------------------------------------------------------------
    //  VALIDACION DE NOMBRES
    // ------------------------------------------------------------------

    @Test
    void nombresConEspaciosDeberianFallar() {
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crearCola(new CrearColaRequest("cola con espacio", true, null)));
    }

    @Test
    void crearCola_deberiaPermitirArgumentosNulosYDurablePorDefecto() {
        when(amqpAdmin.getQueueInfo("simple"))
                .thenReturn(new QueueInformation("simple", 0, 0));

        var cola = servicio.crearCola(new CrearColaRequest("simple", null, null));

        assertEquals("simple", cola.name());
        verify(amqpAdmin).declareQueue(any(Queue.class));
    }
}
