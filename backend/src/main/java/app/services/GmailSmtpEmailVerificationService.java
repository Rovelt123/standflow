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

public class GmailSmtpEmailVerificationService implements EmailVerificationService {

    private final Supplier<SmtpConfig> configuration;
    private final SmtpDelivery delivery;

    //--------------------------------------------------------------

    public GmailSmtpEmailVerificationService() {
        this(SmtpConfig::load, Transport::send);
    }

    //--------------------------------------------------------------

    public GmailSmtpEmailVerificationService(Supplier<SmtpConfig> configuration, SmtpDelivery delivery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.delivery = Objects.requireNonNull(delivery);
    }

    //--------------------------------------------------------------

    @Override
    public void sendVerification(User recipient, String token) {
        try {
            SmtpConfig config = configuration.get();
            MimeMessage email = new MimeMessage(config.session());
            email.setFrom(SmtpConfig.address(config.required("GMAIL_USERNAME")));
            email.setRecipients(Message.RecipientType.TO,
                    new jakarta.mail.Address[]{SmtpConfig.address(recipient.getEmail())});
            email.setSubject("Bekræft din e-mailadresse", StandardCharsets.UTF_8.name());
            String baseUrl = Utils.getPropertyValue("FRONTEND_URL", "config.properties").replaceAll("/+$", "");
            email.setText("Tak for din registrering hos StandFlow.\n\n"
                    + "Bekræft din e-mailadresse her:\n" + baseUrl + "/bekraeft-email#token=" + token
                    + "\n\nLinket kan bruges én gang og udløber om 24 timer."
                    + "\nHvis du ikke har bedt om dette, kan du ignorere mailen.\n\nStandFlow",
                    StandardCharsets.UTF_8.name());
            delivery.send(email);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Bekræftelsesmailen kunne ikke sendes.");
        }
    }
}
