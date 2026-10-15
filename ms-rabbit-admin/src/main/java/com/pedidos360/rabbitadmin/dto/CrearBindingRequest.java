package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Solicitud para crear un binding (enlace cola <-> exchange).
 *
 * Los tres campos son obligatorios y validados: un binding sin cola, sin
 * exchange o con routing key invalida no tiene sentido y se rechaza con un
 * mensaje claro antes de llegar al broker.
 */
public record CrearBindingRequest(

        @NotBlank(message = "la cola de destino del binding es obligatoria")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "el nombre de la cola debe tener entre 1 y 255 caracteres y solo puede usar letras, numeros, '.', '_' o '-'")
        String queue,

        @NotBlank(message = "el exchange de origen del binding es obligatorio")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "el nombre del exchange debe tener entre 1 y 255 caracteres y solo puede usar letras, numeros, '.', '_' o '-'")
        String exchange,

        @NotBlank(message = "la routing key del binding es obligatoria")
        @Pattern(
                regexp = "^[a-zA-Z0-9._:*/-]{1,255}$",
                message = "la routing key debe tener entre 1 y 255 caracteres y solo puede usar letras, numeros, '.', '_', ':', '*', '/' o '-'")
        String routingKey,

        @Size(max = 20, message = "como maximo se admiten 20 argumentos de binding")
        Map<String, Object> arguments
) {
}
