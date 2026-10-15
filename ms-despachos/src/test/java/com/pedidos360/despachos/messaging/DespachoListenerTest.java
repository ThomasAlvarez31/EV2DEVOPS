package com.pedidos360.despachos.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.despachos.config.DespachosMessagingProperties;
import com.pedidos360.despachos.service.DespachoService;
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
 * Pruebas del consumidor de despachos con ACK explicito.
 *
 * Se comprueban las rutas de confirmacion:
 *   - despacho creado o actualizado -> basicAck
 *   - mensaje ilegible o incompleto -> basicNack(requeue=false) sin reintentos
 *   - fallo de base de datos -> reintentos y luego basicNack hacia la DLQ
 */
class DespachoListenerTest {

    private static final long TAG = 42L;

    private DespachoService despachoService;
    private Channel channel;
    private DespachoListener despachoListener;

    @BeforeEach
    void setUp() {
        despachoService = mock(DespachoService.class);
        channel = mock(Channel.class);

        DespachosMessagingProperties rutas = new DespachosMessagingProperties(
                "pedidos360.eventos.topic",
                "pedidos360.despachos",
                "pedidos360.despachos.dlq",
                Map.of(
                        "pago-aprobado", "pago.aprobado",
                        "cambio-estado", "pedido.cambio_estado"),
                new DespachosMessagingProperties.Consumer(3, 0));

        despachoListener = new DespachoListener(new ObjectMapper(), despachoService, rutas);
    }

    private Message mensajeConJson(String json) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    private static final String JSON_PAGO_APROBADO = """
            {"tipo":"PAGO_APROBADO",
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
    //  RUTA 1: despacho creado -> ACK
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaRegistrarElDespacho_yConfirmarConAck() throws Exception {
        when(despachoService.aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class)))
                .thenReturn(DespachoService.Resultado.CREADO);

        despachoListener.recibirEvento(mensajeConJson(JSON_PAGO_APROBADO), channel);

        verify(despachoService).aplicar(org.mockito.ArgumentMatchers.argThat(
                evento -> "PAGO_APROBADO".equals(evento.tipo())
                        && evento.pedidoId() == 7L
                        && "usuario-42".equals(evento.usuarioId())
                        && "PENDIENTE_PAGO".equals(evento.estado())));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    @Test
    void recibirEvento_deberiaHacerAck_siElDespachoYaEstabaRegistrada() throws Exception {
        when(despachoService.aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class)))
                .thenReturn(DespachoService.Resultado.REPETIDO);

        despachoListener.recibirEvento(mensajeConJson(JSON_PAGO_APROBADO), channel);

        // Evento repetido: ya se resolvio, no tiene sentido reentregarlo.
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 2: mensaje ilegible -> NACK directo a la DLQ (sin reintentos)
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaNackSinRequeue_siElJsonEsInvalido() throws Exception {
        despachoListener.recibirEvento(mensajeConJson("{esto no es json"), channel);

        verifyNoInteractions(despachoService);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirEvento_deberiaNackSinRequeue_siElPagoNoTieneUsuarioId() throws Exception {
        when(despachoService.aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class)))
                .thenThrow(new IllegalArgumentException("El evento no incluye usuarioId"));

        despachoListener.recibirEvento(mensajeConJson(
                "{\"tipo\":\"PAGO_APROBADO\",\"pedidoId\":7}"), channel);

        // Evento incompleto: irrecuperable, directo a la DLQ sin reintentos.
        verify(despachoService, times(1)).aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class));
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 3: fallo de base de datos -> reintentos y luego NACK a la DLQ
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaReintentar_yNackHaciaLaDlq_siLaBaseDeDatosFallaSiempre() throws Exception {
        when(despachoService.aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class)))
                .thenThrow(new RuntimeException("H2 caido"));

        despachoListener.recibirEvento(mensajeConJson(JSON_PAGO_APROBADO), channel);

        // 3 intentos (pedidos360.messaging.despachos.consumer.max-attempts)
        verify(despachoService, times(3)).aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class));
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirEvento_deberiaRecuperarse_yHacerAck_siUnReintentoFunciona() throws Exception {
        when(despachoService.aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class)))
                .thenThrow(new RuntimeException("H2 caido"))
                .thenReturn(DespachoService.Resultado.CREADO);

        despachoListener.recibirEvento(mensajeConJson(JSON_PAGO_APROBADO), channel);

        verify(despachoService, times(2)).aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  Fallo del propio canal: no debe dejar la excepcion escapar
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_noDebeLanzarExcepcion_siElCanalFallaAlConfirmar() throws Exception {
        when(despachoService.aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class)))
                .thenReturn(DespachoService.Resultado.CREADO);
        doThrow(new java.io.IOException("canal cerrado")).when(channel).basicAck(TAG, false);

        despachoListener.recibirEvento(mensajeConJson(JSON_PAGO_APROBADO), channel);

        verify(despachoService).aplicar(org.mockito.ArgumentMatchers.any(EventoDespacho.class));
    }
}
