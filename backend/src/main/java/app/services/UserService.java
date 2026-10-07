package app.services;

import app.daos.UserDAO;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.server.Setup;
import app.utils.ErrorHandler;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserService {

    private final UserDAO userDAO;

    //--------------------------------------------------------------

    public UserService() {
        this(new UserDAO(Setup.em));
    }

    //--------------------------------------------------------------

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
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
                .company(text(body, "company", 200))
                .firstname(text(body, body.containsKey("firstName") ? "firstName" : "firstname", 75))
                .lastname(text(body, body.containsKey("lastName") ? "lastName" : "lastname", 74))
                .cvr(text(body, "cvr", 8))
                .email(email)
                .phone(text(body, "phone", 30))
                .address(text(body, "address", 255))
                .city(text(body, "city", 150))
                .acceptTerms(acceptTerms)
                .acceptPrivacy(acceptPrivacy)
                .acceptMarketing(booleanField(body, "acceptMarketing"))
                .roles(new HashSet<>(Set.of(Role.USER)))
                .build();
        if (!user.getCvr().matches("[0-9]{8}")) {
            throw new ApiException(400, "CVR skal bestå af præcis 8 cifre.");
        }
        if (userDAO.getByEmail(email) != null) {
            throw new ApiException(409, "E-mailadressen er allerede registreret.");
        }
        user.setPassword(PasswordService.hashHelper(password));
        return userDAO.create(user);
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
        if (!user.getEmail().equals(email)) user.setTokenVersion(user.getTokenVersion() + 1);
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

    private String requiredString(Map<String, ?> body, String field) {
        if (body == null || !(body.get(field) instanceof String value)) {
            throw new ApiException(400, field + " skal angives som tekst.");
        }
        return ErrorHandler.tryString(value, field + " skal udfyldes.");
    }
}
