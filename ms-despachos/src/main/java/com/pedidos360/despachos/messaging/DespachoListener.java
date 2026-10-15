package com.pedidos360.despachos.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.despachos.config.DespachosMessagingProperties;
import com.pedidos360.despachos.service.DespachoService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de la cola de despachos: recibe los eventos publicados por
 * ms-carrito (PAGO_APROBADO y CAMBIO_ESTADO) y genera/actualiza la guia de
 * envio de forma ASINCRONA con {@link DespachoService}.
 *
 * La escucha esta agrupada por dominio funcional: esta clase pertenece al
 * dominio "despachos" (paquete {@code messaging} de ms-despachos) y la cola
 * se toma de la configuracion centralizada (pedidos360.messaging.despachos.queue).
 *
 * CONFIRMACION EXPLICITA (acknowledge-mode: manual):
 *
 *  - El contenedor NO confirma nada. Cada ejecucion confirma o rechaza el
 *    mensaje con basicAck / basicNack sobre el delivery tag, por lo que el
 *    ciclo de vida del mensaje es explicito y auditable en los logs.
 *
 *  - Decisiones de error (rutas diferenciadas):
 *      1. Mensaje ilegible o evento incompleto (sin tipo, pedidoId o items)
 *         -> es IRRECUPERABLE: basicNack(requeue=false) directo a la DLQ.
 *      2. Fallo transitorio (base de datos caida) -> es RECUPERABLE: se
 *         reintenta un numero acotado de veces con backoff exponencial. Si
 *         todos los intentos fallan -> basicNack(requeue=false) a la DLQ.
 *         La aplicacion del evento es TRANSACCIONAL: si falla, no queda nada
 *         aplicado a medias.
 *      3. Evento repetido o ajeno al dominio -> basicAck: ya se resolvio,
 *         no tiene sentido reintentarlo (idempotencia de DespachoService).
 *      4. Exito -> basicAck: el despacho quedo generado o actualizado.
 *
 *  - requeue=false SIEMPRE: re-encolar de inmediato generaria un bucle
 *    caliente de reentregas. Los reintentos se hacen en memoria y, cuando se
 *    agotan, el dead-lettering del broker envia el mensaje a
 *    pedidos360.despachos.dlq (declarada en RabbitConfig).
 *
 *  - Si la aplicacion muere a mitad de proceso, el mensaje sin confirmar
 *    vuelve a la cola: entrega al menos una vez, y DespachoService evita por
 *    idempotencia generar dos despachos para el mismo pedido.
 */
@Component
public class DespachoListener {

    private static final Logger log = LoggerFactory.getLogger(DespachoListener.class);

    private final ObjectMapper objectMapper;
    private final DespachoService despachoService;
    private final String cola;
    private final String dlq;
    private final DespachosMessagingProperties.Consumer politica;

    public DespachoListener(ObjectMapper objectMapper,
                         DespachoService despachoService,
                         DespachosMessagingProperties rutas) {
        this.objectMapper = objectMapper;
        this.despachoService = despachoService;
        this.cola = rutas.queue();
        this.dlq = rutas.dlq();
        // Politica de reintentos centralizada en application.yml
        // (pedidos360.messaging.despachos.consumer), con un valor por defecto
        // razonable si la clave no estuviera definida.
        this.politica = rutas.consumer() != null
                ? rutas.consumer()
                : new DespachosMessagingProperties.Consumer(3, 1000);
    }

    @RabbitListener(queues = "${pedidos360.messaging.despachos.queue}")
    public void recibirEvento(Message message, Channel channel) {
        long tag = message.getMessageProperties().getDeliveryTag();

        EventoDespacho evento;
        try {
            evento = objectMapper.readValue(message.getBody(), EventoDespacho.class);
        } catch (Exception e) {
            // RUTA 1a: mensaje ilegible -> irrecuperable, sin reintentos.
            log.error("Mensaje ilegible en la cola {} (tag={}): {} -> NACK sin requeue, va a la DLQ",
                    cola, tag, e.getMessage());
            nack(channel, tag, "mensaje ilegible");
            return;
        }

        int intentos = politica.intentosEfectivos();
        for (int intento = 1; intento <= intentos; intento++) {
            try {
                aplicar(evento, tag);
                ack(channel, tag);
                return;
            } catch (IllegalArgumentException e) {
                // RUTA 1b: evento incompleto -> irrecuperable, sin reintentos.
                log.error("Evento incompleto en la cola {} (tag={}): {} -> NACK sin requeue, va a la DLQ",
                        cola, tag, e.getMessage());
                nack(channel, tag, "evento incompleto");
                return;
            } catch (Exception e) {
                log.error("Intento {}/{} fallado al aplicar el evento {} del pedido {} (cola {}, tag={}): {}",
                        intento, intentos, evento.tipo(), evento.pedidoId(), cola, tag, e.getMessage());
                if (intento < intentos) {
                    esperar(conDelayExponencial(intento));
                }
            }
        }

        // RUTA 2: reintentos agotados -> irrecuperable por ahora, a la DLQ.
        log.error("Agotados los {} intentos de aplicar el evento {} del pedido {} (cola {}, tag={}) "
                        + "-> NACK sin requeue, va a la DLQ",
                intentos, evento.tipo(), evento.pedidoId(), cola, tag);
        nack(channel, tag, "reintentos agotados");
    }

    /** Aplica el evento al despacho y deja constancia en el log. */
    private void aplicar(EventoDespacho evento, long tag) {
        DespachoService.Resultado resultado = despachoService.aplicar(evento);
        switch (resultado) {
            case CREADO -> log.info("Evento {} del pedido {} -> despacho CREADO (cola {}, tag={}) -> ACK",
                    evento.tipo(), evento.pedidoId(), cola, tag);
            case ACTUALIZADO -> log.info("Evento {} del pedido {} -> despacho ACTUALIZADO (cola {}, tag={}) -> ACK",
                    evento.tipo(), evento.pedidoId(), cola, tag);
            case REPETIDO -> log.info("Evento {} del pedido {} repetido: ya reflejado en el despacho "
                    + "(cola {}, tag={}) -> ACK", evento.tipo(), evento.pedidoId(), cola, tag);
            case IGNORADO -> log.warn("Evento {} del pedido {} descartado (cola {}, tag={}) -> ACK",
                    evento.tipo(), evento.pedidoId(), cola, tag);
        }
    }

    /** Backoff exponencial: 1s, 2s, 4s... limitado por la politica. */
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
            // re-entrega: la entrega sigue garantizada (al menos una vez) y
            // la idempotencia de DespachoService evita duplicar el despacho.
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
