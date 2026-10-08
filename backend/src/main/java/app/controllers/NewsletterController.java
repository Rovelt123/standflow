package app.controllers;

import app.dtos.NewsletterRequest;
import app.enums.Role;
import app.exceptions.ApiException;
import app.services.NewsletterService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.post;

public class NewsletterController {

    private static final Set<String> NEWSLETTER_FIELDS = Set.of("audience", "templateId", "recipientIds", "category");
    private static final Set<String> UNSUBSCRIBE_FIELDS = Set.of("token");

    private final NewsletterService newsletterService;
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

    //--------------------------------------------------------------

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = Objects.requireNonNull(newsletterService);
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes() {
        return registerRoutes(new NewsletterService());
    }

    //--------------------------------------------------------------

    public static EndpointGroup registerRoutes(NewsletterService newsletterService) {
        NewsletterController controller = new NewsletterController(newsletterService);
        return () -> {
            post("/newsletter", controller::send, Role.ADMIN);
            post("/newsletter/unsubscribe", controller::unsubscribe, Role.ANYONE);
        };
    }

    //--------------------------------------------------------------

    private void send(Context ctx) {
        ctx.status(202).json(newsletterService.send(parseNewsletterRequest(ctx.body())));
    }

    //--------------------------------------------------------------

    private void unsubscribe(Context ctx) {
        newsletterService.unsubscribe(parseUnsubscribeRequest(ctx.body()));
        ctx.status(200).json(Map.of("marketingConsent", false));
    }

    //--------------------------------------------------------------

    private NewsletterRequest parseNewsletterRequest(String body) {
        try {
            JsonNode root = objectRoot(body, "Nyhedsbrevet skal vaere et JSON-objekt.");
            validateFields(root, NEWSLETTER_FIELDS, "Nyhedsbrevet indeholder ukendte felter.");
            return json.treeToValue(root, NewsletterRequest.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(400, "Nyhedsbrevet indeholder ugyldig JSON.");
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, "Nyhedsbrevet indeholder en forkert felttype.");
        }
    }

    //--------------------------------------------------------------

    private Map<String, ?> parseUnsubscribeRequest(String body) {
        try {
            JsonNode root = objectRoot(body, "Afmeldingen skal vaere et JSON-objekt.");
            validateFields(root, UNSUBSCRIBE_FIELDS, "Afmeldingen indeholder ukendte felter.");
            return json.convertValue(root, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new ApiException(400, "Afmeldingen indeholder ugyldig JSON.");
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, "Afmeldingen indeholder en forkert felttype.");
        }
    }

    //--------------------------------------------------------------

    private JsonNode objectRoot(String body, String message) throws JsonProcessingException {
        JsonNode root = json.readTree(body);
        if (root == null || !root.isObject()) {
            throw new ApiException(400, message);
        }
        return root;
    }

    //--------------------------------------------------------------

    private void validateFields(JsonNode root, Set<String> allowedFields, String message) {
        var fields = root.fieldNames();
        while (fields.hasNext()) {
            if (!allowedFields.contains(fields.next())) {
                throw new ApiException(400, message);
            }
        }
    }
}
