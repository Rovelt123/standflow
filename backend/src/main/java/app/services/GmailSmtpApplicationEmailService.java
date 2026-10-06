package app.services;

import app.entities.Application;
import app.exceptions.ApiException;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class GmailSmtpApplicationEmailService implements ApplicationEmailService {

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;
    private static final String USERNAME_ENV = "GMAIL_USERNAME";
    private static final String PASSWORD_ENV = "GMAIL_APP_PASSWORD";
    private static final String RECEIVER_ENV = "APPLICATION_RECEIVER_EMAIL";

    //--------------------------------------------------------------

    @Override
    public void sendApplication(Application application, byte[] pdfAttachment, String attachmentFilename) {
        try {
            String username = requiredEnv(USERNAME_ENV);
            String password = requiredEnv(PASSWORD_ENV);
            String receiver = requiredEnv(RECEIVER_ENV);

            MimeMessage message = new MimeMessage(createSession(username, password));
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(receiver));
            message.setSubject("Ny standansoegning fra " + application.getCompany(), StandardCharsets.UTF_8.name());
            message.setContent(createContent(application, pdfAttachment, attachmentFilename));

            Transport.send(message);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Ansogningen kunne ikke sendes via e-mail.");
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

    private MimeMultipart createContent(Application application, byte[] pdfAttachment, String attachmentFilename)
            throws MessagingException {
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("Der er modtaget en ny standansoegning fra " + application.getCompany() + ".",
                StandardCharsets.UTF_8.name());

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(pdfAttachment, "application/pdf")));
        attachmentPart.setFileName(attachmentFilename);

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachmentPart);
        return multipart;
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
