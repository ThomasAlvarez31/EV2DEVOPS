package com.pedidos360.carrito.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.carrito.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica los eventos de negocio en el topico de Kafka que consume ms-monitoreo.
 *
 * Diferencia clave con RabbitMQ (EventoNegocioPublisher):
 *  - RabbitMQ es una COLA: ms-email recibe cada mensaje UNA vez y al consumirlo
 *    desaparece (sirve para ENTREGAR correos).
 *  - Kafka es un LOG de eventos: el mensaje queda guardado en el topico y
 *    ms-monitoreo lo lee como un historial (sirve para OBSERVAR/monitorear).
 *
 * Eventos publicados (uno por hecho de negocio, igual que los de correo):
 *   PEDIDO_REGISTRADO -> checkout: pedido creado pendiente de pago
 *   PAGO_APROBADO     -> pago simulado aprobado
 *   PAGO_RECHAZADO    -> pago simulado rechazado (pedido cancelado)
 *   CAMBIO_ESTADO     -> cambio de estado logistico del pedido
 */
@Component
public class MonitorPublisher {

    private static final Logger log = LoggerFactory.getLogger(MonitorPublisher.class);

    /** Topico (topic) donde queda el log de eventos para monitoreo. */
    public static final String TOPICO = "pedidos360.monitoreo";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MonitorPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicarPedidoRegistrado(Pedido pedido) {
        publicar("PEDIDO_REGISTRADO", pedido, "Pedido creado y pendiente de pago");
    }

    public void publicarPagoAprobado(Pedido pedido) {
        publicar("PAGO_APROBADO", pedido, "Pago aprobado por ms-pagos");
    }

    public void publicarPagoRechazado(Pedido pedido) {
        publicar("PAGO_RECHAZADO", pedido, "Pago rechazado; pedido cancelado y carrito restaurado");
    }

    public void publicarCambioEstado(Pedido pedido) {
        publicar("CAMBIO_ESTADO", pedido, "Cambio de estado logistico a " + pedido.getEstado());
    }

    /**
     * Convierte el evento a JSON y lo envia al topico. Si Kafka no esta
     * disponible solo se registra el error: la compra ya quedo persistida y
     * el monitoreo no debe romper el flujo principal (mismo criterio que
     * EventoNegocioPublisher con RabbitMQ).
     */
    private void publicar(String tipo, Pedido pedido, String detalle) {
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("tipo", tipo);
        evento.put("pedidoId", pedido.getId());
        evento.put("usuarioId", pedido.getUsuarioId());
        evento.put("monto", pedido.getTotal());
        evento.put("estadoPedido", pedido.getEstado() == null ? null : pedido.getEstado().name());
        evento.put("estadoPago", pedido.getEstadoPago() == null ? null : pedido.getEstadoPago().name());
        evento.put("metodoPago", pedido.getMetodoPago() == null ? null : pedido.getMetodoPago().name());
        evento.put("detalle", detalle);
        evento.put("fecha", Instant.now().toString());
        evento.put("servicio", "ms-carrito");

        try {
            String json = objectMapper.writeValueAsString(evento);
            // La clave (key) es el id del pedido: agrupa en la misma particion
            // todos los eventos del mismo pedido, conservando su orden.
            kafkaTemplate.send(TOPICO, String.valueOf(pedido.getId()), json).whenComplete((resultado, error) -> {
                if (error != null) {
                    log.error("No se pudo publicar el evento {} del pedido {} en Kafka: {}",
                            tipo, pedido.getId(), error.getMessage());
                } else {
                    log.info("Evento {} del pedido {} publicado en Kafka (topico {}, particion {})",
                            tipo, pedido.getId(), TOPICO,
                            resultado.getRecordMetadata().partition());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar el evento {} del pedido {}: {}",
                    tipo, pedido.getId(), e.getMessage());
        }
    }
}
