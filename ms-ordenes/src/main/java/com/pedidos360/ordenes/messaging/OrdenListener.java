package com.pedidos360.ordenes.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.ordenes.config.OrdenesMessagingProperties;
import com.pedidos360.ordenes.service.OrdenService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de la cola de ordenes: recibe los eventos publicados por
 * ms-carrito (PEDIDO_REGISTRADO, PAGO_APROBADO, PAGO_RECHAZADO y
 * CAMBIO_ESTADO) y registra/actualiza la orden de compra de forma ASINCRONA
 * con {@link OrdenService}.
 *
 * La escucha esta agrupada por dominio funcional: esta clase pertenece al
 * dominio "ordenes" (paquete {@code messaging} de ms-ordenes) y la cola se
 * toma de la configuracion centralizada (pedidos360.messaging.ordenes.queue).
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
 *         no tiene sentido reintentarlo (idempotencia de OrdenService).
 *      4. Exito -> basicAck: la orden quedo registrada o actualizada.
 *
 *  - requeue=false SIEMPRE: re-encolar de inmediato generaria un bucle
 *    caliente de reentregas. Los reintentos se hacen en memoria y, cuando se
 *    agotan, el dead-lettering del broker envia el mensaje a
 *    pedidos360.ordenes.dlq (declarada en RabbitConfig).
 *
 *  - Si la aplicacion muere a mitad de proceso, el mensaje sin confirmar
 *    vuelve a la cola: entrega al menos una vez, y OrdenService evita por
 *    idempotencia crear dos ordenes para el mismo pedido.
 */
@Component
public class OrdenListener {

    private static final Logger log = LoggerFactory.getLogger(OrdenListener.class);

    private final ObjectMapper objectMapper;
    private final OrdenService ordenService;
    private final String cola;
    private final String dlq;
    private final OrdenesMessagingProperties.Consumer politica;

    public OrdenListener(ObjectMapper objectMapper,
                         OrdenService ordenService,
                         OrdenesMessagingProperties rutas) {
        this.objectMapper = objectMapper;
        this.ordenService = ordenService;
        this.cola = rutas.queue();
        this.dlq = rutas.dlq();
        // Politica de reintentos centralizada en application.yml
        // (pedidos360.messaging.ordenes.consumer), con un valor por defecto
        // razonable si la clave no estuviera definida.
        this.politica = rutas.consumer() != null
                ? rutas.consumer()
                : new OrdenesMessagingProperties.Consumer(3, 1000);
    }

    @RabbitListener(queues = "${pedidos360.messaging.ordenes.queue}")
    public void recibirEvento(Message message, Channel channel) {
        long tag = message.getMessageProperties().getDeliveryTag();

        EventoOrden evento;
        try {
            evento = objectMapper.readValue(message.getBody(), EventoOrden.class);
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
        log.error("Agotados los {} intentos de registrar el evento {} del pedido {} (cola {}, tag={}) "
                        + "-> NACK sin requeue, va a la DLQ",
                intentos, evento.tipo(), evento.pedidoId(), cola, tag);
        nack(channel, tag, "reintentos agotados");
    }

    /** Aplica el evento a la orden y deja constancia en el log. */
    private void aplicar(EventoOrden evento, long tag) {
        OrdenService.Resultado resultado = ordenService.aplicar(evento);
        switch (resultado) {
            case CREADA -> log.info("Evento {} del pedido {} -> orden CREADA (cola {}, tag={}) -> ACK",
                    evento.tipo(), evento.pedidoId(), cola, tag);
            case ACTUALIZADA -> log.info("Evento {} del pedido {} -> orden ACTUALIZADA (cola {}, tag={}) -> ACK",
                    evento.tipo(), evento.pedidoId(), cola, tag);
            case REPETIDA -> log.info("Evento {} del pedido {} repetido: ya reflejado en la orden "
                    + "(cola {}, tag={}) -> ACK", evento.tipo(), evento.pedidoId(), cola, tag);
            case IGNORADA -> log.warn("Evento {} del pedido {} descartado (cola {}, tag={}) -> ACK",
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
            // la idempotencia de OrdenService evita duplicar la orden.
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
