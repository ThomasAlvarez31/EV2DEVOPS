package com.pedidos360.carrito.model;

import java.util.Arrays;

/**
 * Metodos de pago ofrecidos en el checkout. Todos se procesan con una
 * pasarela SIMULADA: no se conecta a bancos ni se almacena ningun dato
 * financiero real.
 */
public enum MetodoPago {
    TARJETA_CREDITO("Tarjeta de crédito"),
    TARJETA_DEBITO("Tarjeta de débito"),
    TRANSFERENCIA("Transferencia bancaria");

    private final String etiqueta;

    MetodoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el valor enviado por el frontend (enum o etiqueta legible)
     * en MetodoPago. Lanza IllegalArgumentException si no es valido.
     */
    public static MetodoPago desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe seleccionar un método de pago");
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        return Arrays.stream(values())
                .filter(m -> m.name().equals(normalizado) || m.etiqueta.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no válido: " + valor));
    }
}
