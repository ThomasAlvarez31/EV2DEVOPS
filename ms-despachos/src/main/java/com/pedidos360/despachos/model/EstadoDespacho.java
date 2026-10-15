package com.pedidos360.despachos.model;

import java.util.Arrays;
import java.util.Set;

/**
 * Estados del ciclo de vida de un DESPACHO (envio de un pedido).
 *
 * PENDIENTE   -> pago aprobado, el envio esta por prepararse
 * EN_RUTA     -> el pedido salio hacia el cliente
 * ENTREGADO   -> el cliente recibio el pedido
 * CANCELADO   -> anulacion manual del envio (solo antes de salir)
 *
 * El despacho SE CREA a partir del evento PAGO_APROBADO y avanza con los
 * cambios de estado del pedido; tambien puede avanzarse manualmente por la
 * API (area de logistica).
 */
public enum EstadoDespacho {
    PENDIENTE("Pendiente"),
    EN_RUTA("En ruta"),
    ENTREGADO("Entregado"),
    CANCELADO("Cancelado");

    private final String etiqueta;

    EstadoDespacho(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Transiciones permitidas de un despacho por la API.
     *
     * Solo se admite avanzar o cancelar antes de salir: un envio entregado
     * ya no se puede mover.
     */
    public static Set<EstadoDespacho> transicionesDesde(EstadoDespacho actual) {
        if (actual == null) {
            return Set.of();
        }
        return switch (actual) {
            case PENDIENTE -> Set.of(EN_RUTA, ENTREGADO, CANCELADO);
            case EN_RUTA -> Set.of(ENTREGADO);
            case ENTREGADO, CANCELADO -> Set.of();
        };
    }

    /**
     * Traduce un estado logistico del pedido al estado del despacho.
     *
     * @param estadoPedido estado publicado en el evento (ENVIADO, ENTREGADO...)
     * @return el estado del despacho o null si el estado no define el envio
     */
    public static EstadoDespacho desdeEstadoPedido(String estadoPedido) {
        if (estadoPedido == null || estadoPedido.isBlank()) {
            return null;
        }
        return switch (estadoPedido.trim().toUpperCase().replace('-', '_')) {
            case "ENVIADO" -> EN_RUTA;
            case "ENTREGADO" -> ENTREGADO;
            default -> null;
        };
    }

    /**
     * Convierte el valor enviado por el cliente (enum o etiqueta legible).
     * Lanza IllegalArgumentException si no es valido.
     */
    public static EstadoDespacho desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el estado del despacho");
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        return Arrays.stream(values())
                .filter(e -> e.name().equals(normalizado) || e.etiqueta.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de despacho no valido: " + valor));
    }
}
