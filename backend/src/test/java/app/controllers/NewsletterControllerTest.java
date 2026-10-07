package app.controllers;

import app.dtos.UserDTO;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.security.SecurityService;
import app.support.NewsletterTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.junit.jupiter.api.Assertions.*;

class NewsletterControllerTest extends NewsletterTestSupport {
    private Javalin server;
    private HttpClient client;

    //--------------------------------------------------------------

    @BeforeEach
    void startServer() {
        SecurityService security = new SecurityService();
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.router.apiBuilder(() -> path("/api", () ->
                    NewsletterController.registerRoutes(newsletterService).addEndpoints()));
        }).beforeMatched(ctx -> {
            if (ctx.routeRoles().isEmpty() || ctx.routeRoles().contains(Role.ANYONE)) {
                return;
            }
            String header = ctx.header("Test-User");
            if (header == null) throw new UnauthorizedResponse("Authentication required");
            User user = userDAO.getById(UUID.fromString(header));
            if (!security.authorize(user, ctx.routeRoles())) throw new UnauthorizedResponse("Role rejected");
            ctx.attribute("user", UserDTO.builder().id(user.getId()).build());
        }).exception(ApiException.class, (error, ctx) -> ctx.status(error.getStatus()).result(error.getMessage()))
                .start(0);
        client = HttpClient.newHttpClient();
    }

    //--------------------------------------------------------------

    @AfterEach
    void stopServer() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    //--------------------------------------------------------------

    @Test
    void adminCanSendNewsletterAndResponseContainsSafeCounts() throws Exception {
        HttpResponse<String> response = send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of(
                        "audience", "CATEGORY",
                        "templateId", template.getId().toString(),
                        "category", "A")),
                admin);

        assertEquals(202, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("sentTo", "skippedNoConsent", "failedToSend"), fields(body));
        assertEquals(1, body.get("sentTo").asInt());
        assertEquals(1, body.get("skippedNoConsent").asInt());
        assertEquals(0, body.get("failedToSend").asInt());
        assertFalse(response.body().contains(alice.getEmail()));
    }

    //--------------------------------------------------------------

    @Test
    void newsletterEndpointIsAdminOnly() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "audience", "ALL_APPLICANTS",
                "templateId", template.getId().toString()));

        assertEquals(401, send("POST", "/api/newsletter", body, null).statusCode());
        assertEquals(401, send("POST", "/api/newsletter", body, alice).statusCode());
        assertEquals(202, send("POST", "/api/newsletter", body, admin).statusCode());
    }

    //--------------------------------------------------------------

    @Test
    void publicUnsubscribeRequiresNoAuthenticationAndOnlyChangesMarketingConsent() throws Exception {
        String token = tokenService.issueToken(alice);

        HttpResponse<String> response = send("POST", "/api/newsletter/unsubscribe",
                json.writeValueAsString(Map.of("token", token)), null);

        assertEquals(200, response.statusCode(), response.body());
        assertFalse(json.readTree(response.body()).get("marketingConsent").asBoolean());
        assertFalse(reloadUser(alice.getId()).isAcceptMarketing());
        assertTrue(reloadUser(alice.getId()).isEmailNotifications());
        assertNotNull(reloadUser(alice.getId()));
    }

    //--------------------------------------------------------------

    @Test
    void invalidUnsubscribeTokenIsRejectedWithoutConsentChange() throws Exception {
        HttpResponse<String> response = send("POST", "/api/newsletter/unsubscribe",
                json.writeValueAsString(Map.of("token", "invalid")), null);

        assertEquals(400, response.statusCode());
        assertTrue(reloadUser(alice.getId()).isAcceptMarketing());
    }

    //--------------------------------------------------------------

    @Test
    void requestValidationReturns400() throws Exception {
        String allApplicants = json.writeValueAsString(Map.of(
                "audience", "ALL_APPLICANTS",
                "templateId", template.getId().toString()));
        assertEquals(400, send("POST", "/api/newsletter", "{}", admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "UNKNOWN", "templateId", template.getId())), admin)
                .statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "ALL_APPLICANTS")), admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                "{\"audience\":\"ALL_APPLICANTS\",\"templateId\":\"not-a-uuid\"}", admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "INDIVIDUAL", "templateId", template.getId())),
                admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "INDIVIDUAL", "templateId", template.getId(),
                        "recipientIds", java.util.List.of())), admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "CATEGORY", "templateId", template.getId())),
                admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "CATEGORY", "templateId", template.getId(),
                        "category", "Z")), admin).statusCode());
        assertEquals(400, send("POST", "/api/newsletter",
                json.writeValueAsString(Map.of("audience", "ALL_APPLICANTS", "templateId", template.getId(),
                        "category", "A")), admin).statusCode());
        assertEquals(202, send("POST", "/api/newsletter", allApplicants, admin).statusCode());
    }

    //--------------------------------------------------------------

    private Set<String> fields(JsonNode dto) {
        Set<String> names = new java.util.HashSet<>();
        dto.fieldNames().forEachRemaining(names::add);
        return names;
    }

    //--------------------------------------------------------------

    private HttpResponse<String> send(String method, String route, String body, User authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + route))
                .header("Content-Type", "application/json");
        if (authenticated != null) request.header("Test-User", authenticated.getId().toString());
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
