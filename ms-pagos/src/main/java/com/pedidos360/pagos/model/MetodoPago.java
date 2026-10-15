package com.pedidos360.pagos.model;

/**
 * Métodos de pago aceptados por la tienda (simulados: no hay pasarela real).
 * Los mismos valores usa ms-carrito en el pedido.
 */
public enum MetodoPago {
    TARJETA_CREDITO("Tarjeta de crédito"),
    TARJETA_DEBITO("Tarjeta de débito"),
    TRANSFERENCIA("Transferencia");

    private final String etiqueta;

    MetodoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Convierte el valor enviado por JSON (mayúsculas, minúsculas o con guiones). */
    public static MetodoPago desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe indicar un método de pago");
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        try {
            return valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Método de pago no válido: " + valor);
        }
    }
}
