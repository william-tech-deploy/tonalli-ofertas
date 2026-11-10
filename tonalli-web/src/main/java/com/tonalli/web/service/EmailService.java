package com.tonalli.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envía el correo de verificación mediante un servicio SMTP de terceros (se configura con las variables
 * SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME y SPRING_MAIL_PASSWORD).
 * Si no hay SMTP configurado (desarrollo), el enlace se escribe en el registro del servidor.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> remitente;
    private final String desde;
    private final String baseUrl;
    private final String smtpHost;

    public EmailService(ObjectProvider<JavaMailSender> remitente,
                        @Value("${tonalli.mail.remitente:no-reply@tonalli.local}") String desde,
                        @Value("${tonalli.base-url:http://localhost:8080}") String baseUrl,
                        @Value("${spring.mail.host:}") String smtpHost) {
        this.remitente = remitente;
        this.desde = desde;
        this.baseUrl = baseUrl;
        this.smtpHost = smtpHost;
    }

    public void enviarVerificacion(String destino, String nombre, String token) {
        String enlace = baseUrl + "/verificar?token=" + token;
        JavaMailSender sender = remitente.getIfAvailable();
        // Una variable de entorno vacía crea el sender igual; por eso también se mira que haya host.
        if (sender == null || smtpHost.isBlank()) {
            log.warn("SMTP no configurado (solo desarrollo). Enlace de verificación para {}: {}", destino, enlace);
            return;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(desde);
            mensaje.setTo(destino);
            mensaje.setSubject("Verifica tu correo en Tonalli");
            mensaje.setText("Hola " + nombre + ",\n\nConfirma tu correo con este enlace (vale 24 horas):\n"
                    + enlace + "\n\nSi no creaste una cuenta en Tonalli, ignora este mensaje.");
            sender.send(mensaje);
        } catch (MailException e) {
            // No se registra el enlace ni el token; solo el tipo de fallo.
            log.error("No se pudo enviar el correo de verificación a {}: {}", destino, e.getClass().getSimpleName());
        }
    }
}
