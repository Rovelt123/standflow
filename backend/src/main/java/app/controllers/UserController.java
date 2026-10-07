package app.controllers;

import app.controllers.generic.BaseController;
import app.dtos.UserDTO;
import app.entities.User;
import app.enums.Notifications;
import app.enums.Role;
import app.mappers.UserMapper;
import app.security.SecurityService;
import app.services.UserService;
import app.utils.ErrorHandler;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class UserController extends BaseController<User, UserDTO> {

    private final UserMapper userMapper = new UserMapper();
    private final SecurityService securityService = new SecurityService();
    private final UserService userService = new UserService();

    //--------------------------------------------------------------

    public UserController() {
        super(User.class, new UserMapper());
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes() {
        UserController controller = new UserController();
        return () -> {
            post("users/auth/register", controller::registerUser, Role.ANYONE);
            post("users/auth/login", controller::login, Role.ANYONE);
            get("users/me", controller::currentUser, Role.USER, Role.ADMIN);
            put("users/me", controller::updateProfile, Role.USER, Role.ADMIN);
            put("users/me/password", controller::changePassword, Role.USER, Role.ADMIN);
            get("users/me/consent", controller::getMarketingConsent, Role.USER, Role.ADMIN);
            patch("users/me/consent", controller::updateMarketingConsent, Role.USER, Role.ADMIN);
            post("users/me/unsubscribe", controller::unsubscribeMarketing, Role.USER, Role.ADMIN);
            delete("users/me", controller::deleteAccount, Role.USER, Role.ADMIN);
        };
    }

    //--------------------------------------------------------------

    @Override
    protected List<User> getAllEntities() {
        return userService.getAll();
    }

    //--------------------------------------------------------------

    @Override
    protected User getEntityById(UUID id) {
        return userService.getById(id);
    }

    //--------------------------------------------------------------

    private void registerUser(Context ctx) {
        Map<String, ?> body = ErrorHandler.tryBody(ctx, Map.class, Notifications.BODY_EMPTY.getDisplayName());
        User user = userService.register(body);
        UserDTO dto = userMapper.toDTO(user);
        String token = securityService.createToken(dto);
        String message = messageService.buildMessage(Notifications.REGISTER_SUCCESS, user.getFirstname());
        respond(ctx, 201, message, Map.of("token", token, "data", dto));
    }

    //--------------------------------------------------------------

    private void login(Context ctx) {
        Map<String, ?> body = ErrorHandler.tryBody(ctx, Map.class, Notifications.BODY_EMPTY.getDisplayName());
        boolean rememberMe = userService.booleanField(body, "rememberMe");
        User user = userService.login(body);
        UserDTO dto = userMapper.toDTO(user);
        String token = securityService.createToken(dto, rememberMe);
        String message = messageService.buildMessage(Notifications.LOGGED_IN, user.getFirstname());
        respond(ctx, 200, message, Map.of("token", token, "data", dto));
    }

    //--------------------------------------------------------------

    private void currentUser(Context ctx) {
        UserDTO user = ctx.attribute("user");
        ctx.header("Cache-Control", "no-store");
        ctx.status(200).json(user);
    }

    //--------------------------------------------------------------

    private void updateProfile(Context ctx) {
        UserDTO current = ctx.attribute("user");
        User user = userService.updateProfile(current.getId(), ErrorHandler.tryBody(ctx, Map.class, "Ugyldige profiloplysninger."));
        profileResponse(ctx, user);
    }

    //--------------------------------------------------------------

    private void changePassword(Context ctx) {
        UserDTO current = ctx.attribute("user");
        User user = userService.changePassword(current.getId(), ErrorHandler.tryBody(ctx, Map.class, "Ugyldig adgangskode."));
        profileResponse(ctx, user);
    }

    //--------------------------------------------------------------

    private void getMarketingConsent(Context ctx) {
        UserDTO current = ctx.attribute("user");
        consentResponse(ctx, userService.getMarketingConsent(current.getId()));
    }

    //--------------------------------------------------------------

    private void updateMarketingConsent(Context ctx) {
        UserDTO current = ctx.attribute("user");
        User user = userService.updateMarketingConsent(current.getId(),
                ErrorHandler.tryBody(ctx, Map.class, "Ugyldigt samtykke."));
        consentResponse(ctx, user.isAcceptMarketing());
    }

    //--------------------------------------------------------------

    private void unsubscribeMarketing(Context ctx) {
        UserDTO current = ctx.attribute("user");
        User user = userService.unsubscribeMarketing(current.getId());
        consentResponse(ctx, user.isAcceptMarketing());
    }

    //--------------------------------------------------------------

    private void deleteAccount(Context ctx) {
        UserDTO current = ctx.attribute("user");
        userService.deleteAccount(current.getId(), ErrorHandler.tryBody(ctx, Map.class, "Ugyldig bekræftelse."));
        ctx.status(204);
    }

    //--------------------------------------------------------------

    private void profileResponse(Context ctx, User user) {
        UserDTO dto = userMapper.toDTO(user);
        ctx.header("Cache-Control", "no-store");
        ctx.json(Map.of("user", dto, "token", securityService.createToken(dto)));
    }

    //--------------------------------------------------------------

    private void consentResponse(Context ctx, boolean marketingConsent) {
        ctx.header("Cache-Control", "no-store");
        ctx.status(200).json(Map.of("marketingConsent", marketingConsent));
    }
}
