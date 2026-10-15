package com.pedidos360.email.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.email.config.EmailMessagingProperties;
import com.pedidos360.email.service.EmailService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pruebas del consumidor con ACK explicito.
 *
 * Se comprueban las TRES rutas de confirmacion:
 *   - exito  -> basicAck
 *   - mensaje ilegible -> basicNack(requeue=false) sin reintentos
 *   - error de envio agotado -> basicNack(requeue=false) tras los reintentos
 */
class EmailListenerTest {

    private static final long TAG = 42L;

    private EmailService emailService;
    private Channel channel;
    private EmailListener emailListener;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        channel = mock(Channel.class);

        EmailMessagingProperties rutas = new EmailMessagingProperties(
                "pedidos360.eventos.topic",
                "pedidos360.emails",
                "pedidos360.emails.dlq",
                Map.of(
                        "pedido-registrado", "pedido.registrado",
                        "pago-aprobado", "pago.aprobado",
                        "pago-rechazado", "pago.rechazado",
                        "cambio-estado", "pedido.cambio_estado"),
                new EmailMessagingProperties.Consumer(3, 0));

        emailListener = new EmailListener(new ObjectMapper(), emailService, rutas);
    }

    private Message mensajeConJson(String json) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    private static final String JSON_TEXTO = """
            {"destino":"cliente@pedidos360.cl",
             "asunto":"Pedidos360 - Pedido #1 registrado",
             "cuerpo":"Tu pedido fue registrado.",
             "tipo":"PEDIDO_REGISTRADO"}
            """;

    private static final String JSON_HTML = """
            {"destino":"cliente@pedidos360.cl",
             "asunto":"Pedidos360 - Pago aprobado",
             "cuerpo":"<!doctype html><html><body>Hola</body></html>",
             "tipo":"PAGO_APROBADO"}
            """;

    // ------------------------------------------------------------------
    //  RUTA 1: exito -> ACK
    // ------------------------------------------------------------------

    @Test
    void recibirMensaje_deberiaEnviarCorreoDeTexto_yConfirmarConAck() throws Exception {
        emailListener.recibirMensaje(mensajeConJson(JSON_TEXTO), channel);

        verify(emailService).enviarCorreo(
                "cliente@pedidos360.cl",
                "Pedidos360 - Pedido #1 registrado",
                "Tu pedido fue registrado.");
        verify(emailService, never()).enviarHtml(anyString(), anyString(), anyString());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    @Test
    void recibirMensaje_deberiaEnviarHtml_yConfirmarConAck() throws Exception {
        emailListener.recibirMensaje(mensajeConJson(JSON_HTML), channel);

        verify(emailService).enviarHtml(
                "cliente@pedidos360.cl",
                "Pedidos360 - Pago aprobado",
                "<!doctype html><html><body>Hola</body></html>");
        verify(emailService, never()).enviarCorreo(anyString(), anyString(), anyString());
        verify(channel).basicAck(TAG, false);
    }

    // ------------------------------------------------------------------
    //  RUTA 2: mensaje ilegible -> NACK directo a la DLQ (sin reintentos)
    // ------------------------------------------------------------------

    @Test
    void recibirMensaje_deberiaNackSinRequeue_siElJsonEsInvalido() throws Exception {
        emailListener.recibirMensaje(mensajeConJson("{esto no es json"), channel);

        // No se reintentan mensajes irrecuperables: directo a la DLQ.
        verifyNoInteractions(emailService);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    // ------------------------------------------------------------------
    //  RUTA 3: error de envio -> reintentos y luego NACK a la DLQ
    // ------------------------------------------------------------------

    @Test
    void recibirMensaje_deberiaReintentar_yNackHaciaLaDlq_siElEnvioFallaSiempre() throws Exception {
        doThrow(new RuntimeException("SMTP caido"))
                .when(emailService).enviarCorreo(anyString(), anyString(), anyString());

        emailListener.recibirMensaje(mensajeConJson(JSON_TEXTO), channel);

        // 3 intentos (pedidos360.messaging.email.consumer.max-attempts)
        verify(emailService, times(3)).enviarCorreo(anyString(), anyString(), anyString());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void recibirMensaje_deberiaRecuperarse_yHacerAck_siUnReintentoFunciona() throws Exception {
        doThrow(new RuntimeException("SMTP caido"))
                .doNothing()
                .when(emailService).enviarCorreo(anyString(), anyString(), anyString());

        emailListener.recibirMensaje(mensajeConJson(JSON_TEXTO), channel);

        verify(emailService, times(2)).enviarCorreo(anyString(), anyString(), anyString());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), eq(false), eq(false));
    }

    // ------------------------------------------------------------------
    //  Fallo del propio canal: no debe dejar la excepcion escapar
    // ------------------------------------------------------------------

    @Test
    void recibirMensaje_noDebeLanzarExcepcion_siElCanalFallaAlConfirmar() throws Exception {
        org.mockito.Mockito.doThrow(new java.io.IOException("canal cerrado"))
                .when(channel).basicAck(TAG, false);

        emailListener.recibirMensaje(mensajeConJson(JSON_TEXTO), channel);

        verify(emailService).enviarCorreo(anyString(), anyString(), anyString());
    }
}
