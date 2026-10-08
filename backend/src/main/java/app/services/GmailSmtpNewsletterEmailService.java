package app.services;

import app.entities.User;
import app.exceptions.ApiException;
import app.configs.SmtpConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

public class GmailSmtpNewsletterEmailService implements NewsletterEmailService {

    private final Supplier<SmtpConfig> configuration;
    private final SmtpDelivery delivery;

    //--------------------------------------------------------------

    public GmailSmtpNewsletterEmailService() {
        this(SmtpConfig::load, Transport::send);
    }

    //--------------------------------------------------------------

    public GmailSmtpNewsletterEmailService(Supplier<SmtpConfig> configuration, SmtpDelivery delivery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.delivery = Objects.requireNonNull(delivery);
    }

    //--------------------------------------------------------------

    @Override
    public void sendNewsletter(User recipient, String subject, String body) {
        try {
            SmtpConfig config = configuration.get();

            MimeMessage email = new MimeMessage(config.session());
            email.setFrom(SmtpConfig.address(config.required("GMAIL_USERNAME")));
            email.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    new jakarta.mail.Address[]{SmtpConfig.address(recipient.getEmail())});
            email.setSubject(subject, StandardCharsets.UTF_8.name());
            email.setText(body, StandardCharsets.UTF_8.name());

            delivery.send(email);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Nyhedsbrevet kunne ikke sendes via e-mail.");
        }
    }

}
