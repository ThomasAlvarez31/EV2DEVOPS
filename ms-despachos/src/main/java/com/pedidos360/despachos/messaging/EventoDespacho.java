package com.pedidos360.despachos.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Evento de negocio que llega por la cola pedidos360.despachos.
 *
 * Es el MISMO payload que publica ms-carrito en el exchange de eventos, pero
 * aqui solo se leen los campos que necesita el dominio de despachos: tipo,
 * pedidoId, usuarioId y estado. Los campos de correo y de items que consumen
 * otros microservicios se ignoran (@JsonIgnoreProperties).
 *
 * El consumidor hace parseo directo del JSON: no existe ninguna dependencia
 * de codigo con el productor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoDespacho(
        String tipo,
        Long pedidoId,
        String usuarioId,
        String estado,
        String metodoPago,
        Double total,
        String fecha,
        List<ItemDespachoEvent> items
) {

    /** Tipos de evento que este dominio sabe aplicar. */
    public static final String TIPO_PAGO_APROBADO = "PAGO_APROBADO";
    public static final String TIPO_CAMBIO_ESTADO = "CAMBIO_ESTADO";

    /** Linea del pedido (no interviene en el despacho, solo se ignora). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemDespachoEvent(
            Long productoId,
            String nombre,
            Integer cantidad,
            Double precioUnitario
    ) {
    }
}
