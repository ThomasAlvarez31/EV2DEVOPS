package com.pedidos360.rabbitadmin.error;

/** El broker RabbitMQ no responde o la API de gestion (15672) no esta disponible. */
public class BrokerNoDisponibleException extends RuntimeException {

    public BrokerNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public BrokerNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
