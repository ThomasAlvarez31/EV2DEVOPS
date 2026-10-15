package com.pedidos360.ordenes.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Evento de negocio que llega por la cola pedidos360.ordenes.
 *
 * Es el MISMO payload que publica ms-carrito en el exchange de eventos, pero
 * aqui solo se leen los campos que necesita el dominio de ordenes: tipo,
 * pedidoId, usuarioId, estado, total, metodoPago, fecha e items. Los campos
 * de correo (destino/asunto/cuerpo) que consume ms-email se ignoran
 * (@JsonIgnoreProperties).
 *
 * El consumidor hace parseo directo del JSON: no existe ninguna dependencia
 * de codigo con el productor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoOrden(
        String tipo,
        Long pedidoId,
        String usuarioId,
        String estado,
        String metodoPago,
        Double total,
        String fecha,
        List<ItemOrdenEvent> items
) {

    /** Tipos de evento que este dominio sabe aplicar. */
    public static final String TIPO_PEDIDO_REGISTRADO = "PEDIDO_REGISTRADO";
    public static final String TIPO_PAGO_APROBADO = "PAGO_APROBADO";
    public static final String TIPO_PAGO_RECHAZADO = "PAGO_RECHAZADO";
    public static final String TIPO_CAMBIO_ESTADO = "CAMBIO_ESTADO";

    /** Linea del pedido que se persiste en la orden. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemOrdenEvent(
            Long productoId,
            String nombre,
            Integer cantidad,
            Double precioUnitario
    ) {
    }
}
