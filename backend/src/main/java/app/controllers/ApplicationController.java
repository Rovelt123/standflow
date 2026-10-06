package app.controllers;

import app.controllers.generic.BaseController;
import app.dtos.AdminApplicationDTO;
import app.dtos.ApplicationDTO;
import app.dtos.UserDTO;
import app.entities.Application;
import app.enums.Role;
import app.mappers.ApplicationMapper;
import app.services.ApplicationService;
import app.utils.ErrorHandler;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class ApplicationController extends BaseController<Application, ApplicationDTO> {

    private final ApplicationMapper applicationMapper = new ApplicationMapper();
    private final ApplicationService applicationService;

    //--------------------------------------------------------------

    public ApplicationController() {
        this(new ApplicationService());
    }

    //--------------------------------------------------------------

    public ApplicationController(ApplicationService applicationService) {
        super(Application.class, new ApplicationMapper());
        this.applicationService = applicationService;
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes() {
        return registerRoutes(new ApplicationService());
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(ApplicationService applicationService) {
        ApplicationController controller = new ApplicationController(applicationService);
        return () -> {
            post("/applications", controller::create, Role.USER);
            get("/applications/mine", controller::getOwn, Role.USER);
            get("/applications/mine/{id}", controller::getOwnById, Role.USER);
            put("/applications/mine/{id}", controller::updateOwn, Role.USER);
            delete("/applications/mine/{id}", controller::deleteOwn, Role.USER);
            get("/applications", controller::getAll, Role.ADMIN);
            get("/applications/{id}", controller::getByID, Role.ADMIN);
            patch("/applications/{id}/status", controller::updateStatus, Role.ADMIN);
            put("/applications/{id}/comment", controller::updateComment, Role.ADMIN);
        };
    }

    //--------------------------------------------------------------

    @Override
    protected List<Application> getAllEntities() {
        return applicationService.getAll();
    }

    //--------------------------------------------------------------

    @Override
    protected Application getEntityById(UUID id) {
        return applicationService.getById(id);
    }

    //--------------------------------------------------------------

    public void create(Context ctx) {
        applicationService.validateRequestFields(
                ErrorHandler.tryBodyMap(ctx, "Ansøgningen indeholder ugyldig JSON."));
        ApplicationDTO request = ErrorHandler.tryBody(ctx, ApplicationDTO.class,
                "Ansøgningen indeholder ugyldig JSON.");
        ApplicationDTO saved = applicationMapper.toDTO(applicationService.create(request, currentUserId(ctx)));
        ctx.status(201).json(Map.of(
                "id", saved.getId(),
                "status", saved.getStatus(),
                "createdAt", saved.getCreatedAt()));
    }

    //--------------------------------------------------------------

    @Override
    public void getAll(Context ctx) {
        ctx.status(200).json(applicationService.getAll().stream().map(this::adminDTO).toList());
    }

    //--------------------------------------------------------------

    @Override
    public void getByID(Context ctx) {
        ctx.status(200).json(adminDTO(applicationService.getById(applicationId(ctx))));
    }

    //--------------------------------------------------------------

    public void updateStatus(Context ctx) {
        Map<String, ?> body = ErrorHandler.tryBody(ctx, Map.class, "Status indeholder ugyldig JSON.");
        ctx.status(200).json(adminDTO(applicationService.updateStatus(applicationId(ctx), body)));
    }

    //--------------------------------------------------------------

    public void updateComment(Context ctx) {
        Map<String, ?> body = ErrorHandler.tryBody(ctx, Map.class, "Kommentaren indeholder ugyldig JSON.");
        ctx.status(200).json(adminDTO(applicationService.updateComment(applicationId(ctx), body)));
    }

    //--------------------------------------------------------------

    private UUID applicationId(Context ctx) {
        return ErrorHandler.tryParseUUID(ctx.pathParam("id"), "Ansøgningens id er ugyldigt.");
    }

    //--------------------------------------------------------------

    private AdminApplicationDTO adminDTO(Application application) {
        return new AdminApplicationDTO(applicationMapper.toDTO(application), application.getInternalComment());
    }

    //--------------------------------------------------------------

    private UUID currentUserId(Context ctx) {
        UserDTO user = ctx.attribute("user");
        return user.getId();
    }

    //--------------------------------------------------------------

    private void getOwn(Context ctx) {
        ctx.json(applicationService.getOwn(currentUserId(ctx)).stream().map(applicationMapper::toDTO).toList());
    }

    //--------------------------------------------------------------

    private void getOwnById(Context ctx) {
        ctx.json(applicationMapper.toDTO(applicationService.getOwnById(applicationId(ctx), currentUserId(ctx))));
    }

    //--------------------------------------------------------------

    private void updateOwn(Context ctx) {
        applicationService.validateRequestFields(ErrorHandler.tryBodyMap(ctx, "Ugyldig ansøgning."));
        ApplicationDTO request = ErrorHandler.tryBody(ctx, ApplicationDTO.class, "Ugyldig ansøgning.");
        ctx.json(applicationMapper.toDTO(applicationService.updateOwn(applicationId(ctx), currentUserId(ctx), request)));
    }

    //--------------------------------------------------------------

    private void deleteOwn(Context ctx) {
        applicationService.deleteOwn(applicationId(ctx), currentUserId(ctx));
        ctx.status(204);
    }
}
