package app.services;

import app.daos.UserDAO;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.server.Setup;
import app.utils.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private final UserDAO userDAO;
    private final PasswordResetEmailService passwordResetEmailService;
    private final Clock clock;
    private final RegistrationEmailService registrationEmailService;

    //--------------------------------------------------------------

    public UserService() {
        this(new UserDAO(Setup.em));
    }

    //--------------------------------------------------------------

    public UserService(UserDAO userDAO) {
        this(userDAO, new GmailSmtpRegistrationEmailService());
    }

    //--------------------------------------------------------------

    public UserService(UserDAO userDAO, RegistrationEmailService registrationEmailService) {
        this(userDAO, registrationEmailService, new GmailSmtpPasswordResetEmailService(), Clock.systemUTC());
    }

    //--------------------------------------------------------------

    public UserService(UserDAO userDAO, RegistrationEmailService registrationEmailService,
                       PasswordResetEmailService passwordResetEmailService, Clock clock) {
        this.passwordResetEmailService = passwordResetEmailService;
        this.clock = clock;
        this.userDAO = userDAO;
        this.registrationEmailService = registrationEmailService;
    }

    //--------------------------------------------------------------

    public User register(Map<String, ?> body) {
        String email = email(body);
        String password = password(body);
        PasswordService.passwordValidation(password);
        if (!password.equals(requiredString(body, "confirmPassword"))) {
            throw new ApiException(400, "Adgangskoderne skal være ens.");
        }
        boolean acceptTerms = booleanField(body, "acceptTerms");
        boolean acceptPrivacy = booleanField(body, "acceptPrivacy");
        if (!acceptTerms || !acceptPrivacy) {
            throw new ApiException(400, "Betingelser og privatlivspolitik skal accepteres.");
        }
        User user = User.builder()
                .company(optionalText(body, "company", 200))
                .firstname(text(body, body.containsKey("firstName") ? "firstName" : "firstname", 75))
                .lastname(text(body, body.containsKey("lastName") ? "lastName" : "lastname", 74))
                .cvr(optionalText(body, "cvr", 8))
                .email(email)
                .phone(text(body, "phone", 30))
                .address(text(body, "address", 255))
                .city(text(body, "city", 150))
                .acceptTerms(acceptTerms)
                .acceptPrivacy(acceptPrivacy)
                .acceptMarketing(booleanField(body, "acceptMarketing"))
                .roles(new HashSet<>(Set.of(Role.USER)))
                .build();
        if (!user.getCvr().isEmpty() && !user.getCvr().matches("[0-9]{8}")) {
            throw new ApiException(400, "CVR skal bestå af præcis 8 cifre.");
        }
        if (userDAO.getByEmail(email) != null) {
            throw new ApiException(409, "E-mailadressen er allerede registreret.");
        }
        user.setPassword(PasswordService.hashHelper(password));
        User saved = userDAO.create(user);
        try {
            registrationEmailService.sendWelcome(saved);
        } catch (RuntimeException exception) {
            LOGGER.warn("Welcome email failed; registered account retained. Check SMTP configuration or delivery.");
        }
        return saved;
    }

    //--------------------------------------------------------------

    public User login(Map<String, ?> body) {
        String email = email(body);
        String password = password(body);
        User user = userDAO.getByEmail(email);
        if (user == null || !PasswordService.passwordEquals(password, user.getPassword())) {
            throw new ApiException(401, "Forkert e-mailadresse eller adgangskode.");
        }
        return user;
    }

    //--------------------------------------------------------------

    public void forgotPassword(Map<String, ?> body) {
        User user = userDAO.getByEmail(email(body));
        if (user == null) return;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (!userDAO.preparePasswordReset(user.getId(), resetTokenHash(token), clock.instant())) return;
        try {
            passwordResetEmailService.sendReset(user, token);
        } catch (RuntimeException exception) {
            LOGGER.warn("Password reset email failed. Check SMTP configuration or delivery.");
        }
    }

    //--------------------------------------------------------------

    public void resetPassword(Map<String, ?> body) {
        String token = requiredString(body, "token");
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApiException(400, "Nulstillingslinket er ugyldigt eller udløbet.");
        }
        String password = requiredString(body, "newPassword");
        PasswordService.passwordValidation(password);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Adgangskoden må højst fylde 72 UTF-8 bytes.");
        }
        if (!password.equals(requiredString(body, "confirmPassword"))) {
            throw new ApiException(400, "Adgangskoderne skal være ens.");
        }
        if (!userDAO.resetPassword(resetTokenHash(token), PasswordService.hashHelper(password), clock.instant())) {
            throw new ApiException(400, "Nulstillingslinket er ugyldigt eller udløbet.");
        }
    }

    //--------------------------------------------------------------

    private String resetTokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    //--------------------------------------------------------------

    private String email(Map<String, ?> body) {
        String email = text(body, "email", 254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) {
            throw new ApiException(400, "E-mailadressen er ugyldig.");
        }
        return email;
    }

    //--------------------------------------------------------------

    public boolean booleanField(Map<String, ?> body, String field) {
        if (body == null) {
            throw new ApiException(400, "Request skal være et JSON-objekt.");
        }
        if (!body.containsKey(field)) {
            return false;
        }
        if (!(body.get(field) instanceof Boolean value)) {
            throw new ApiException(400, field + " skal være true eller false.");
        }
        return value;
    }

    //--------------------------------------------------------------

    public List<User> getAll() {
        return userDAO.getAll();
    }

    //--------------------------------------------------------------

    public User getById(UUID id) {
        return userDAO.getById(id);
    }

    //--------------------------------------------------------------

    public boolean getMarketingConsent(UUID id) {
        return ErrorHandler.tryEntity(userDAO.getById(id), "Brugeren findes ikke.").isAcceptMarketing();
    }

    //--------------------------------------------------------------

    public User updateMarketingConsent(UUID id, Map<String, ?> body) {
        User user = ErrorHandler.tryEntity(userDAO.getById(id), "Brugeren findes ikke.");
        user.setAcceptMarketing(requiredBoolean(body, "marketingConsent"));
        return userDAO.update(user);
    }

    //--------------------------------------------------------------

    public User unsubscribeMarketing(UUID id) {
        User user = ErrorHandler.tryEntity(userDAO.getById(id), "Brugeren findes ikke.");
        user.setAcceptMarketing(false);
        return userDAO.update(user);
    }

    //--------------------------------------------------------------

    public User updateProfile(UUID id, Map<String, ?> body) {
        User user = confirmPassword(id, body);
        String email = email(body);
        String company = text(body, "company", 200);
        String firstname = text(body, "firstname", 75);
        String lastname = text(body, "lastname", 74);
        String cvr = text(body, "cvr", 8);
        String phone = text(body, "phone", 30);
        String address = text(body, "address", 255);
        String city = text(body, "city", 150);
        if (!cvr.matches("[0-9]{8}")) throw new ApiException(400, "CVR skal bestå af præcis 8 cifre.");
        if (!body.containsKey("emailNotifications")) throw new ApiException(400, "Vælg mailnotifikationer til eller fra.");
        boolean notifications = booleanField(body, "emailNotifications");
        User duplicate = userDAO.getByEmail(email);
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new ApiException(409, "E-mailadressen er allerede registreret.");
        }
        if (!user.getEmail().equals(email)) {
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setPasswordResetHash(null);
            user.setPasswordResetExpiresAt(null);
        }
        user.setEmail(email);
        user.setCompany(company);
        user.setFirstname(firstname);
        user.setLastname(lastname);
        user.setCvr(cvr);
        user.setPhone(phone);
        user.setAddress(address);
        user.setCity(city);
        user.setEmailNotifications(notifications);
        return userDAO.update(user);
    }

    //--------------------------------------------------------------

    public User changePassword(UUID id, Map<String, ?> body) {
        User user = confirmPassword(id, body);
        String password = requiredString(body, "newPassword");
        PasswordService.passwordValidation(password);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Adgangskoden må højst fylde 72 UTF-8 bytes.");
        }
        if (!password.equals(requiredString(body, "confirmPassword"))) {
            throw new ApiException(400, "De nye adgangskoder skal være ens.");
        }
        user.setPasswordResetHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setPassword(PasswordService.hashHelper(password));
        user.setTokenVersion(user.getTokenVersion() + 1);
        return userDAO.update(user);
    }

    //--------------------------------------------------------------

    public void deleteAccount(UUID id, Map<String, ?> body) {
        User user = confirmPassword(id, body);
        if (!booleanField(body, "confirmDelete")) {
            throw new ApiException(400, "Du skal bekræfte, at din bruger og dine data slettes.");
        }
        userDAO.deleteAccount(user);
    }

    //--------------------------------------------------------------

    private User confirmPassword(UUID id, Map<String, ?> body) {
        User user = ErrorHandler.tryEntity(userDAO.getById(id), "Brugeren findes ikke.");
        String password = requiredString(body, "currentPassword");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || !PasswordService.passwordEquals(password, user.getPassword())) {
            throw new ApiException(400, "Din nuværende adgangskode er forkert.");
        }
        return user;
    }

    //--------------------------------------------------------------

    private String password(Map<String, ?> body) {
        String password = requiredString(body, "password");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Adgangskoden må højst fylde 72 UTF-8 bytes.");
        }
        return password;
    }

    //--------------------------------------------------------------

    private boolean requiredBoolean(Map<String, ?> body, String field) {
        if (body == null || !body.containsKey(field)) {
            throw new ApiException(400, field + " skal angives.");
        }
        if (!(body.get(field) instanceof Boolean value)) {
            throw new ApiException(400, field + " skal være true eller false.");
        }
        return value;
    }

    //--------------------------------------------------------------

    private String text(Map<String, ?> body, String field, int maximumLength) {
        String value = requiredString(body, field).strip();
        if (value.length() > maximumLength) {
            throw new ApiException(400, field + " må højst være " + maximumLength + " tegn.");
        }
        return value;
    }

    //--------------------------------------------------------------

    private String optionalText(Map<String, ?> body, String field, int maximumLength) {
        if (body == null || !body.containsKey(field) || body.get(field) == null) {
            return "";
        }
        if (!(body.get(field) instanceof String value)) {
            throw new ApiException(400, field + " skal angives som tekst.");
        }
        value = value.strip();
        if (value.length() > maximumLength) {
            throw new ApiException(400, field + " må højst være " + maximumLength + " tegn.");
        }
        return value;
    }

    //--------------------------------------------------------------

    private String requiredString(Map<String, ?> body, String field) {
        if (body == null || !(body.get(field) instanceof String value)) {
            throw new ApiException(400, field + " skal angives som tekst.");
        }
        return ErrorHandler.tryString(value, field + " skal udfyldes.");
    }
}
