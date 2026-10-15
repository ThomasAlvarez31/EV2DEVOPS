package com.pedidos360.email.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.email.config.EmailMessagingProperties;
import com.pedidos360.email.model.MensajeEmail;
import com.pedidos360.email.service.EmailService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de la cola de correos: recibe los eventos publicados por
 * ms-carrito (PEDIDO_REGISTRADO, PAGO_APROBADO, PAGO_RECHAZADO,
 * CAMBIO_ESTADO) y dispara el envio por SMTP.
 *
 * La escucha esta agrupada por dominio funcional: esta clase pertenece al
 * dominio "email" (paquete {@code listener} de ms-email) y la cola se toma
 * de la configuracion centralizada (pedidos360.messaging.email.queue).
 *
 * CONFIRMACION EXPLICITA (acknowledge-mode: manual):
 *
 *  - El contenedor NO confirma nada. Cada ejecucion confirma o rechaza el
 *    mensaje con basicAck / basicNack sobre el delivery tag, por lo que el
 *    ciclo de vida del mensaje es explicito y auditable en los logs.
 *
 *  - Decisiones de error (rutas diferenciadas):
 *      1. Mensaje ilegible (JSON invalido) -> es IRRECUPERABLE: se hace
 *         basicNack(requeue=false) SIN reintentar, y el mensaje cae
 *         directamente en la DLQ para poder inspeccionarse.
 *      2. Error de envio (SMTP caido, red) -> es RECUPERABLE: se reintenta
 *         un numero acotado de veces con backoff exponencial. Si todos los
 *         intentos fallan -> basicNack(requeue=false) y a la DLQ.
 *      3. Exito -> basicAck(requeue=false): el mensaje queda consumido.
 *
 *  - requeue=false SIEMPRE: re-encolar de inmediato generaria un bucle
 *    caliente de reentregas. Los reintentos se hacen en memoria, con espera,
 *    y cuando se agotan el dead-lettering del broker se encarga de la cola
 *    de respaldo pedidos360.emails.dlq (declarada en RabbitConfig).
 *
 *  - Si la aplicacion muere a mitad de proceso, el mensaje sin confirmar
 *    vuelve a la cola: entrega al menos una vez.
 *
 * El resto de la compra nunca falla: el pedido ya esta persistido en
 * ms-carrito.
 */
@Component
public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final String cola;
    private final String dlq;
    private final EmailMessagingProperties.Consumer politica;

    public EmailListener(ObjectMapper objectMapper,
                         EmailService emailService,
                         EmailMessagingProperties rutas) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.cola = rutas.queue();
        this.dlq = rutas.dlq();
        // Politica de reintentos tambien centralizada en application.yml
        // (pedidos360.messaging.email.consumer). Con un valor por defecto
        // razonable si la clave no estuviera definida.
        this.politica = rutas.consumer() != null
                ? rutas.consumer()
                : new EmailMessagingProperties.Consumer(3, 2000);
    }

    @RabbitListener(queues = "${pedidos360.messaging.email.queue}")
    public void recibirMensaje(Message message, Channel channel) {
        long tag = message.getMessageProperties().getDeliveryTag();

        MensajeEmail mensaje;
        try {
            mensaje = objectMapper.readValue(message.getBody(), MensajeEmail.class);
        } catch (Exception e) {
            // RUTA 1: mensaje ilegible -> irrecuperable, sin reintentos.
            log.error("Mensaje ilegible en la cola {} (tag={}): {} -> NACK sin requeue, va a la DLQ",
                    cola, tag, e.getMessage());
            nack(channel, tag, "mensaje ilegible");
            return;
        }

        int intentos = politica.intentosEfectivos();
        for (int intento = 1; intento <= intentos; intento++) {
            try {
                enviar(mensaje);
                log.info("Mensaje de tipo {} procesado para {} (cola {}, tag={}) -> ACK",
                        mensaje.tipo(), mensaje.destino(), cola, tag);
                ack(channel, tag);
                return;
            } catch (Exception e) {
                log.error("Intento {}/{} fallado al enviar el correo a {} (cola {}, tag={}): {}",
                        intento, intentos, mensaje.destino(), cola, tag, e.getMessage());
                if (intento < intentos) {
                    esperar(conDelayExponencial(intento));
                }
            }
        }

        // RUTA 2: reintentos agotados -> irrecuperable por ahora, a la DLQ.
        log.error("Agotados los {} intentos de envio a {} (cola {}, tag={}) -> NACK sin requeue, va a la DLQ",
                intentos, mensaje.destino(), cola, tag);
        nack(channel, tag, "reintentos agotados");
    }

    /** Elige la plantilla de envio segun el cuerpo del mensaje. */
    private void enviar(MensajeEmail mensaje) {
        if (EmailService.esHtml(mensaje.cuerpo())) {
            emailService.enviarHtml(mensaje.destino(), mensaje.asunto(), mensaje.cuerpo());
        } else {
            emailService.enviarCorreo(mensaje.destino(), mensaje.asunto(), mensaje.cuerpo());
        }
    }

    /** Backoff exponencial: 2s, 4s, 8s... limitado por la politica. */
    private long conDelayExponencial(int intento) {
        long base = Math.max(politica.retryDelayMs(), 0);
        int shift = Math.min(intento - 1, 10);
        return base * (1L << shift);
    }

    private void esperar(long milisegundos) {
        if (milisegundos <= 0) {
            return;
        }
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ack(Channel channel, long tag) {
        try {
            channel.basicAck(tag, false);
        } catch (IOException | RuntimeException e) {
            // Sin ACK el canal se cierra al finalizar y el mensaje se
            // re-entrega: la entrega sigue garantizada (al menos una vez).
            log.error("No se pudo confirmar el mensaje tag={}: {}", tag, e.getMessage());
        }
    }

    private void nack(Channel channel, long tag, String motivo) {
        try {
            // requeue=false -> el broker lo envia a la DLQ declarada con
            // x-dead-letter-routing-key, nunca a un bucle de reentregas.
            channel.basicNack(tag, false, false);
            log.info("Mensaje tag={} rechazado ({}) -> enviado a la DLQ {}", tag, motivo, dlq);
        } catch (IOException | RuntimeException e) {
            log.error("No se pudo rechazar el mensaje tag={}: {}", tag, e.getMessage());
        }
    }
}
