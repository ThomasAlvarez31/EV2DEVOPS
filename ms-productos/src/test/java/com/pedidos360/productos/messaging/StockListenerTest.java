package com.pedidos360.productos.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.productos.config.StockMessagingProperties;
import com.pedidos360.productos.service.StockService;
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
 * Pruebas del consumidor de stock con ACK explicito.
 *
 * Se comprueban las rutas de confirmacion:
 *   - evento aplicado o repetido -> basicAck
 *   - mensaje ilegible o incompleto -> basicNack(requeue=false) sin reintentos
 *   - fallo de base de datos -> reintentos y luego basicNack hacia la DLQ
 */
class StockListenerTest {

    private static final long TAG = 42L;

    private StockService stockService;
    private Channel channel;
    private StockListener stockListener;

    @BeforeEach
    void setUp() {
        stockService = mock(StockService.class);
        channel = mock(Channel.class);

        StockMessagingProperties rutas = new StockMessagingProperties(
                "pedidos360.eventos.topic",
                "pedidos360.stock",
                "pedidos360.stock.dlq",
                Map.of(
                        "pedido-registrado", "pedido.registrado",
                        "pago-rechazado", "pago.rechazado"),
                new StockMessagingProperties.Consumer(3, 0));

        stockListener = new StockListener(new ObjectMapper(), stockService, rutas);
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
             "total":749.97,
             "fecha":"2026-10-08T12:30",
             "items":[{"productoId":1,"nombre":"Monitor 27\\"","cantidad":3,"precioUnitario":249.99}],
             "destino":"cliente@pedidos360.cl",
             "asunto":"Pedidos360 - Pedido #7 registrado",
             "cuerpo":"Tu pedido fue registrado."}
            """;

    // ------------------------------------------------------------------
    //  RUTA 1: evento aplicado -> ACK
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaAplicarElStock_yConfirmarConAck() throws Exception {
        when(stockService.aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class)))
                .thenReturn(StockService.Resultado.APLICADO);

        stockListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        verify(stockService).aplicar(org.mockito.ArgumentMatchers.argThat(
                evento -> "PEDIDO_REGISTRADO".equals(evento.tipo())
                        && evento.pedidoId() == 7L
                        && evento.items().size() == 1
                        && evento.items().get(0).cantidad() == 3));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    @Test
    void recibirEvento_deberiaHacerAck_siElEventoYaEstabaAplicado() throws Exception {
        when(stockService.aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class)))
                .thenReturn(StockService.Resultado.REPETIDO);

        stockListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        // Evento repetido: ya se resolvio, no tiene sentido reentregarlo.
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 2: mensaje ilegible -> NACK directo a la DLQ (sin reintentos)
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaNackSinRequeue_siElJsonEsInvalido() throws Exception {
        stockListener.recibirEvento(mensajeConJson("{esto no es json"), channel);

        verifyNoInteractions(stockService);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirEvento_deberiaNackSinRequeue_siElEventoNoTieneItems() throws Exception {
        when(stockService.aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class)))
                .thenThrow(new IllegalArgumentException("El evento del pedido 7 no incluye items"));

        stockListener.recibirEvento(mensajeConJson(
                "{\"tipo\":\"PEDIDO_REGISTRADO\",\"pedidoId\":7,\"items\":[]}"), channel);

        // Evento incompleto: irrecuperable, directo a la DLQ sin reintentos.
        verify(stockService, times(1)).aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class));
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 3: fallo de base de datos -> reintentos y luego NACK a la DLQ
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_deberiaReintentar_yNackHaciaLaDlq_siLaBaseDeDatosFallaSiempre() throws Exception {
        when(stockService.aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class)))
                .thenThrow(new RuntimeException("H2 caido"));

        stockListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        // 3 intentos (pedidos360.messaging.stock.consumer.max-attempts)
        verify(stockService, times(3)).aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class));
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirEvento_deberiaRecuperarse_yHacerAck_siUnReintentoFunciona() throws Exception {
        when(stockService.aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class)))
                .thenThrow(new RuntimeException("H2 caido"))
                .thenReturn(StockService.Resultado.APLICADO);

        stockListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        verify(stockService, times(2)).aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  Fallo del propio canal: no debe dejar la excepcion escapar
    // ------------------------------------------------------------------

    @Test
    void recibirEvento_noDebeLanzarExcepcion_siElCanalFallaAlConfirmar() throws Exception {
        when(stockService.aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class)))
                .thenReturn(StockService.Resultado.APLICADO);
        doThrow(new java.io.IOException("canal cerrado")).when(channel).basicAck(TAG, false);

        stockListener.recibirEvento(mensajeConJson(JSON_REGISTRADO), channel);

        verify(stockService).aplicar(org.mockito.ArgumentMatchers.any(EventoStock.class));
    }
}
