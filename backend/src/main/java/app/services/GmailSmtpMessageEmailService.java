package app.services;

import app.entities.Message;
import app.entities.User;
import app.exceptions.ApiException;
import app.utils.Utils;
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class GmailSmtpMessageEmailService implements MessageEmailService {

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;
    private static final String USERNAME_ENV = "GMAIL_USERNAME";
    private static final String PASSWORD_ENV = "GMAIL_APP_PASSWORD";
    private static final String FRONTEND_URL_PROPERTY = "FRONTEND_URL";

    //--------------------------------------------------------------

    @Override
    public void sendAdminMessageNotification(User recipient, Message message) {
        try {
            String username = requiredEnv(USERNAME_ENV);
            String password = requiredEnv(PASSWORD_ENV);

            MimeMessage email = new MimeMessage(createSession(username, password));
            email.setFrom(new InternetAddress(username));
            email.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    InternetAddress.parse(recipient.getEmail()));
            email.setSubject("Ny besked i StandFlow", StandardCharsets.UTF_8.name());
            email.setText(body(), StandardCharsets.UTF_8.name());

            Transport.send(email);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Beskednotifikationen kunne ikke sendes via e-mail.");
        }
    }

    //--------------------------------------------------------------

    private Session createSession(String username, String password) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", SMTP_HOST);
        properties.put("mail.smtp.port", String.valueOf(SMTP_PORT));
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");

        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    //--------------------------------------------------------------

    private String body() {
        return "Du har modtaget en ny besked.\n\n"
                + "Log ind for at laese og svare paa beskeden:\n"
                + frontendUrl() + "/messages";
    }

    //--------------------------------------------------------------

    private String frontendUrl() {
        String value = Utils.getPropertyValue(FRONTEND_URL_PROPERTY, "config.properties");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    //--------------------------------------------------------------

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(500, "SMTP-konfiguration mangler.");
        }
        return value.trim();
    }
}
