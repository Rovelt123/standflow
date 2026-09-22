package app.controllers;

import app.controllers.generic.BaseController;
import app.daos.TemplateDAO;
import app.dtos.TemplateDTO;
import app.entities.Template;
import app.enums.Role;
import app.mappers.WeddingMapper;
import app.server.Setup;
import app.services.TemplateService;
import io.javalin.apibuilder.EndpointGroup;

import java.util.List;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class TemplateController extends BaseController<Template, TemplateDTO> {

    private final TemplateDAO templateDAO = new TemplateDAO(Setup.em);
    private final WeddingMapper templateMapper = new WeddingMapper();
    private final TemplateService templateService = new TemplateService();

    // ________________________________________________________

    public TemplateController() {
        super(Template.class, new WeddingMapper());
    }

    // ________________________________________________________

    public static EndpointGroup registerRoutes() {
        TemplateController controller = new TemplateController();
        return () -> {
            get("/template", controller::getAll, Role.USER);
            get("/template/{id}", controller::getByID, Role.USER);
        };
    }

    // ________________________________________________________

    @Override
    protected List<Template> getAllEntities() {
        return templateDAO.getAll();
    }

    // ________________________________________________________

    @Override
    protected Template getEntityById(UUID id) {
        return templateDAO.getById(id);
    }
}
