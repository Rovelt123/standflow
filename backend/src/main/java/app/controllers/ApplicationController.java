package app.controllers;

import app.controllers.generic.BaseController;
import app.daos.ApplicationDAO;
import app.dtos.ApplicationDTO;
import app.entities.Application;
import app.enums.Role;
import app.mappers.ApplicationMapper;
import app.server.Setup;
import app.services.ApplicationService;
import app.utils.ErrorHandler;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class ApplicationController extends BaseController<Application, ApplicationDTO> {

    private final ApplicationDAO applicationDAO = new ApplicationDAO(Setup.em);
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
        ApplicationController controller = new ApplicationController();
        return () -> {
            post("/applications", controller::create, Role.ANYONE);
        };
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(ApplicationService applicationService) {
        ApplicationController controller = new ApplicationController(applicationService);
        return () -> {
            post("/applications", controller::create, Role.ANYONE);
        };
    }

    //--------------------------------------------------------------

    @Override
    protected List<Application> getAllEntities() {
        return applicationDAO.getAll();
    }

    //--------------------------------------------------------------

    @Override
    protected Application getEntityById(UUID id) {
        return applicationDAO.getById(id);
    }

    //--------------------------------------------------------------

    public void create(Context ctx) {
        applicationService.validateRequestFields(
                ErrorHandler.tryBodyMap(ctx, "Ansøgningen indeholder ugyldig JSON."));
        ApplicationDTO request = ErrorHandler.tryBody(ctx, ApplicationDTO.class,
                "Ansøgningen indeholder ugyldig JSON.");
        ApplicationDTO saved = applicationMapper.toDTO(applicationService.create(request));
        ctx.status(201).json(Map.of(
                "id", saved.getId(),
                "status", saved.getStatus(),
                "createdAt", saved.getCreatedAt()));
    }
}
