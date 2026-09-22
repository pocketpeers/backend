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

    /**
     * Juego de caracteres de los correos que salen de aqui.
     *
     * <p>Sin declararlo, el Content-Type sale sin charset y cada cliente de
     * correo adivina: los textos en español llegan con "contrase&#xC3;&#xB1;a"
     * en vez de "contraseña". Fijarlo es la unica forma de que la eñe y las
     * tildes se vean igual en Gmail, Outlook y el resto.</p>
     */
    private static final String MAIL_ENCODING = "UTF-8";

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

    /**
     * Da la bienvenida a quien acaba de completar su registro.
     *
     * <p>Llega despues de confirmar el codigo, no antes: hasta ese momento la
     * cuenta no existe y felicitar a alguien por algo que todavia no ocurrio
     * solo confunde.</p>
     */
    public void sendWelcomeEmail(String to, String name) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, MAIL_ENCODING);
        applySender(helper);
        helper.setTo(to);
        helper.setSubject("¡Bienvenido a PocketPeers, " + name + "!");
        helper.setText(buildWelcomeMessage(name), true);
        emailSender.send(message);
    }

    /**
     * Envia la bienvenida en segundo plano.
     *
     * <p>Aqui importa mas que en los otros dos correos: este sale justo despues
     * de crear la cuenta, dentro de la misma peticion que devuelve el alta. Si
     * se esperara al servidor de correo, un Gmail lento retrasaria el registro;
     * y si fallara, tumbaria la respuesta de una cuenta que si se creo. Un
     * correo de cortesia nunca debe poder estropear eso.</p>
     */
    @Async
    public void sendWelcomeEmailAsync(String to, String name) {
        try {
            sendWelcomeEmail(to, name);
            LOGGER.info("Welcome email sent to {}", mask(to));
        } catch (Exception exception) {
            LOGGER.error("Could not send welcome email to {}. cause={}",
                    mask(to), exception.getMessage(), exception);
        }
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
        MimeMessageHelper helper = new MimeMessageHelper(message, true, MAIL_ENCODING);
        applySender(helper);
        helper.setTo(to);
        helper.setSubject("Código para restablecer tu contraseña");
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

    /**
     * Envia el codigo que verifica el correo de un alta.
     *
     * <p>A diferencia del de recuperacion, este llega a alguien que todavia no
     * tiene cuenta. Por eso dice de forma explicita que, si no se registro,
     * puede ignorarlo: no se ha creado nada a su nombre y sin el codigo nadie
     * podra crearlo.</p>
     */
    public void sendSignUpVerificationEmail(String to, String name, String code) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, MAIL_ENCODING);
        applySender(helper);
        helper.setTo(to);
        helper.setSubject("Confirma tu correo en PocketPeers");
        helper.setText(buildSignUpVerificationMessage(name, code), true);
        emailSender.send(message);
    }

    /**
     * Envia el codigo de verificacion en segundo plano.
     *
     * <p>Mismo motivo que en la recuperacion: la respuesta del endpoint no
     * depende de que el correo salga, asi que esperar al servidor de correo solo
     * dejaria la pantalla girando. Va en esta clase porque {@code @Async} solo
     * surte efecto cuando la llamada cruza el proxy de Spring.</p>
     */
    @Async
    public void sendSignUpVerificationEmailAsync(String to, String name, String code) {
        try {
            sendSignUpVerificationEmail(to, name, code);
            LOGGER.info("Sign-up verification email sent to {}", mask(to));
        } catch (Exception exception) {
            LOGGER.error("Could not send sign-up verification email to {}. cause={}",
                    mask(to), exception.getMessage(), exception);
        }
    }

    private String buildSignUpVerificationMessage(String name, String code) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; background:#F4F7F8; padding:24px;'>" +
                "<div style='max-width:520px; margin:auto; background:#ffffff; border:1px solid #D8E1E7;" +
                " border-radius:8px; padding:28px;'>" +
                "<h2 style='color:#0B2545; margin:0 0 12px;'>Hola " + name + ",</h2>" +
                "<p style='color:#334; line-height:1.6; margin:0 0 20px;'>" +
                "Gracias por registrarte en PocketPeers. Para terminar de crear tu cuenta, " +
                "ingresa este código en la aplicación:</p>" +
                "<div style='text-align:center; margin:24px 0;'>" +
                "<span style='display:inline-block; font-size:34px; font-weight:bold; letter-spacing:10px;" +
                " color:#134074; background:#F4F7F8; border:1px solid #D8E1E7; border-radius:8px;" +
                " padding:16px 24px;'>" + code + "</span>" +
                "</div>" +
                "<p style='color:#556; line-height:1.6; margin:0 0 8px;'>" +
                "El código vence en " +
                com.pocketpeers.backend.users.domain.model.entities.PendingRegistration.EXPIRATION_MINUTES +
                " minutos y solo se puede usar una vez.</p>" +
                "<p style='color:#556; line-height:1.6; margin:16px 0 0;'>" +
                "<strong>Si no te registraste</strong>, puedes ignorar este correo: no se ha creado " +
                "ninguna cuenta con tu dirección y sin este código nadie podrá crearla.</p>" +
                "<hr style='border:0; border-top:1px solid #D8E1E7; margin:24px 0;'>" +
                "<p style='font-size:12px; color:#8a97a0; margin:0;'>Equipo de PocketPeers</p>" +
                "</div>" +
                "</body>" +
                "</html>";
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
                "Recibimos una solicitud para restablecer tu contraseña en PocketPeers. " +
                "Ingresa este código en la aplicación:</p>" +
                "<div style='text-align:center; margin:24px 0;'>" +
                "<span style='display:inline-block; font-size:34px; font-weight:bold; letter-spacing:10px;" +
                " color:#134074; background:#F4F7F8; border:1px solid #D8E1E7; border-radius:8px;" +
                " padding:16px 24px;'>" + code + "</span>" +
                "</div>" +
                "<p style='color:#556; line-height:1.6; margin:0 0 8px;'>" +
                "El código vence en " +
                com.pocketpeers.backend.users.domain.model.entities.PasswordResetCode.EXPIRATION_MINUTES +
                " minutos y solo se puede usar una vez.</p>" +
                "<p style='color:#556; line-height:1.6; margin:16px 0 0;'>" +
                "<strong>Si no pediste este cambio</strong>, puedes ignorar este correo: tu contraseña " +
                "actual sigue funcionando y nadie puede cambiarla sin este código.</p>" +
                "<hr style='border:0; border-top:1px solid #D8E1E7; margin:24px 0;'>" +
                "<p style='font-size:12px; color:#8a97a0; margin:0;'>Equipo de PocketPeers</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    /**
     * El correo de bienvenida.
     *
     * <p>Reemplaza a uno anterior que hablaba de "Pockets Partner" —el nombre
     * viejo del proyecto—, usaba una paleta que no es la de la aplicacion y no
     * decia nada concreto: "explora nuestra plataforma y aprovecha todas las
     * oportunidades" no le dice a nadie que puede hacer. Ademas no lo enviaba
     * nadie: el metodo existia sin que ninguna ruta lo llamara.</p>
     *
     * <p>Esta version esta escrita para quien va a usar esto de verdad: alguien
     * que lleva sus juntas en un cuaderno y quiza nunca instalo una aplicacion
     * de dinero. De ahi las tres decisiones de redaccion:</p>
     *
     * <ul>
     *   <li><b>Cuatro pasos y se acaba.</b> Una lista larga en un correo de
     *       bienvenida no se lee; se cierra.</li>
     *   <li><b>Ni una palabra tecnica.</b> No dice "blockchain" ni "anclaje":
     *       dice que queda anotado donde nadie lo puede cambiar, que es lo que
     *       de verdad significa para quien lo lee. Tampoco dice "registro
     *       publico", aunque la cadena lo sea: quien lee eso entiende que sus
     *       cuentas se ven desde fuera, y lo unico que viaja a la cadena son
     *       huellas criptograficas. Una palabra exacta que se malinterpreta es
     *       peor que una aproximada que se entiende.</li>
     *   <li><b>Termina con una sola accion.</b> Crear el primer grupo. Un
     *       correo que sugiere cinco cosas a la vez no consigue ninguna.</li>
     * </ul>
     */
    private String buildWelcomeMessage(String name) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; background:#F4F7F8; padding:24px;'>" +
                "<div style='max-width:520px; margin:auto; background:#ffffff; border:1px solid #D8E1E7;" +
                " border-radius:8px; padding:28px;'>" +

                "<h2 style='color:#0B2545; margin:0 0 12px;'>¡Hola " + name + "! Tu cuenta ya está lista</h2>" +

                "<p style='color:#334; line-height:1.6; margin:0 0 20px;'>" +
                "PocketPeers te ayuda a llevar la cuenta de los gastos que compartes con tu junta, " +
                "tu familia o tus compañeros de trabajo. Sin cuadernos y sin discutir quién puso qué.</p>" +

                "<p style='color:#0B2545; font-weight:bold; margin:0 0 12px;'>Esto es lo que puedes hacer:</p>" +

                welcomeStep("1", "Arma tu grupo",
                        "Invita a las personas con las que compartes gastos. Cada una entra desde su propio celular.") +
                welcomeStep("2", "Anota los gastos",
                        "Registra cuánto fue y entre quiénes se reparte. Si le tomas foto al recibo, la aplicación lee el monto sola.") +
                welcomeStep("3", "Registra tus pagos",
                        "Paga todo de una vez o de a pocos. Quien puso el dinero confirma que lo recibió, y queda anotado.") +
                welcomeStep("4", "Construye tu historial",
                        "Cada pago que cumples a tiempo suma a tu puntaje, y queda guardado de una forma que nadie puede modificar después. Ni nosotros.") +

                "<div style='background:#F4F7F8; border-left:4px solid #134074; padding:14px 16px; margin:24px 0;'>" +
                "<p style='color:#334; line-height:1.6; margin:0;'>" +
                "Ese último punto es el que más importa: si algún día necesitas demostrarle a alguien " +
                "que cumples con tus pagos, vas a tener con qué.</p>" +
                "</div>" +

                "<p style='color:#334; line-height:1.6; margin:0 0 4px;'>" +
                "<strong>¿Por dónde empiezo?</strong> Abre la aplicación y crea tu primer grupo. " +
                "Toma menos de un minuto.</p>" +

                "<hr style='border:0; border-top:1px solid #D8E1E7; margin:24px 0;'>" +
                "<p style='font-size:12px; color:#8a97a0; margin:0;'>" +
                "PocketPeers no guarda tu dinero ni te presta: solo lleva la cuenta de los acuerdos " +
                "que ya tienes con tu gente.</p>" +
                "<p style='font-size:12px; color:#8a97a0; margin:8px 0 0;'>Equipo de PocketPeers</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    /** Un paso de la lista de bienvenida, con su numero en un circulo. */
    private String welcomeStep(String number, String title, String description) {
        return "<table role='presentation' cellpadding='0' cellspacing='0' style='margin:0 0 16px; width:100%;'>" +
                "<tr>" +
                "<td style='width:34px; vertical-align:top;'>" +
                "<div style='width:26px; height:26px; border-radius:13px; background:#134074; color:#ffffff;" +
                " text-align:center; line-height:26px; font-weight:bold; font-size:14px;'>" + number + "</div>" +
                "</td>" +
                "<td style='vertical-align:top;'>" +
                "<p style='color:#0B2545; font-weight:bold; margin:0 0 2px;'>" + title + "</p>" +
                "<p style='color:#556; line-height:1.5; margin:0; font-size:14px;'>" + description + "</p>" +
                "</td>" +
                "</tr>" +
                "</table>";
    }


}
