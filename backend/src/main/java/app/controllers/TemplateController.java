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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class TemplateController extends BaseController<Template, TemplateDTO> {

    private static final Set<String> TEXT_FIELDS = Set.of("name", "subject", "body");
    private static final Set<String> VALIDATE_FIELDS = Set.of("subject", "body");
    /** Request field -> template variable name. */
    private static final Map<String, String> RECIPIENT_FIELDS = Map.of(
            "firstName", "Firstname",
            "lastName", "Lastname",
            "company", "Company",
            "email", "Email"
    );

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
            delete("/template/{id}", controller::deleteTemplate, Role.USER);
            post("/template/{id}/render", controller::render, Role.USER);
            post("/template/validate", controller::validate, Role.USER);
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

    public void deleteTemplate(Context ctx) {
        UUID id = ErrorHandler.tryParseUUID(ctx.pathParam("id"), "Skabelonens id er ugyldigt.");
        templateService.delete(id);
        ctx.status(204);
    }

    //--------------------------------------------------------------

    /** Renders a saved template with a recipient's data. Does not change the stored template. */
    public void render(Context ctx) {
        UUID id = ErrorHandler.tryParseUUID(ctx.pathParam("id"), "Skabelonens id er ugyldigt.");
        Map<String, String> values = parseRecipient(ctx.body());
        ctx.status(200).json(templateService.render(id, values));
    }

    //--------------------------------------------------------------

    /** Checks a draft's variables without saving, so the frontend can warn before sending. */
    public void validate(Context ctx) {
        String text = parseDraft(ctx.body());
        List<String> unknown = templateService.unknownVariables(text);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", unknown.isEmpty());
        result.put("unknownVariables", unknown);
        ctx.status(200).json(result);
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

    //--------------------------------------------------------------

    /** Reads the recipient fields and maps them to template variable values. */
    private Map<String, String> parseRecipient(String body) {
        JsonNode root = readObject(body);
        Map<String, String> values = new HashMap<>();
        var fields = root.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String variable = RECIPIENT_FIELDS.get(field.getKey());
            if (variable == null) {
                throw new ApiException(400, "Modtageren indeholder ukendte felter.");
            }
            if (!field.getValue().isTextual() && !field.getValue().isNull()) {
                throw new ApiException(400, "Modtageren indeholder en forkert felttype.");
            }
            if (field.getValue().isTextual()) {
                values.put(variable, field.getValue().asText());
            }
        }
        return values;
    }

    //--------------------------------------------------------------

    /** Reads a draft (subject and/or body) into one text blob to validate its variables. */
    private String parseDraft(String body) {
        JsonNode root = readObject(body);
        StringBuilder text = new StringBuilder();
        var fields = root.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!VALIDATE_FIELDS.contains(field.getKey())) {
                throw new ApiException(400, "Udkastet indeholder ukendte felter.");
            }
            if (!field.getValue().isTextual() && !field.getValue().isNull()) {
                throw new ApiException(400, "Udkastet indeholder en forkert felttype.");
            }
            if (field.getValue().isTextual()) {
                text.append(field.getValue().asText()).append('\n');
            }
        }
        return text.toString();
    }

    //--------------------------------------------------------------

    private JsonNode readObject(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) {
                throw new ApiException(400, "Forventede et JSON-objekt.");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new ApiException(400, "Ugyldig JSON.");
        }
    }
}
