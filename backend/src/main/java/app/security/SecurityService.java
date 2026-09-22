package app.security;

import app.dtos.UserDTO;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.exceptions.NotAuthorizedException;
import app.utils.Utils;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.security.RouteRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Purpose: To handle security in the API
 * Author: Thomas Hartmann
 */
public class SecurityService implements ISecurityService {
    private static SecurityService instance;
    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);

    // ________________________________________________________

    public SecurityService() { }

    // ________________________________________________________

    public static SecurityService getInstance() { // Singleton because we don't want multiple instances of the same class
        if (instance == null) {
            instance = new SecurityService();
        }
        return instance;
    }

    // ________________________________________________________

    @Override
    public Handler authenticate() throws UnauthorizedResponse {

        return (ctx) -> {
            // This is a preflight request => OK
            if (ctx.method().toString().equals("OPTIONS")) {
                ctx.status(200);
                return;
            }
            String header = ctx.header("Authorization");

            if (header == null) {
                throw new UnauthorizedResponse("Authorization header missing");
            }

            String[] headerParts = header.split(" ");
            if (headerParts.length != 2) {
                throw new UnauthorizedResponse("Authorization header malformed");
            }

            String token = headerParts[1];
            UserDTO verifiedTokenUser = verifyToken(token);

            if (verifiedTokenUser == null) {
                throw new UnauthorizedResponse("Invalid User or Token");
            }
            logger.info("User verified: {}", verifiedTokenUser);

            ctx.attribute("user", verifiedTokenUser);
        };
    }

    // ________________________________________________________

    @Override
    // Check if the user's roles contain any of the allowed roles
    public boolean authorize(User user, Set<RouteRole> allowedRoles) {
        if (user == null) {
            throw new UnauthorizedResponse("You need to log in, dude!");
        }

        Set<Role> roles = user.getRoles();

        if (roles.contains(Role.OWNER)) {
            return true;
        }

        return user.getRoles().stream().anyMatch(allowedRoles::contains);
    }

    // ________________________________________________________

    @Override
    public String createToken(UserDTO user) {
        try {
            
            String ISSUER = Utils.getPropertyValue("ISSUER", "config.properties");
            String TOKEN_EXPIRE_TIME = Utils.getPropertyValue("TOKEN_EXPIRE_TIME", "config.properties");
            String SECRET_KEY = Utils.getPropertyValue("SECRET_KEY", "config.properties");
            
            return JWTTokenGenerator.createToken(user, ISSUER, TOKEN_EXPIRE_TIME, SECRET_KEY);
        } catch (Exception e) {
            logger.error("Create token: {}", String.valueOf(e));

            throw new ApiException(500, "Could not create token");
        }
    }

    // ________________________________________________________

    @Override
    public UserDTO verifyToken(String token) {
        try {
            String SECRET_KEY = Utils.getPropertyValue("SECRET_KEY", "config.properties");
            if (!JWTTokenGenerator.tokenIsValid(token, SECRET_KEY) || !JWTTokenGenerator.tokenNotExpired(token)) {
                throw new NotAuthorizedException(403, "Token is invalid or expired");
            }
            return JWTTokenGenerator.getUserFromToken(token);
        } catch (Exception e) {
            logger.error("Verify token: {}", String.valueOf(e));
            throw new ApiException(HttpStatus.UNAUTHORIZED.getCode(), "Unauthorized. Could not verify token");
        }
    }
}
