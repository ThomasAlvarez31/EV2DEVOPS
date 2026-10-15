package com.pedidos360.carrito.dto;

/**
 * Cuerpo del pago simulado.
 *
 * simularRechazo es un control de la DEMO: permite forzar el rechazo para
 * mostrar el camino de error sin conectarse a ningun banco. No representa
 * datos de una tarjeta real y no se almacena ningun dato financiero.
 */
public record PagoRequest(Boolean simularRechazo) {

    public boolean esRechazoSimulado() {
        return Boolean.TRUE.equals(simularRechazo);
    }
}
