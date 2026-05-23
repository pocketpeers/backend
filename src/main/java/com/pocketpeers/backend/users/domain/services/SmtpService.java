package com.pocketpeers.backend.users.domain.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpService {

    @Autowired
    private JavaMailSender emailSender;

    public void sendWelcomeEmail(String to, String name) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(to);
        helper.setSubject("¡Bienvenido a Pockets Partner!");
        helper.setText(buildHtmlMessage(name), true);
        emailSender.send(message);
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
