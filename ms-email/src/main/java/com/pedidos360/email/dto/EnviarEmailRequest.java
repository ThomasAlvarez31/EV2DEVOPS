package com.pedidos360.email.dto;

public record EnviarEmailRequest(
        String destino,
        String asunto,
        String cuerpo
) {
}
