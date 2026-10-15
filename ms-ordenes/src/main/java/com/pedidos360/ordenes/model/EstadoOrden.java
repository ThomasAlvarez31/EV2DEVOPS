package com.pedidos360.ordenes.model;

import java.util.Arrays;
import java.util.Set;

/**
 * Estados del ciclo de vida de una ORDEN de compra (comprobante formal).
 *
 * REGISTRADA -> el pedido quedo registrado y el pago aun no se resuelve
 * PAGADA     -> el pago fue aprobado
 * CANCELADA  -> el pago fue rechazado
 * ANULADA    -> anulacion manual desde la API (solo desde REGISTRADA o PAGADA)
 *
 * La orden SE CREA a partir de un evento de RabbitMQ: nadie hace POST para
 * crearla. El estado logistico del pedido (EN_PREPARACION, ENVIADO,
 * ENTREGADO...) se guarda aparte en {@code estadoPedido}.
 */
public enum EstadoOrden {
    REGISTRADA("Registrada"),
    PAGADA("Pagada"),
    CANCELADA("Cancelada"),
    ANULADA("Anulada");

    private final String etiqueta;

    EstadoOrden(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Transiciones permitidas de una orden por la API.
     *
     * Solo se admite la anulacion manual: los demas cambios llegan desde
     * RabbitMQ (pago aprobado, pago rechazado).
     */
    public static Set<EstadoOrden> transicionesDesde(EstadoOrden actual) {
        if (actual == null) {
            return Set.of();
        }
        return switch (actual) {
            case REGISTRADA, PAGADA -> Set.of(ANULADA);
            case CANCELADA, ANULADA -> Set.of();
        };
    }

    /**
     * Convierte el valor enviado por el cliente (enum o etiqueta legible).
     * Lanza IllegalArgumentException si no es valido.
     */
    public static EstadoOrden desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el estado de la orden");
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        return Arrays.stream(values())
                .filter(e -> e.name().equals(normalizado) || e.etiqueta.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de orden no valido: " + valor));
    }
}
