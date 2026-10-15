package com.pedidos360.carrito.dto;

/**
 * Cuerpo de PUT /pedidos/{id}/estado: indica el nuevo estado del pedido.
 * El backend valida las transiciones permitidas; un valor invalido o una
 * transicion no permitida devuelve 400.
 */
public record ActualizarEstadoRequest(String estado) {
}
