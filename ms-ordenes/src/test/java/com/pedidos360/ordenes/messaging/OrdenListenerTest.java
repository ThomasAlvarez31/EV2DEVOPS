package com.pedidos360.ordenes.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.ordenes.config.OrdenesMessagingProperties;
import com.pedidos360.ordenes.service.OrdenService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas del consumidor de ordenes con ACK explicito.
 *
 * Se comprueban las rutas de confirmacion:
 *   - orden creada o actualizada -> basicAck
 *   - mensaje ilegible o incompleto -> basicNack(requeue=false) sin reintentos
 *   - fallo de base de datos -> reintentos y luego basicNack hacia la DLQ
 */
class OrdenListenerTest {

    private static final long TAG = 42L;

    private OrdenService ordenService;
    private Channel channel;
    private OrdenListener ordenListener;

    @BeforeEach
    void setUp() {
        ordenService = mock(OrdenService.class);
        channel = mock(Channel.class);

        OrdenesMessagingProperties rutas = new OrdenesMessagingProperties(
                "pedidos360.eventos.topic",
                "pedidos360.ordenes",
                "pedidos360.ordenes.dlq",
                Map.of(
                        "pedido-registrado", "pedido.registrado",
                        "pago-aprobado", "pago.aprobado",
                        "pago-rechazado", "pago.rechazado",
                        "cambio-estado", "pedido.cambio_estado"),
                new OrdenesMessagingProperties.Consumer(3, 0));

        ordenListener = new OrdenListener(new ObjectMapper(), ordenService, rutas);
    }

    private Message mensajeConJson(String json) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    private static final String JSON_REGISTRADO = """
            {"tipo":"PEDIDO_REGISTRADO",
             "pedidoId":7,
             "usuarioId":"usuario-42",
             "estado":"PENDIENTE_PAGO",
             "metodoPago":"TARJETA_CREDITO",
             "total":749.97,
             "fecha":"2026-10-08T12:30:00",
             "items":[{"productoId":1,"nombre":"Monitor 27\\"","cantidad":3,"precioUnitario":249.99}],
             "destino":"cliente@pedidos360.cl",
             "asunto":"Pedidos360 - Pedido #7 registrado",
             "cuerpo":"Tu pedido fue registrado."}
            """;

    // ------------------------------------------------------------------
    //  RUTA 1: orden creada -> ACK
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaRegistrarLaOrden_yConfirmarConAck() throws Exception {
        when(ordenService.aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class)))
                .thenReturn(OrdenService.Resultado.CREADA);

        ordenListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        verify(ordenService).aplicar(org.mockito.ArgumentMatchers.argThat(
                evento -> "PEDIDO_REGISTRADO".equals(evento.tipo())
                        && evento.pedidoId() == 7L
                        && "usuario-42".equals(evento.usuarioId())
                        && evento.items().size() == 1));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    @Test
    void recibirEvento_deberiaHacerAck_siLaOrdenYaEstabaRegistrada() throws Exception {
        when(ordenService.aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class)))
                .thenReturn(OrdenService.Resultado.REPETIDA);

        ordenListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        // Evento repetido: ya se resolvio, no tiene sentido reentregarlo.
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 2: mensaje ilegible -> NACK directo a la DLQ (sin reintentos)
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaNackSinRequeue_siElJsonEsInvalido() throws Exception {
        ordenListener.recibirEvento(mensajeConJson("{esto no es json"), channel);

        verifyNoInteractions(ordenService);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirEvento_deberiaNackSinRequeue_siElEventoNoTieneItems() throws Exception {
        when(ordenService.aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class)))
                .thenThrow(new IllegalArgumentException("El evento del pedido 7 no incluye items"));

        ordenListener.recibirEvento(mensajeConJson(
                "{\"tipo\":\"PEDIDO_REGISTRADO\",\"pedidoId\":7,\"items\":[]}"), channel);

        // Evento incompleto: irrecuperable, directo a la DLQ sin reintentos.
        verify(ordenService, times(1)).aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class));
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 3: fallo de base de datos -> reintentos y luego NACK a la DLQ
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaReintentar_yNackHaciaLaDlq_siLaBaseDeDatosFallaSiempre() throws Exception {
        when(ordenService.aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class)))
                .thenThrow(new RuntimeException("H2 caido"));

        ordenListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        // 3 intentos (pedidos360.messaging.ordenes.consumer.max-attempts)
        verify(ordenService, times(3)).aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class));
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirEvento_deberiaRecuperarse_yHacerAck_siUnReintentoFunciona() throws Exception {
        when(ordenService.aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class)))
                .thenThrow(new RuntimeException("H2 caido"))
                .thenReturn(OrdenService.Resultado.CREADA);

        ordenListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        verify(ordenService, times(2)).aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  Fallo del propio canal: no debe dejar la excepcion escapar
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_noDebeLanzarExcepcion_siElCanalFallaAlConfirmar() throws Exception {
        when(ordenService.aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class)))
                .thenReturn(OrdenService.Resultado.CREADA);
        doThrow(new java.io.IOException("canal cerrado")).when(channel).basicAck(TAG, false);

        ordenListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        verify(ordenService).aplicar(org.mockito.ArgumentMatchers.any(EventoOrden.class));
    }
}
