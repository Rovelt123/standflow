package app.controllers;

import app.controllers.generic.BaseController;
import app.dtos.TemplateDTO;
import app.entities.Template;
import app.enums.Notifications;
import app.enums.Role;
import app.exceptions.ApiException;
import app.mappers.TemplateMapper;
import app.services.TemplateService;
import app.utils.ErrorHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class TemplateController extends BaseController<Template, TemplateDTO> {

    private static final Set<String> TEXT_FIELDS = Set.of("name", "subject", "body");
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private final TemplateService templateService;

    //--------------------------------------------------------------

    public TemplateController(TemplateService templateService) {
        super(Template.class, new TemplateMapper());
        this.templateService = Objects.requireNonNull(templateService);
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes() {
        return registerRoutes(new TemplateService());
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(TemplateService templateService) {
        TemplateController controller = new TemplateController(templateService);
        return () -> {
            get("/template", controller::getAll, Role.USER);
            get("/template/{id}", controller::getByID, Role.USER);
            post("/template", controller::create, Role.USER);
            put("/template/{id}", controller::update, Role.USER);
        };
    }

    //--------------------------------------------------------------

    /** Returns 200 with an empty list instead of the generic 204 when no templates exist. */
    @Override
    public void getAll(Context ctx) {
        List<TemplateDTO> list = templateService.getAll().stream().map(mapper::toDTO).toList();
        String message = messageService.buildMessage(
                Notifications.GET_ALL,
                String.valueOf(list.size()),
                entityClass.getSimpleName().toLowerCase(Locale.ROOT)
        );
        respond(ctx, 200, message, Map.of("data", list));
    }

    //--------------------------------------------------------------

    public void create(Context ctx) {
        TemplateDTO request = parseRequest(ctx.body());
        ctx.status(201).json(templateService.create(request));
    }

    //--------------------------------------------------------------

    public void update(Context ctx) {
        UUID id = ErrorHandler.tryParseUUID(ctx.pathParam("id"), "Skabelonens id er ugyldigt.");
        TemplateDTO request = parseRequest(ctx.body());
        ctx.status(200).json(templateService.update(id, request));
    }

    //--------------------------------------------------------------

    @Override
    protected List<Template> getAllEntities() {
        return templateService.getAll();
    }

    //--------------------------------------------------------------

    @Override
    protected Template getEntityById(UUID id) {
        return templateService.getById(id);
    }

    //--------------------------------------------------------------

    /** Only name, subject and body are accepted; the id is controlled by the server and path. */
    private TemplateDTO parseRequest(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) {
                throw new ApiException(400, "Skabelonen skal være et JSON-objekt.");
            }
            var fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!TEXT_FIELDS.contains(field.getKey())) {
                    throw new ApiException(400, "Skabelonen indeholder ukendte felter.");
                }
                if (!field.getValue().isTextual() && !field.getValue().isNull()) {
                    throw new ApiException(400, "Skabelonen indeholder en forkert felttype.");
                }
            }
            return json.treeToValue(root, TemplateDTO.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(400, "Skabelonen indeholder ugyldig JSON.");
        }
    }
}
