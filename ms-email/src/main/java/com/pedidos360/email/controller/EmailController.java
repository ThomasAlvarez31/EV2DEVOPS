package com.pedidos360.email.controller;

import com.pedidos360.email.dto.EnviarEmailRequest;
import com.pedidos360.email.service.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Envío manual de correos (pruebas / operación).
 * El flujo normal del sistema no pasa por aquí: ms-carrito publica el evento
 * en RabbitMQ y el EmailListener lo consume y envía de forma asíncrona.
 */
@RestController
@RequestMapping("/emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/enviar")
    public ResponseEntity<String> enviarCorreo(@RequestBody EnviarEmailRequest request) {
        try {
            emailService.enviarCorreo(request.destino(), request.asunto(), request.cuerpo());
            return ResponseEntity.ok("¡Correo enviado exitosamente a " + request.destino() + "!");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Error al enviar el correo: " + ex.getMessage());
        }
    }
}
