package com.pedidos360.despachos.dto;

/**
 * Cuerpo de PUT /despachos/{id}/estado: indica el nuevo estado del envio.
 * El backend valida las transiciones permitidas; un valor invalido o una
 * transicion no permitida devuelve 400.
 */
public record ActualizarEstadoDespachoRequest(String estado) {
}
