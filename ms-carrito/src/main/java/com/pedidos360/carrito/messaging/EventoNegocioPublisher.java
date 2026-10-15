package com.pedidos360.carrito.messaging;

import com.pedidos360.carrito.config.EventosMessagingProperties;
import com.pedidos360.carrito.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publica los eventos de negocio en RabbitMQ (productor).
 *
 * Eventos publicados (uno por hecho de negocio, para no duplicar trabajo):
 *   PEDIDO_REGISTRADO -> checkout: el pedido queda pendiente de pago
 *   PAGO_APROBADO     -> el pago simulado fue aprobado
 *   PAGO_RECHAZADO    -> el pago simulado fue rechazado
 *   CAMBIO_ESTADO     -> seguimiento logistico del pedido
 *
 * El mensaje NO va a una cola sino al exchange de eventos, con la routing key
 * que configura el binding de CADA consumidor:
 *
 *   pedidos360.emails      -> ms-email (correo)
 *   pedidos360.stock       -> ms-productos (descuenta o repone stock)
 *   pedidos360.ordenes     -> ms-ordenes (registro formal de la orden)
 *   pedidos360.despachos   -> ms-despachos (genera el despacho)
 *
 * Los nombres del exchange y de las routing keys viven en application.yml
 * (pedidos360.messaging.eventos); aqui no hay ningun literal de RabbitMQ:
 * solo se consume la configuracion tipada {@link EventosMessagingProperties}.
 *
 * Si RabbitMQ no esta disponible solo se registra el error: el pedido ya
 * quedo persistido y la compra nunca debe fallar por la mensajeria.
 */
