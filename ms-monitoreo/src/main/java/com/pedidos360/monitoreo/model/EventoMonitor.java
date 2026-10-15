package com.pedidos360.monitoreo.model;

/**
 * Evento de negocio leido del topico de Kafka.
 *
 * Los primeros campos vienen en el JSON publicado por ms-carrito; los ultimos
 * (topico/particion/offset) son la "coordenada" del mensaje dentro del log de
 * Kafka, que es lo que permite saber exactamente DONDE esta cada evento.
 *
 * @param tipo         PEDIDO_REGISTRADO | PAGO_APROBADO | PAGO_RECHAZADO | CAMBIO_ESTADO
 * @param pedidoId     id del pedido afectado
 * @param usuarioId    usuario (oid de Azure AD o usuario sintetico en local)
 * @param monto        total del pedido
 * @param estadoPedido estado logistico del pedido
 * @param estadoPago   estado del pago simulado
 * @param metodoPago   metodo de pago elegido
 * @param detalle      descripcion legible del evento
 * @param fecha        instante en que ocurrio (ISO-8601)
 * @param servicio     microservicio que origino el evento
 * @param topico       topic de Kafka donde quedo registrado
 * @param particion    particion del log que lo contiene
 * @param offset       posicion dentro de esa particion
 */
public record EventoMonitor(
        String tipo,
        Long pedidoId,
        String usuarioId,
        Double monto,
        String estadoPedido,
        String estadoPago,
        String metodoPago,
        String detalle,
        String fecha,
        String servicio,
        String topico,
        Integer particion,
        Long offset) {

    /** Devuelve el evento con la posicion (particion/offset) que le asigno Kafka. */
    public EventoMonitor enPosicion(String topico, int particion, long offset) {
        return new EventoMonitor(tipo, pedidoId, usuarioId, monto, estadoPedido, estadoPago,
                metodoPago, detalle, fecha, servicio, topico, particion, offset);
    }
}
