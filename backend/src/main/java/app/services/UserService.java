package app.services;

import app.daos.UserDAO;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.server.Setup;
import app.utils.ErrorHandler;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        User user = User.builder()
                .company(text(body, "company", 200))
                .firstname(text(body, "firstname", 75))
                .lastname(text(body, "lastname", 74))
                .cvr(text(body, "cvr", 8))
                .email(email)
                .phone(text(body, "phone", 30))
                .address(text(body, "address", 255))
                .city(text(body, "city", 150))
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

    private String password(Map<String, ?> body) {
        String password = requiredString(body, "password");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Adgangskoden må højst fylde 72 UTF-8 bytes.");
        }
        return password;
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
