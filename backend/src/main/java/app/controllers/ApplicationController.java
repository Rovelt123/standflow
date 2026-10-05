package app.controllers;

import app.dtos.ApplicationRequestDTO;
import app.enums.Role;
import app.exceptions.ApiException;
import app.services.ApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.Objects;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.post;

public class ApplicationController {

    private static final Set<String> TEXT_FIELDS = Set.of("company", "contact", "cvr", "email",
            "phone", "address", "city", "website", "products", "standType");
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    //--------------------------------------------------------------

    /** Integration supplies the live administration status provider through the service. */
    public static EndpointGroup registerRoutes(ApplicationService service) {
        ApplicationController controller = new ApplicationController(service);
        return () -> post("/applications", controller::create, Role.ANYONE);
    }

    //--------------------------------------------------------------

    public void create(Context ctx) {
        ApplicationRequestDTO request = parseRequest(ctx.body());
        ctx.status(201).json(service.create(request));
    }

    //--------------------------------------------------------------

    private ApplicationRequestDTO parseRequest(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) {
                throw new ApiException(400, "Ansøgningen skal være et JSON-objekt.");
            }
            var fields = root.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String name = field.getKey();
                JsonNode value = field.getValue();
                boolean valid;
                if (TEXT_FIELDS.contains(name)) {
                    valid = value.isTextual() || value.isNull();
                } else if ("previousExhibitor".equals(name)) {
                    valid = value.isBoolean() || value.isNull();
                } else if ("tables".equals(name) || "chairs".equals(name)) {
                    valid = value.isNull() || (value.isIntegralNumber() && value.canConvertToInt());
                } else {
                    throw new ApiException(400, "Ansøgningen indeholder ukendte felter.");
                }
                if (!valid) {
                    throw new ApiException(400, "Ansøgningen indeholder en forkert felttype.");
                }
            }
            return json.treeToValue(root, ApplicationRequestDTO.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(400, "Ansøgningen indeholder ugyldig JSON.");
        }
    }
}
