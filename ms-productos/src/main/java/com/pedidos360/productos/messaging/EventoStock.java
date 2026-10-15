package com.pedidos360.productos.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Evento de negocio que llega por la cola pedidos360.stock.
 *
 * Es el MISMO payload que publica ms-carrito en el exchange de eventos, pero
 * aqui solo se leen los campos que necesita el dominio de stock: tipo,
 * pedidoId e items (productoId + cantidad). Los campos de correo y de pedido
 * que consumen otros microservicios se ignoran (@JsonIgnoreProperties).
 *
 * El consumidor hace parseo directo del JSON: no existe ninguna dependencia
 * de codigo con el productor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoStock(
        String tipo,
        Long pedidoId,
        String usuarioId,
        String estado,
        Double total,
        List<ItemStock> items
) {

    /** Tipos de evento que este dominio sabe aplicar. */
    public static final String TIPO_PEDIDO_REGISTRADO = "PEDIDO_REGISTRADO";
    public static final String TIPO_PAGO_RECHAZADO = "PAGO_RECHAZADO";

    /** Linea del pedido: con ella se descuenta o se repone el inventario. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemStock(
            Long productoId,
            String nombre,
            Integer cantidad,
            Double precioUnitario
    ) {
    }
}
