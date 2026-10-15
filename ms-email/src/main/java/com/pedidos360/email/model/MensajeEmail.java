package com.pedidos360.email.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mensaje que viaja por la cola RabbitMQ pedidos360.emails.
 *
 * El productor publica un evento de NEGOCIO enriquecido (pedidoId, items,
 * estado, total, fecha, usuarioId) que ms-email no necesita: solo se leen
 * estos cuatro campos y los demas se ignoran. Es decir, el consumidor hace
 * parseo directo del JSON sin acoplarse a las clases del productor
 * (com.pedidos360.carrito.messaging.EventoNegocio).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MensajeEmail(
        String destino,
        String asunto,
        String cuerpo,
        String tipo
) {
}
