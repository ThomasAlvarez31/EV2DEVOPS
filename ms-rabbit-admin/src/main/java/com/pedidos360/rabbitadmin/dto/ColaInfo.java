package com.pedidos360.rabbitadmin.dto;

/**
 * Informacion de una cola expuesta por la API.
 *
 * messages/consumers vienen del broker (queue.declare pasivo); durable, autoDelete
 * y arguments vienen de la API de gestion cuando esta disponible.
 */
public record ColaInfo(
        String name,
        Integer messages,
        Integer consumers,
        Boolean durable,
        Boolean autoDelete,
        java.util.Map<String, Object> arguments
) {
}
