package app.services;

import app.daos.UnsubscribeTokenDAO;
import app.daos.UserDAO;
import app.entities.UnsubscribeToken;
import app.entities.User;
import app.exceptions.ApiException;
import app.server.Setup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public class UnsubscribeTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_DAYS = 30;

    private final UnsubscribeTokenDAO tokenDAO;
    private final UserDAO userDAO;
    private final SecureRandom secureRandom;
    private final Clock clock;

    //--------------------------------------------------------------

    public UnsubscribeTokenService() {
        this(new UnsubscribeTokenDAO(Setup.em), new UserDAO(Setup.em), SecureRandomHolder.INSTANCE,
                Clock.systemDefaultZone());
    }

    //--------------------------------------------------------------

    public UnsubscribeTokenService(UnsubscribeTokenDAO tokenDAO, UserDAO userDAO, SecureRandom secureRandom,
                                   Clock clock) {
        this.tokenDAO = Objects.requireNonNull(tokenDAO);
        this.userDAO = Objects.requireNonNull(userDAO);
        this.secureRandom = Objects.requireNonNull(secureRandom);
        this.clock = Objects.requireNonNull(clock);
    }

    //--------------------------------------------------------------

    public String issueToken(User user) {
        tokenDAO.revokeActiveForUser(user);
        return prepareToken(user);
    }

    //--------------------------------------------------------------

    public String prepareToken(User user) {
        String token = createRawToken();
        LocalDateTime now = tokenCreatedAt(user);
        tokenDAO.create(UnsubscribeToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plusDays(TOKEN_DAYS))
                .revoked(false)
                .build());
        return token;
    }

    //--------------------------------------------------------------

    public void deliverySucceeded(User user, String token) {
        tokenDAO.revokeOtherTokens(user, hash(token));
    }

    //--------------------------------------------------------------

    public void deliveryFailed(String token) {
        tokenDAO.revokeToken(hash(token));
    }

    //--------------------------------------------------------------

    private LocalDateTime tokenCreatedAt(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime latest = tokenDAO.latestCreatedAtForUser(user);
        if (latest != null && !latest.isBefore(now)) {
            return latest.plusNanos(1);
        }
        return now;
    }

    //--------------------------------------------------------------

    public User unsubscribe(Map<String, ?> body) {
        String token = requiredToken(body);
        UnsubscribeToken unsubscribeToken = tokenDAO.getUsableToken(hash(token), LocalDateTime.now(clock));
        if (unsubscribeToken == null) {
            throw new ApiException(400, "Afmeldingslinket er ugyldigt eller udloebet.");
        }
        User user = unsubscribeToken.getUser();
        user.setAcceptMarketing(false);
        unsubscribeToken.setRevoked(true);
        userDAO.update(user);
        tokenDAO.update(unsubscribeToken);
        return user;
    }

    //--------------------------------------------------------------

    private String createRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    //--------------------------------------------------------------

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(500, "Afmeldingstoken kunne ikke behandles.");
        }
    }

    //--------------------------------------------------------------

    private String requiredToken(Map<String, ?> body) {
        if (body == null || body.size() != 1 || !(body.get("token") instanceof String token)
                || token.isBlank()) {
            throw new ApiException(400, "Request skal indeholde praecis feltet token som tekst.");
        }
        return token;
    }

    //--------------------------------------------------------------

    private static final class SecureRandomHolder {
        private static final SecureRandom INSTANCE = new SecureRandom();
    }
}
