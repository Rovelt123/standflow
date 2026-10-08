package app.configs;

import app.exceptions.ApiException;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class SmtpConfig {
    private final Map<String, String> values;

    //--------------------------------------------------------------

    private SmtpConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    //--------------------------------------------------------------

    public static SmtpConfig load() {
        return load(Path.of(".env"), System.getenv());
    }

    //--------------------------------------------------------------

    public static SmtpConfig load(Path file, Map<String, String> environment) {
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String entry = line.strip();
                if (entry.startsWith("\uFEFF")) entry = entry.substring(1).strip();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                if (entry.startsWith("export ")) entry = entry.substring(7).strip();
                int separator = entry.indexOf('=');
                if (separator < 1) throw new ApiException(500, "SMTP configuration: invalid .env syntax.");
                String name = entry.substring(0, separator).strip();
                String value = entry.substring(separator + 1).strip();
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new ApiException(500, "SMTP configuration: invalid .env syntax.");
                }
                if (value.startsWith("\"") || value.startsWith("'")) {
                    char quote = value.charAt(0);
                    int end = value.indexOf(quote, 1);
                    if (end < 0 || !(value.substring(end + 1).strip().isEmpty()
                            || value.substring(end + 1).strip().startsWith("#"))) {
                        throw new ApiException(500, "SMTP configuration: invalid .env quoting.");
                    }
                    value = value.substring(1, end);
                } else {
                    value = value.startsWith("#") ? "" : value.replaceFirst("\\s+#.*$", "").strip();
                }
                values.put(name, value);
            }
        } catch (NoSuchFileException ignored) {
            // Hosting environments may supply all settings without a local file.
        } catch (IOException | SecurityException exception) {
            throw new ApiException(500, "SMTP configuration: cannot read .env.");
        }
        values.putAll(environment);
        return new SmtpConfig(values);
    }

    //--------------------------------------------------------------

    public String required(String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(500, "SMTP configuration: missing or blank " + name + ".");
        }
        return value.strip();
    }

    //--------------------------------------------------------------

    public static InternetAddress address(String value) {
        try {
            if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) {
                throw new AddressException();
            }
            InternetAddress address = new InternetAddress(value.strip(), true);
            address.validate();
            if (address.isGroup() || address.getPersonal() != null || !address.getAddress().contains("@")) {
                throw new AddressException();
            }
            return address;
        } catch (AddressException exception) {
            throw new ApiException(500, "SMTP configuration: invalid email address.");
        }
    }

    //--------------------------------------------------------------

    public Session session() {
        String username = required("GMAIL_USERNAME");
        address(username);
        String password = required("GMAIL_APP_PASSWORD");
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", "smtp.gmail.com");
        properties.setProperty("mail.smtp.port", "587");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}