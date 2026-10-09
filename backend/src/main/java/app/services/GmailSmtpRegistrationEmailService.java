package app.services;

import app.configs.SmtpConfig;
import app.entities.User;
import app.exceptions.ApiException;
import app.utils.Utils;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

public class GmailSmtpRegistrationEmailService implements RegistrationEmailService {

    private final Supplier<SmtpConfig> configuration;
    private final SmtpDelivery delivery;

    //--------------------------------------------------------------

    public GmailSmtpRegistrationEmailService() {
        this(SmtpConfig::load, Transport::send);
    }

    //--------------------------------------------------------------

    public GmailSmtpRegistrationEmailService(Supplier<SmtpConfig> configuration, SmtpDelivery delivery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.delivery = Objects.requireNonNull(delivery);
    }

    //--------------------------------------------------------------

    @Override
    public void sendWelcome(User recipient) {
        try {
            SmtpConfig config = configuration.get();
            MimeMessage email = new MimeMessage(config.session());
            email.setFrom(SmtpConfig.address(config.required("GMAIL_USERNAME")));
            email.setRecipients(Message.RecipientType.TO,
                    new jakarta.mail.Address[]{SmtpConfig.address(recipient.getEmail())});
            email.setSubject("Velkommen til StandFlow", StandardCharsets.UTF_8.name());
            email.setText("Hej " + recipient.getFirstname() + "\n\n"
                    + "Din konto hos StandFlow er nu oprettet.\n\n"
                    + "Du kan logge ind her:\n"
                    + Utils.getPropertyValue("FRONTEND_URL", "config.properties") + "\n\n"
                    + "Venlig hilsen\nStandFlow", StandardCharsets.UTF_8.name());
            delivery.send(email);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Velkomstmailen kunne ikke sendes.");
        }
    }
}
