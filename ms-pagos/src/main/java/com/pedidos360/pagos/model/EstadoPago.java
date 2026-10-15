package com.pedidos360.pagos.model;

/**
 * Resultado del pago. Este microservicio es el dueño de esta información:
 * ms-carrito solo lo consume para saber si el pago fue aprobado o rechazado.
 */
public enum EstadoPago {
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado");

    private final String etiqueta;

    EstadoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Convierte el valor enviado por JSON (mayúsculas o no) al enum. */
    public static EstadoPago desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe indicar un estado de pago");
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de pago no válido: " + valor);
        }
    }
}