@Component
public class EventoNegocioPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoNegocioPublisher.class);

    public static final String TIPO_PEDIDO_REGISTRADO = "PEDIDO_REGISTRADO";
    public static final String TIPO_PAGO_APROBADO = "PAGO_APROBADO";
    public static final String TIPO_PAGO_RECHAZADO = "PAGO_RECHAZADO";
    public static final String TIPO_CAMBIO_ESTADO = "CAMBIO_ESTADO";

    private final RabbitTemplate rabbitTemplate;
    private final EventosMessagingProperties rutas;

    public EventoNegocioPublisher(RabbitTemplate rabbitTemplate, EventosMessagingProperties rutas) {
        this.rabbitTemplate = rabbitTemplate;
        this.rutas = rutas;
    }

    /**
     * Confirmacion de compra: el pedido ya quedo registrado en el sistema.
     */
    public void publicarPedidoRegistrado(String destino, String nombre, Pedido pedido) {
        String cuerpo = PlantillaEmails.envoltorio(
                "Pedido #" + pedido.getId() + " registrado",
                PlantillaEmails.resumenPedido(pedido, nombre)
                        + PlantillaEmails.cierre(
                                "Recibimos tu pedido y ya lo estamos preparando. "
                                        + "El pago aún no se ha procesado: cuando lo hagas recibirás la confirmación. "
                                        + "Gracias por comprar en Pedidos360."));
        publicar(destino, pedido, "Pedidos360 - Pedido #" + pedido.getId() + " registrado",
                cuerpo, TIPO_PEDIDO_REGISTRADO);
    }

    /**
     * Confirmacion de pago aprobado.
     */
    public void publicarPagoAprobado(String destino, String nombre, Pedido pedido) {
        String cuerpo = PlantillaEmails.envoltorio(
                "Pago aprobado - Pedido #" + pedido.getId(),
                PlantillaEmails.aviso("#dcfce7", "Tu pago fue aprobado. El pedido está confirmado.")
                        + PlantillaEmails.resumenPedido(pedido, nombre)
                        + PlantillaEmails.cierre("Gracias por comprar en Pedidos360."));
        publicar(destino, pedido, "Pedidos360 - Pago aprobado (pedido #" + pedido.getId() + ")",
                cuerpo, TIPO_PAGO_APROBADO);
    }

    /**
     * Aviso de pago rechazado: el pedido queda cancelado y el carrito restaurado.
     */
    public void publicarPagoRechazado(String destino, String nombre, Pedido pedido) {
        String cuerpo = PlantillaEmails.envoltorio(
                "Pago rechazado - Pedido #" + pedido.getId(),
                PlantillaEmails.aviso("#fee2e2", "Tu pago fue rechazado. El pedido quedó cancelado.")
                        + PlantillaEmails.resumenPedido(pedido, nombre)
                        + PlantillaEmails.cierre(
                                "No se realizó ningún cobro. Tus productos siguen en el carrito "
                                        + "para que puedas intentarlo nuevamente."));
        publicar(destino, pedido, "Pedidos360 - Pago rechazado (pedido #" + pedido.getId() + ")",
                cuerpo, TIPO_PAGO_RECHAZADO);
    }

    /**
     * Actualizacion del estado del pedido (seguimiento logistico): un unico
     * evento por cada cambio de estado, no uno por evento repetido.
     */
    public void publicarCambioEstado(String destino, String nombre, Pedido pedido) {
        String color = switch (pedido.getEstado()) {
            case ENTREGADO, CONFIRMADO -> "#dcfce7";
            case CANCELADO -> "#fee2e2";
            default -> "#dbeafe";
        };

        String cuerpo = PlantillaEmails.envoltorio(
                "Actualizacion del pedido #" + pedido.getId(),
                PlantillaEmails.aviso(color, "El estado del pedido cambio a: " + PlantillaEmails.etiquetaEstado(pedido))
                        + PlantillaEmails.resumenPedido(pedido, nombre)
                        + PlantillaEmails.cierre(
                                "Puedes consultar el detalle de tus compras en la seccion "
                                        + "\"Mis pedidos\" de Pedidos360."));

        publicar(destino, pedido, "Pedidos360 - Pedido #" + pedido.getId() + ": "
                        + PlantillaEmails.etiquetaEstado(pedido),
                cuerpo, TIPO_CAMBIO_ESTADO);
    }

    /**
     * Publica el evento en el exchange de eventos con la routing key del caso
     * de uso. Si RabbitMQ no esta disponible solo se registra el error: el
     * pedido ya quedo persistido y no debe fallar por la mensajeria.
     */
    private void publicar(String destino, Pedido pedido, String asunto, String cuerpo, String tipo) {
        if (destino == null || destino.isBlank()) {
            log.warn("No se publica el evento {} del pedido {}: el token no incluye email (preferred_username)",
                    tipo, pedido.getId());
            return;
        }

        try {
            String routingKey = rutas.routingKeyDe(tipo);
            if (routingKey == null || routingKey.isBlank()) {
                log.error("No hay routing key para el tipo {} en pedidos360.messaging.eventos.routing-keys "
                                + "del exchange {}; el mensaje NO se publica.",
                        tipo, rutas.exchange());
                return;
            }

            EventoNegocio evento = new EventoNegocio(destino, asunto, cuerpo, tipo,
                    pedido.getId(), pedido.getUsuarioId(),
                    pedido.getEstado() == null ? null : pedido.getEstado().name(),
                    pedido.getMetodoPago() == null ? null : pedido.getMetodoPago().name(),
                    pedido.getTotal(),
                    pedido.getFecha() == null ? null : pedido.getFecha().toString(),
                    itemsDe(pedido));

            rabbitTemplate.convertAndSend(rutas.exchange(), routingKey, evento);
            log.info("Evento {} del pedido {} publicado en el exchange {} (routing key {}) -> {}",
                    tipo, pedido.getId(), rutas.exchange(), routingKey, destino);
        } catch (AmqpException e) {
            log.error("No se pudo publicar el evento {} del pedido {}: {}",
                    tipo, pedido.getId(), e.getMessage(), e);
        }
    }

    /**
     * Copia las lineas del pedido en el formato del evento: ms-productos usa
     * productoId/cantidad para descontar stock y ms-ordenes usa la lista
     * completa para registrar la orden formal.
     */
    private List<EventoNegocio.ItemEvento> itemsDe(Pedido pedido) {
        if (pedido.getItems() == null) {
            return List.of();
        }
        return pedido.getItems().stream()
                .map(item -> new EventoNegocio.ItemEvento(
                        item.getProductoId(),
                        item.getNombreProducto(),
                        item.getCantidad(),
                        item.getPrecioUnitario()))
                .toList();
    }
}
