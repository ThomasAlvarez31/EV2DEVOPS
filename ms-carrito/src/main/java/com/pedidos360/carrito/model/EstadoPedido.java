package com.pedidos360.carrito.model;

import java.util.Arrays;

/**
 * Estados del ciclo de vida de un pedido.
 *
 * PENDIENTE_PAGO  -> recien creado en el checkout, esperando el pago simulado.
 * CONFIRMADO      -> pago aprobado (equivale a "Pagado").
 * EN_PREPARACION / ENVIADO / ENTREGADO -> seguimiento logistico.
 * CANCELADO       -> pedido anulado (pago rechazado u otra anulacion).
 */
public enum EstadoPedido {
    PENDIENTE_PAGO,
    CONFIRMADO,
    EN_PREPARACION,
    ENVIADO,
    ENTREGADO,
    CANCELADO;

    /**
     * Convierte el valor enviado por el frontend (enum o etiqueta legible)
     * en EstadoPedido. Lanza IllegalArgumentException si no es valido.
     */
    public static EstadoPedido desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el estado del pedido");
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        return Arrays.stream(values())
                .filter(e -> e.name().equals(normalizado) || e.getEtiqueta().equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de pedido no valido: " + valor));
    }

    public String getEtiqueta() {
        return switch (this) {
            case PENDIENTE_PAGO -> "Pendiente de pago";
            case CONFIRMADO -> "Pagado";
            case EN_PREPARACION -> "En preparacion";
            case ENVIADO -> "Enviado";
            case ENTREGADO -> "Entregado";
            case CANCELADO -> "Cancelado";
        };
    }
}
