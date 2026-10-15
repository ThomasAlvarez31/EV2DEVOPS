package com.pedidos360.email.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    /** Remitente configurado en application.yml (MAIL_FROM). */
    @Value("${pedidos360.email.from}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envia un correo de texto plano por SMTP.
     * Lanza IllegalArgumentException si el destino falta y
     * org.springframework.mail.MailException si el servidor SMTP rechaza el envio.
     */
    public void enviarCorreo(String destino, String asunto, String cuerpo) {
        validar(destino, asunto);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destino);
        message.setSubject(asunto);
        message.setText(cuerpo);
        message.setFrom(from);

        mailSender.send(message);
        log.info("Correo enviado a {} (asunto: {})", destino, asunto);
    }

    /**
     * Envia un correo en formato HTML (plantillas de Pedidos360).
     * Si el SMTP no esta disponible lanza org.springframework.mail.MailException,
     * igual que el envio de texto plano: quien lo llama decide el reintento.
     */
    public void enviarHtml(String destino, String asunto, String contenidoHtml) {
        validar(destino, asunto);

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(destino);
            helper.setSubject(asunto);
            helper.setText(contenidoHtml, true);
            mailSender.send(mensaje);
            log.info("Correo HTML enviado a {} (asunto: {})", destino, asunto);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("No se pudo componer el correo HTML: " + e.getMessage(), e);
        }
    }

    /** Detecta si el cuerpo viene como HTML (plantilla) o como texto plano. */
    public static boolean esHtml(String cuerpo) {
        if (cuerpo == null) {
            return false;
        }
        String inicio = cuerpo.strip().toLowerCase();
        return inicio.startsWith("<!doctype") || inicio.startsWith("<html") || inicio.startsWith("<div");
    }

    private void validar(String destino, String asunto) {
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("El destino del correo es obligatorio");
        }
        if (asunto == null || asunto.isBlank()) {
            throw new IllegalArgumentException("El asunto del correo es obligatorio");
        }
    }
}
