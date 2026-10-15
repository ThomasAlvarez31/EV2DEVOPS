package com.pedidos360.carrito.model;

/**
 * Estado del pago simulado de un pedido. Se guarda por separado del estado
 * del pedido para poder distinguir "pedido pendiente de pago" de "pedido
 * cancelado por un pago rechazado".
 */
public enum EstadoPago {
    /** Checkout realizado, el pago aun no se procesa. */
    PENDIENTE,
    /** Pago simulado aprobado. */
    APROBADO,
    /** Pago simulado rechazado. */
    RECHAZADO
}
