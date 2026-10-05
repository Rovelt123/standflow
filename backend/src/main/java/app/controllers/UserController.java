package app.controllers;

import app.controllers.generic.BaseController;
import app.daos.UserDAO;
import app.dtos.UserDTO;
import app.entities.User;
import app.enums.Notifications;
import app.enums.Role;
import app.mappers.UserMapper;
import app.security.SecurityService;
import app.server.Setup;
import app.services.PasswordService;
import app.services.UserService;
import app.utils.ErrorHandler;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class UserController extends BaseController<User, UserDTO> {

    private final UserDAO userDAO = new UserDAO(Setup.em);
    private final UserMapper userMapper = new UserMapper();
    private final SecurityService securityService = new SecurityService();
    private final UserService userService = new UserService();


    // ________________________________________________________

    public UserController() {
        super(User.class, new UserMapper());
    }

    // ________________________________________________________

    public static EndpointGroup registerRoutes() {
        UserController controller = new UserController();
        return ()->{

            post("users/auth/register", controller::registerUser, Role.ANYONE);
            post("users/auth/login", controller::login, Role.ANYONE);

        };
    }
    // ________________________________________________________
    @Override
    protected List<User> getAllEntities() {
        return userDAO.getAll();
    }

    // ________________________________________________________

    @Override
    protected User getEntityById(UUID id) {
        return userDAO.getById(id);
    }

    // ________________________________________________________

    private void registerUser(Context ctx) {
        Map<String, String> body = ErrorHandler.tryBodyMap(ctx, Notifications.BODY_EMPTY.getDisplayName());
        String company = ErrorHandler.tryString(body.get("company"), Notifications.REGISTER_NO_COMPANY.getDisplayName());
        String firstname = ErrorHandler.tryString(body.get("firstName"), Notifications.REGISTER_NO_FIRSTNAME.getDisplayName());
        String lastname = ErrorHandler.tryString(body.get("lastName"), Notifications.REGISTER_NO_LASTNAME.getDisplayName());
        String cvr = ErrorHandler.tryString(body.get("cvr"), Notifications.REGISTER_NO_CVR.getDisplayName());
        String email = ErrorHandler.tryString(body.get("email"), Notifications.REGISTER_NO_EMAIL.getDisplayName());
        String phone = ErrorHandler.tryString(body.get("phone"), Notifications.REGISTER_NO_PHONE.getDisplayName());
        String adress = ErrorHandler.tryString(body.get("address"), Notifications.REGISTER_NO_ADRESS.getDisplayName());
        String password = ErrorHandler.tryString(body.get("password"), Notifications.REGISTER_NO_PASSWORD.getDisplayName());
        String password_repeat = ErrorHandler.tryString(body.get("confirmPassword"), Notifications.REGISTER_NO_PASSWORD_REPEAT.getDisplayName());

        PasswordService.passwordValidation(password);

        if(!password.equals(password_repeat)){
            ctx.status(400).json(Notifications.REGISTER_PASSWORD_MISMATCH.getDisplayName());
            return;
        }

        Role role = Role.USER;

        if (userDAO.existByColumn(email, "email") || !email.contains("@")) {
            String message = messageService.buildMessage(Notifications.EMAIL_EXISTS, email);
            ctx.status(400).json(message);
            return;
        }


        User user = ErrorHandler.tryEntity(
                userDAO.create(User.builder()
                        .company(company)
                        .firstname(firstname)
                        .lastname(lastname)
                        .roles(Set.of(role))
                        .email(email)
                        .password(PasswordService.hashHelper(password))
                        .cvr(cvr)
                        .phone(phone)
                        .address(adress)
                        .build()),
                messageService.buildMessage(Notifications.EMAIL_EXISTS, email)
        );

        UserDTO dto = userMapper.toDTO(user);

        String jwtToken = securityService.createToken(dto);

        String message = messageService.buildMessage(Notifications.REGISTER_SUCCESS, user.getFirstname());

        respond(ctx, 201, message, Map.of(
                "token", jwtToken,
                "data", dto
        ));
    }

    // ________________________________________________________

    private void login(Context ctx) {
        Map<String, String> body = ErrorHandler.tryBodyMap(ctx, Notifications.BODY_EMPTY.getDisplayName());
        User user = ErrorHandler.tryEntity(
                userDAO.getByEmail(body.get("email")),
                Notifications.WRONG_CREDENTIALS.getDisplayName()
        );

        if (!PasswordService.passwordEquals(body.get("password"), user.getPassword())) {
            respond(ctx, 401, Notifications.WRONG_CREDENTIALS.getDisplayName(), null);
            return;
        }

        UserDTO dto = userMapper.toDTO(user);

        String token = securityService.createToken(dto);

        String message = messageService.buildMessage(
                Notifications.LOGGED_IN,
                user.getFirstname()
        );

        respond(ctx, 200, message, Map.of(
                "token", token,
                "data", dto
        ));
    }

}
