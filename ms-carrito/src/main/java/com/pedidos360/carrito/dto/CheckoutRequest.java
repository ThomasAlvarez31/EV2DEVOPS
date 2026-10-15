package com.pedidos360.carrito.dto;

/**
 * Cuerpo del checkout: obliga al frontend a indicar el metodo de pago elegido.
 * No se recibe ningun dato bancario: el pago es simulado.
 */
public record CheckoutRequest(String metodoPago) {
}
