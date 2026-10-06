package app.controllers;

import app.dtos.UserDTO;
import app.enums.Role;
import app.exceptions.ApiException;
import app.services.ConversationService;
import app.utils.ErrorHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class MessageController {
    private final ConversationService service;
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

    //--------------------------------------------------------------

    public MessageController(ConversationService service) {
        this.service = Objects.requireNonNull(service);
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes() {
        return registerRoutes(new ConversationService());
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(ConversationService service) {
        MessageController controller = new MessageController(service);
        return () -> {
            post("/messages", controller::sendCustomer, Role.USER);
            get("/messages/mine", controller::getMine, Role.USER);
            get("/messages/threads", controller::getThreads, Role.ADMIN);
            get("/messages/threads/{customerId}", controller::getThread, Role.ADMIN);
            post("/messages/threads/{customerId}", controller::sendAdmin, Role.ADMIN);
            patch("/messages/threads/{customerId}/read", controller::markRead, Role.ADMIN);
        };
    }

    //--------------------------------------------------------------

    private void sendCustomer(Context ctx) {
        ctx.status(201).json(service.sendCustomer(currentUserId(ctx), parseRequest(ctx.body())));
    }

    //--------------------------------------------------------------

    private void getMine(Context ctx) {
        ctx.status(200).json(service.getMine(currentUserId(ctx)));
    }

    //--------------------------------------------------------------

    private void getThreads(Context ctx) {
        ctx.status(200).json(service.getThreads(currentUserId(ctx), ctx.queryParam("sort")));
    }

    //--------------------------------------------------------------

    private void getThread(Context ctx) {
        ctx.status(200).json(service.getThread(currentUserId(ctx), customerId(ctx)));
    }

    //--------------------------------------------------------------

    private void sendAdmin(Context ctx) {
        ctx.status(201).json(service.sendAdmin(currentUserId(ctx), customerId(ctx), parseRequest(ctx.body())));
    }

    //--------------------------------------------------------------

    private void markRead(Context ctx) {
        if (!ctx.body().isBlank()) {
            Map<String, ?> request = parseRequest(ctx.body());
            if (!request.isEmpty()) throw new ApiException(400, "Endpointet accepterer ingen felter.");
        }
        service.markRead(currentUserId(ctx), customerId(ctx));
        ctx.status(204);
    }

    //--------------------------------------------------------------

    private UUID currentUserId(Context ctx) {
        UserDTO user = ctx.attribute("user");
        if (user == null || user.getId() == null) throw new ApiException(401, "Login er påkrævet.");
        return user.getId();
    }

    //--------------------------------------------------------------

    private UUID customerId(Context ctx) {
        return ErrorHandler.tryParseUUID(ctx.pathParam("customerId"), "Kundens id er ugyldigt.");
    }

    //--------------------------------------------------------------

    private Map<String, ?> parseRequest(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) throw new ApiException(400, "Beskeden skal være et JSON-objekt.");
            return json.convertValue(root, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new ApiException(400, "Beskeden indeholder ugyldig JSON.");
        }
    }
}
