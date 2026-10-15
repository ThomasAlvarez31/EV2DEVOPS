package com.pedidos360.rabbitadmin.error;

/** Error de negocio: la solicitud es invalida y no llego a tocar el broker. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
