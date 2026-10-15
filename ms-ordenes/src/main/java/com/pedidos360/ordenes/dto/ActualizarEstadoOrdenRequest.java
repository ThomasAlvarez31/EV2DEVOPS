package com.pedidos360.ordenes.dto;

/**
 * Cuerpo de PUT /ordenes/{id}/estado: indica el nuevo estado de la orden.
 * El backend valida las transiciones permitidas; un valor invalido o una
 * transicion no permitida devuelve 400.
 */
public record ActualizarEstadoOrdenRequest(String estado) {
}
