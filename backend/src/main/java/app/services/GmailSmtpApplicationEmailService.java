package app.services;

import app.entities.Application;
import app.exceptions.ApiException;
import jakarta.activation.DataHandler;
import app.configs.SmtpConfig;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

public class GmailSmtpApplicationEmailService implements ApplicationEmailService {

    private final Supplier<SmtpConfig> configuration;
    private final SmtpDelivery delivery;

    //--------------------------------------------------------------

    public GmailSmtpApplicationEmailService() {
        this(SmtpConfig::load, Transport::send);
    }

    //--------------------------------------------------------------

    public GmailSmtpApplicationEmailService(Supplier<SmtpConfig> configuration, SmtpDelivery delivery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.delivery = Objects.requireNonNull(delivery);
    }

    //--------------------------------------------------------------

    @Override
    public void sendApplication(Application application, byte[] pdfAttachment, String attachmentFilename) {
        try {
            SmtpConfig config = configuration.get();
            String receiver = config.required("APPLICATION_RECEIVER_EMAIL");

            MimeMessage message = new MimeMessage(config.session());
            message.setFrom(SmtpConfig.address(config.required("GMAIL_USERNAME")));
            message.setRecipients(Message.RecipientType.TO, new jakarta.mail.Address[]{SmtpConfig.address(receiver)});
            message.setSubject("Ny standansoegning fra " + application.getCompany(), StandardCharsets.UTF_8.name());
            message.setContent(createContent(application, pdfAttachment, attachmentFilename));

            delivery.send(message);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Ansogningen kunne ikke sendes via e-mail.");
        }
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

    @Override
    public void sendStandTypeChange(Application application, String previousStandType) {
        try {
            SmtpConfig config = configuration.get();
            MimeMessage message = new MimeMessage(config.session());
            message.setFrom(SmtpConfig.address(config.required("GMAIL_USERNAME")));
            message.setRecipients(Message.RecipientType.TO,
                    new jakarta.mail.Address[]{SmtpConfig.address(application.getEmail())});
            message.setSubject("Standtypen i din ansøgning er ændret", StandardCharsets.UTF_8.name());
            message.setText("Hej " + application.getContact() + "\n\nStandtypen for " + application.getCompany()
                    + " (ansøgning " + application.getId() + ", markedsår "
                    + (application.getMarketYear() == null ? "ukendt" : application.getMarketYear())
                    + ") er ændret fra " + previousStandType + " til " + application.getStandType().name() + ".",
                    StandardCharsets.UTF_8.name());
            delivery.send(message);
        } catch (MessagingException exception) {
            throw new ApiException(500, "Ændringen er gemt, men mailen kunne ikke sendes.");
        }
    }

}
