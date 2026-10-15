package com.pedidos360.rabbitadmin.error;

/** El recurso pedido (cola, exchange o binding) no existe en el broker. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
