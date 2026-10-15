package com.pedidos360.carrito.messaging;

import java.util.List;

/**
 * Evento de negocio publicado en RabbitMQ por ms-carrito.
 *
 * Un UNICO payload por evento lo consumen tres microservicios distintos,
 * cada uno desde su propia cola (patron fan-out sobre el mismo exchange):
 *
 *   - ms-email      -> cola pedidos360.emails      (usa destino/asunto/cuerpo)
 *   - ms-productos  -> cola pedidos360.stock       (usa items: descuenta stock)
 *   - ms-ordenes    -> cola pedidos360.ordenes     (registra la orden formal)
 *   - ms-despachos  -> cola pedidos360.despachos   (genera el despacho)
 *
 * Cada consumidor declara su propio DTO de entrada y solo lee los campos que
 * le interesan: aqui NO hay acoplamiento con las clases del consumidor.
 */
public record EventoNegocio(
        String destino,
        String asunto,
        String cuerpo,
        String tipo,
        Long pedidoId,
        String usuarioId,
        String estado,
        String metodoPago,
        Double total,
        String fecha,
        List<ItemEvento> items
) {

    /** Linea del pedido que consumidor de stock y de ordenes necesitan. */
    public record ItemEvento(
            Long productoId,
            String nombre,
            Integer cantidad,
            Double precioUnitario
    ) {
    }
}
