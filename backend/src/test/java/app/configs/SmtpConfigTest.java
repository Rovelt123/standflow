package app.configs;

import app.exceptions.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmtpConfigTest {
    @TempDir
    Path directory;

    //--------------------------------------------------------------

    @Test
    void readsLocalFileWithQuotesCommentsAndEnvironmentPrecedence() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, """
                # Local test configuration only
                export GMAIL_USERNAME = 'local@example.com'
                GMAIL_APP_PASSWORD="dummy local password" # comment
                APPLICATION_RECEIVER_EMAIL=receiver@example.com # comment
                """);
        SmtpConfig config = SmtpConfig.load(file, Map.of("GMAIL_USERNAME", "process@example.com"));
        assertEquals("process@example.com", config.required("GMAIL_USERNAME"));
        assertEquals("dummy local password", config.required("GMAIL_APP_PASSWORD"));
        assertEquals("receiver@example.com", config.required("APPLICATION_RECEIVER_EMAIL"));
        assertNotNull(config.session());
    }

    //--------------------------------------------------------------

    @Test
    void missingFileIsAllowedWithProcessConfiguration() {
        SmtpConfig config = SmtpConfig.load(directory.resolve("absent"), Map.of(
                "GMAIL_USERNAME", "sender@example.com", "GMAIL_APP_PASSWORD", "dummy"));
        assertNotNull(config.session());
        // Only application mail needs the administrative receiver setting.
        assertThrows(ApiException.class, () -> config.required("APPLICATION_RECEIVER_EMAIL"));
    }

    //--------------------------------------------------------------

    @Test
    void missingAndBlankRequiredValuesFailWithoutExposingOtherValues() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "GMAIL_USERNAME=local@example.com\nGMAIL_APP_PASSWORD=dummy-sensitive");
        Path blankFile = directory.resolve("blank.env");
        Files.writeString(blankFile, "GMAIL_APP_PASSWORD= # local value omitted");
        assertThrows(ApiException.class, () -> SmtpConfig.load(blankFile, Map.of()).required("GMAIL_APP_PASSWORD"));
        for (String name : List.of("GMAIL_USERNAME", "GMAIL_APP_PASSWORD", "APPLICATION_RECEIVER_EMAIL")) {
            for (String blank : List.of("", "   ")) {
                SmtpConfig config = SmtpConfig.load(file, Map.of(name, blank));
                ApiException error = assertThrows(ApiException.class, () -> config.required(name));
                assertTrue(error.getMessage().contains(name));
                assertFalse(error.getMessage().contains("dummy-sensitive"));
                assertNull(error.getCause());
            }
            var values = new HashMap<>(Map.of("GMAIL_USERNAME", "sender@example.com",
                    "GMAIL_APP_PASSWORD", "dummy-sensitive", "APPLICATION_RECEIVER_EMAIL", "receiver@example.com"));
            values.remove(name);
            assertThrows(ApiException.class, () -> SmtpConfig.load(directory.resolve("absent"), values).required(name));
        }
    }

    //--------------------------------------------------------------

    @Test
    void malformedAndUnreadableFilesHaveSanitizedErrors() throws Exception {
        Path file = directory.resolve(".env");
        for (String text : List.of("dummy-sensitive", "GMAIL_APP_PASSWORD='dummy-sensitive", "bad key=dummy-sensitive")) {
            Files.writeString(file, text);
            ApiException error = assertThrows(ApiException.class, () -> SmtpConfig.load(file, Map.of()));
            assertFalse(error.getMessage().contains("dummy-sensitive"));
            assertNull(error.getCause());
        }
        ApiException error = assertThrows(ApiException.class, () -> SmtpConfig.load(directory, Map.of()));
        assertEquals("SMTP configuration: cannot read .env.", error.getMessage());
        assertNull(error.getCause());
    }

    //--------------------------------------------------------------

    @Test
    void rejectsInvalidAddressesWithoutEchoingThem() {
        for (String value : List.of("", "invalid", "a@example.com,b@example.com",
                "Person <a@example.com>", "a@example.com\r\nBcc:b@example.com")) {
            ApiException error = assertThrows(ApiException.class, () -> SmtpConfig.address(value));
            assertEquals("SMTP configuration: invalid email address.", error.getMessage());
            assertNull(error.getCause());
        }
    }

    //--------------------------------------------------------------

    @Test
    void sessionRequiresCredentialsAndEnforcesSecureGmailSettings() {
        assertThrows(ApiException.class, () -> SmtpConfig.load(directory.resolve("absent"), Map.of()).session());
        var session = SmtpConfig.load(directory.resolve("absent"), Map.of(
                "GMAIL_USERNAME", "sender@example.com", "GMAIL_APP_PASSWORD", "dummy")).session();
        assertEquals("true", session.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("10000", session.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("10000", session.getProperty("mail.smtp.timeout"));
        assertEquals("10000", session.getProperty("mail.smtp.writetimeout"));
    }
}