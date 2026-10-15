package com.pedidos360.pagos.dto;

/**
 * Solicitud que envía ms-carrito a POST /pagos.
 *
 * El pedido ya fue validado (pertenece al usuario y está pendiente de pago)
 * en ms-carrito; aquí llega el monto definitivo calculado por el servidor.
 */
public record SolicitudPago(Long pedidoId,
                            String usuarioId,
                            Double monto,
                            String metodoPago,
                            Boolean simularRechazo) {

    public boolean esRechazoSimulado() {
        return Boolean.TRUE.equals(simularRechazo);
    }
}
