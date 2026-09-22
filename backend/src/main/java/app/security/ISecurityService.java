package app.security;

import app.dtos.UserDTO;
import app.entities.User;
import io.javalin.http.Handler;
import io.javalin.security.RouteRole;

import java.util.Set;

/**
 * Purpose: To handle security in the API
 * Author: Thomas Hartmann
 */
public interface ISecurityService {
    Handler authenticate(); // to verify roles inside token
    boolean authorize(User user, Set<RouteRole> allowedRoles); // to verify user roles
    String createToken(UserDTO user) throws Exception;
    UserDTO verifyToken(String token) throws Exception;
}
