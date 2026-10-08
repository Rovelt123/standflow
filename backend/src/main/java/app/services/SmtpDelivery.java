package app.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@FunctionalInterface
public interface SmtpDelivery {
    void send(MimeMessage message) throws MessagingException;
}