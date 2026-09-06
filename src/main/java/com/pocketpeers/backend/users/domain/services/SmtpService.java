package com.pocketpeers.backend.users.domain.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpService.class);

    @Autowired
    private JavaMailSender emailSender;

    /**
     * Cuenta que se autentica contra el servidor SMTP. Es tambien el remitente
     * real de todo correo que salga de aqui.
     *
     * <p>Con Gmail no se puede enviar "desde" una direccion distinta a la que se
     * autentica: si se declara otra, el servidor la reescribe. Por eso el
     * remitente se toma de aqui y no de una propiedad aparte.</p>
     */
    @Value("${spring.mail.username:}")
    private String senderAddress;

    /** Nombre visible del remitente, lo unico que Gmail si permite personalizar. */
    @Value("${app.mail.display-name:PocketPeers}")
    private String senderDisplayName;

    public void sendWelcomeEmail(String to, String name) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        applySender(helper);
        helper.setTo(to);
        helper.setSubject("¡Bienvenido a Pockets Partner!");
        helper.setText(buildHtmlMessage(name), true);
        emailSender.send(message);
    }

    /**
     * Fija el remitente con un nombre legible en vez de la direccion cruda.
     *
     * <p>Quien recibe ve "PocketPeers" y no "algo.contacto@gmail.com", que en un
     * correo con un codigo de seguridad es la diferencia entre parecer legitimo y
     * parecer un intento de estafa.</p>
     */
    private void applySender(MimeMessageHelper helper) throws MessagingException {
        if (senderAddress == null || senderAddress.isBlank()) {
            // Sin cuenta configurada no se fija remitente: dejar que falle el
            // envio con el error real de SMTP es mas informativo que fallar aqui
            // con un mensaje sobre direcciones mal formadas.
            return;
        }
        try {
            helper.setFrom(senderAddress, senderDisplayName);
        } catch (java.io.UnsupportedEncodingException exception) {
            helper.setFrom(senderAddress);
        }
    }

    /**
     * Envia el codigo para restablecer la contrasena.
     *
     * <p>El correo dice explicitamente que hacer si la persona no pidio el
     * cambio. Un mensaje de recuperacion que no explica eso deja a quien lo
     * recibe sin saber si su cuenta esta comprometida.</p>
     */
    public void sendPasswordResetEmail(String to, String name, String code) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        applySender(helper);
        helper.setTo(to);
        helper.setSubject("Codigo para restablecer tu contrasena");
        helper.setText(buildPasswordResetMessage(name, code), true);
        emailSender.send(message);
    }

    /**
     * Envia el codigo en segundo plano.
     *
     * <p>Quien pide el codigo no tiene que esperar a que Gmail acepte el correo:
     * el endpoint ignora los fallos de envio a proposito, para que su respuesta
     * sea identica exista o no la cuenta. Si el resultado no cambia nada de lo
     * que se responde, esperarlo solo deja la pantalla girando.</p>
     *
     * <p>Va en esta clase y no en quien la llama porque {@code @Async} solo surte
     * efecto cuando la llamada cruza el proxy de Spring: invocar un metodo
     * anotado desde la misma clase lo ejecuta de forma sincrona, sin avisar.</p>
     */
    @Async
    public void sendPasswordResetEmailAsync(String to, String name, String code) {
        try {
            sendPasswordResetEmail(to, name, code);
            LOGGER.info("Password reset email sent to {}", mask(to));
        } catch (Exception exception) {
            // Este log es el unico rastro del fallo: la respuesta HTTP fue 200
            // antes de intentar el envio.
            LOGGER.error("Could not send password reset email to {}. cause={}",
                    mask(to), exception.getMessage(), exception);
        }
    }

    /** Oculta el correo en los logs: no hace falta guardarlo entero para diagnosticar. */
    private static String mask(String email) {
        if (email == null) {
            return "(null)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String buildPasswordResetMessage(String name, String code) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; background:#F4F7F8; padding:24px;'>" +
                "<div style='max-width:520px; margin:auto; background:#ffffff; border:1px solid #D8E1E7;" +
                " border-radius:8px; padding:28px;'>" +
                "<h2 style='color:#0B2545; margin:0 0 12px;'>Hola " + name + ",</h2>" +
                "<p style='color:#334; line-height:1.6; margin:0 0 20px;'>" +
                "Recibimos una solicitud para restablecer tu contrasena en PocketPeers. " +
                "Ingresa este codigo en la aplicacion:</p>" +
                "<div style='text-align:center; margin:24px 0;'>" +
                "<span style='display:inline-block; font-size:34px; font-weight:bold; letter-spacing:10px;" +
                " color:#134074; background:#F4F7F8; border:1px solid #D8E1E7; border-radius:8px;" +
                " padding:16px 24px;'>" + code + "</span>" +
                "</div>" +
                "<p style='color:#556; line-height:1.6; margin:0 0 8px;'>" +
                "El codigo vence en " +
                com.pocketpeers.backend.users.domain.model.entities.PasswordResetCode.EXPIRATION_MINUTES +
                " minutos y solo se puede usar una vez.</p>" +
                "<p style='color:#556; line-height:1.6; margin:16px 0 0;'>" +
                "<strong>Si no pediste este cambio</strong>, puedes ignorar este correo: tu contrasena " +
                "actual sigue funcionando y nadie puede cambiarla sin este codigo.</p>" +
                "<hr style='border:0; border-top:1px solid #D8E1E7; margin:24px 0;'>" +
                "<p style='font-size:12px; color:#8a97a0; margin:0;'>Equipo de PocketPeers</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String buildHtmlMessage(String name) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: auto; border: 1px solid #ddd; border-radius: 5px; padding: 20px;'>" +
                "<h1 style='color: #c682ff;'>Hola " + name + ",</h1>" +
                "<p>¡Bienvenido a <strong>Pockets Partner</strong>!</p>" +
                "<p>Nos alegra tenerte con nosotros. Explora nuestra plataforma y aprovecha todas las oportunidades que ofrecemos.</p>" +
                "<p style='font-weight: bold;'>¡Saludos!</p>" +
                "<p>El equipo de Pockets Partner</p>" +
                "<footer style='margin-top: 20px;'>" +
                "<p style='font-size: 12px; color: #aaa;'>Si tienes alguna pregunta, no dudes en contactarnos.</p>" +
                "</footer>" +
                "</div>" +
                "</body>" +
                "</html>";
    }


}
