package app.services;

import app.entities.Message;
import app.entities.User;
import app.exceptions.ApiException;
import app.utils.Utils;
import app.configs.SmtpConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

public class GmailSmtpMessageEmailService implements MessageEmailService {

    private final Supplier<SmtpConfig> configuration;
    private final SmtpDelivery delivery;

    //--------------------------------------------------------------

    public GmailSmtpMessageEmailService() {
        this(SmtpConfig::load, Transport::send);
    }

    //--------------------------------------------------------------

    public GmailSmtpMessageEmailService(Supplier<SmtpConfig> configuration, SmtpDelivery delivery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.delivery = Objects.requireNonNull(delivery);
    }

    //--------------------------------------------------------------

    @Override
    public void sendAdminMessageNotification(User recipient, Message message) {
        try {
            SmtpConfig config = configuration.get();

            MimeMessage email = new MimeMessage(config.session());
            email.setFrom(SmtpConfig.address(config.required("GMAIL_USERNAME")));
            email.setRecipients(jakarta.mail.Message.RecipientType.TO,
                    new jakarta.mail.Address[]{SmtpConfig.address(recipient.getEmail())});
            email.setSubject("Ny besked i StandFlow", StandardCharsets.UTF_8.name());
            email.setText(body(), StandardCharsets.UTF_8.name());

            delivery.send(email);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Beskednotifikationen kunne ikke sendes via e-mail.");
        }
    }

    //--------------------------------------------------------------

    private String body() {
        return "Du har modtaget en ny besked.\n\n"
                + "Log ind for at laese og svare paa beskeden:\n"
                + frontendUrl() + "/messages";
    }

    //--------------------------------------------------------------

    private String frontendUrl() {
        String value = Utils.getPropertyValue("FRONTEND_URL", "config.properties");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}
