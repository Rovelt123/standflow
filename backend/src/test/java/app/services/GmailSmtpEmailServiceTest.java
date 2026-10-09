package app.services;

import app.configs.SmtpConfig;
import app.entities.Application;
import app.entities.User;
import app.exceptions.ApiException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GmailSmtpEmailServiceTest {
    @TempDir
    Path directory;
    private final List<MimeMessage> sent = new ArrayList<>();

    //--------------------------------------------------------------

    private SmtpConfig config() {
        return SmtpConfig.load(directory.resolve("absent.env"), Map.of(
                "GMAIL_USERNAME", "sender@example.com",
                "GMAIL_APP_PASSWORD", "dummy-test-password",
                "APPLICATION_RECEIVER_EMAIL", "applications@example.com"));
    }

    //--------------------------------------------------------------

    private void capture(MimeMessage message) throws MessagingException {
        message.saveChanges();
        sent.add(message);
    }

    //--------------------------------------------------------------

    @Test
    void applicationDeliversGeneratedPdfWithConfiguredRecipientAndFilename() throws Exception {
        Application application = Application.builder().id(UUID.randomUUID()).company("Blå håndværk")
                .email("applicant@example.com").build();
        ApplicationPdfGenerator generator = new SimpleApplicationPdfGenerator();
        byte[] pdf = generator.generate(application);
        new GmailSmtpApplicationEmailService(this::config, this::capture)
                .sendApplication(application, pdf, generator.filename(application));

        assertEquals(1, sent.size());
        MimeMessage email = sent.getFirst();
        assertEnvelope(email, "applications@example.com");
        assertTrue(email.getSubject().contains(application.getCompany()));
        MimeMultipart content = (MimeMultipart) email.getContent();
        assertEquals(2, content.getCount());
        assertTrue(content.getBodyPart(0).getContent().toString().contains(application.getCompany()));
        assertEquals("UTF-8", new ContentType(content.getBodyPart(0).getContentType()).getParameter("charset"));
        var attachment = content.getBodyPart(1);
        assertEquals("application-" + application.getId() + ".pdf", attachment.getFileName());
        assertTrue(attachment.isMimeType("application/pdf"));
        assertArrayEquals(pdf, attachment.getInputStream().readAllBytes());
    }

    //--------------------------------------------------------------

    @Test
    void newsletterDeliversUtf8SubjectAndBodyToSelectedRecipient() throws Exception {
        User recipient = User.builder().email("newsletter@example.com").build();
        new GmailSmtpNewsletterEmailService(this::config, this::capture)
                .sendNewsletter(recipient, "Nyheder fra blå ø", "Hej Åse\nAfmeld: https://example.com/unsubscribe?token=dummy");
        assertEquals(1, sent.size());
        MimeMessage email = sent.getFirst();
        assertEnvelope(email, recipient.getEmail());
        assertEquals("Nyheder fra blå ø", email.getSubject());
        assertEquals("Hej Åse\nAfmeld: https://example.com/unsubscribe?token=dummy", email.getContent());
        assertEquals("UTF-8", new ContentType(email.getContentType()).getParameter("charset"));
    }

    //--------------------------------------------------------------

    @Test
    void bothChatDirectionsUseGenericContentAndPassedRecipientWithoutConsent() throws Exception {
        var provider = new GmailSmtpMessageEmailService(this::config, this::capture);
        var message = app.entities.Message.builder().subject("Private subject").body("Private message").build();
        provider.sendAdminMessageNotification(User.builder().email("customer@example.com").acceptMarketing(false).build(), message);
        provider.sendCustomerMessageNotification(User.builder().email("admin@example.com").acceptMarketing(false).build(), message);
        assertEquals(2, sent.size());
        assertEnvelope(sent.get(0), "customer@example.com");
        assertEnvelope(sent.get(1), "admin@example.com");
        for (MimeMessage email : sent) {
            assertEquals("Ny besked i StandFlow", email.getSubject());
            assertTrue(email.getContent().toString().contains("/messages"));
            assertFalse(email.getContent().toString().contains(message.getBody()));
            assertFalse(email.getContent().toString().contains(message.getSubject()));
            assertEquals("UTF-8", new ContentType(email.getContentType()).getParameter("charset"));
        }
    }

    //--------------------------------------------------------------

    @Test
    void welcomeUsesRegisteredRecipientAndUtf8WithoutPasswordOrMarketingConsent() throws Exception {
        User recipient = User.builder().email("new@example.com").firstname("Åse")
                .password("private-password-hash").acceptMarketing(false).build();
        new GmailSmtpRegistrationEmailService(this::config, this::capture).sendWelcome(recipient);
        assertEquals(1, sent.size());
        MimeMessage email = sent.getFirst();
        assertEnvelope(email, recipient.getEmail());
        assertEquals("Velkommen til StandFlow", email.getSubject());
        String body = email.getContent().toString();
        assertTrue(body.contains("Hej Åse"));
        assertTrue(body.contains("Din konto hos StandFlow er nu oprettet."));
        assertTrue(body.contains(app.utils.Utils.getPropertyValue("FRONTEND_URL", "config.properties")));
        assertFalse(body.contains(recipient.getPassword()));
        assertEquals("UTF-8", new ContentType(email.getContentType()).getParameter("charset"));
    }

    //--------------------------------------------------------------

    @Test
    void allProvidersSanitizeTransportErrors() {
        SmtpDelivery failure = message -> { throw new MessagingException("dummy-sensitive-transport-detail"); };
        var application = new GmailSmtpApplicationEmailService(this::config, failure);
        var newsletter = new GmailSmtpNewsletterEmailService(this::config, failure);
        var chat = new GmailSmtpMessageEmailService(this::config, failure);
        User recipient = User.builder().email("recipient@example.com").build();
        List<Runnable> operations = List.of(
                () -> new GmailSmtpRegistrationEmailService(this::config, failure).sendWelcome(recipient),
                () -> application.sendApplication(Application.builder().company("Test").build(), new byte[]{1}, "application.pdf"),
                () -> newsletter.sendNewsletter(recipient, "Subject", "Body"),
                () -> chat.sendAdminMessageNotification(recipient, new app.entities.Message()),
                () -> chat.sendCustomerMessageNotification(recipient, new app.entities.Message()));
        for (Runnable operation : operations) {
            ApiException error = assertThrows(ApiException.class, operation::run);
            assertEquals(500, error.getStatus());
            assertFalse(error.getMessage().contains("dummy-sensitive"));
            assertNull(error.getCause());
        }
    }

    //--------------------------------------------------------------

    @Test
    void invalidRecipientsAndMissingApplicationReceiverNeverInvokeDelivery() {
        var newsletter = new GmailSmtpNewsletterEmailService(this::config, this::capture);
        var chat = new GmailSmtpMessageEmailService(this::config, this::capture);
        User invalid = User.builder().email("one@example.com,two@example.com").build();
        assertThrows(ApiException.class, () -> new GmailSmtpRegistrationEmailService(this::config, this::capture).sendWelcome(invalid));
        assertThrows(ApiException.class, () -> newsletter.sendNewsletter(invalid, "s", "b"));
        assertThrows(ApiException.class, () -> chat.sendCustomerMessageNotification(invalid, new app.entities.Message()));
        SmtpConfig missingReceiver = SmtpConfig.load(directory.resolve("missing"), Map.of(
                "GMAIL_USERNAME", "sender@example.com", "GMAIL_APP_PASSWORD", "dummy"));
        var application = new GmailSmtpApplicationEmailService(() -> missingReceiver, this::capture);
        assertThrows(ApiException.class, () -> application.sendApplication(new Application(), new byte[]{1}, "application.pdf"));
        assertTrue(sent.isEmpty());
    }

    //--------------------------------------------------------------

    private void assertEnvelope(MimeMessage email, String recipient) throws Exception {
        assertEquals("sender@example.com", email.getFrom()[0].toString());
        assertEquals(1, email.getAllRecipients().length);
        assertEquals(recipient, email.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("smtp.gmail.com", email.getSession().getProperty("mail.smtp.host"));
        assertEquals("587", email.getSession().getProperty("mail.smtp.port"));
        assertEquals("true", email.getSession().getProperty("mail.smtp.auth"));
        assertEquals("true", email.getSession().getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", email.getSession().getProperty("mail.smtp.starttls.required"));
        assertFalse(email.getSession().getDebug());
    }
}
