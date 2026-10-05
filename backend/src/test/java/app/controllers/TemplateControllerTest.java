package app.controllers;

import app.entities.Template;
import app.enums.Role;
import app.exceptions.ApiException;
import app.support.TemplateTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.junit.jupiter.api.Assertions.*;

class TemplateControllerTest extends TemplateTestSupport {

    private static final String[] INVALID_BODIES = {"", "null", "[]", "\"text\"", "{", "{} {}",
            "{\"name\":\"a\",\"name\":\"b\",\"subject\":\"s\",\"body\":\"b\"}"};

    private Javalin server;
    private HttpClient client;

    @BeforeEach
    void startServer() {
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.router.apiBuilder(() -> path("/api", () ->
                    TemplateController.registerRoutes(service).addEndpoints()));
        }).beforeMatched(ctx -> {
            // Verify the registered role, without relying on the separate security baseline.
            if (!ctx.routeRoles().contains(Role.USER)) {
                throw new ApiException(401, "Authentication required");
            }
        }).exception(ApiException.class, (error, ctx) ->
                ctx.status(error.getStatus()).result(error.getMessage())).start(0);
        client = HttpClient.newHttpClient();
    }

    //--------------------------------------------------------------

    @AfterEach
    void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    //--------------------------------------------------------------

    @Test
    void postReturnsSavedTemplateWithServerGeneratedId() throws Exception {
        var response = send("POST", "/api/template", validBody().toString());
        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
        JsonNode saved = json.readTree(response.body());
        assertEquals(4, saved.size());
        assertEquals("Kvittering", saved.get("name").asText());
        assertEquals("Tak for din ansøgning", saved.get("subject").asText());
        assertEquals("Kære <navn>,\n\nTak for din ansøgning.", saved.get("body").asText());
        UUID id = UUID.fromString(saved.get("id").asText());
        Template reloaded = reload(id);
        assertNotNull(reloaded);
        assertEquals("Kære <navn>,\n\nTak for din ansøgning.", reloaded.getBody());
    }

    //--------------------------------------------------------------

    @Test
    void getByIdReturnsSavedTemplateAndUnknownIdReturns404() throws Exception {
        UUID id = service.create(request(validBody())).getId();
        var response = send("GET", "/api/template/" + id, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(id.toString()));
        assertTrue(response.body().contains("Kære <navn>"));
        var unknown = send("GET", "/api/template/" + UUID.randomUUID(), null);
        assertEquals(404, unknown.statusCode());
        assertEquals("Skabelonen blev ikke fundet.", unknown.body());
    }

    //--------------------------------------------------------------

    @Test
    void getAllReturnsEmptyListWhenEmptyAndListOtherwise() throws Exception {
        var empty = send("GET", "/api/template", null);
        assertEquals(200, empty.statusCode());
        JsonNode emptyList = json.readTree(empty.body()).get("data").get("data");
        assertTrue(emptyList.isArray());
        assertEquals(0, emptyList.size());
        service.create(request(validBody()));
        service.create(request(validBody().put("name", "Nyhedsbrev")));
        var response = send("GET", "/api/template", null);
        assertEquals(200, response.statusCode());
        assertEquals(2, json.readTree(response.body()).get("data").get("data").size());
    }

    //--------------------------------------------------------------

    @Test
    void putUpdatesExistingTemplate() throws Exception {
        UUID id = service.create(request(validBody())).getId();
        ObjectNode body = validBody().put("name", "Ny navn").put("subject", "Nyt emne").put("body", "Ny tekst");
        var response = send("PUT", "/api/template/" + id, body.toString());
        assertEquals(200, response.statusCode());
        JsonNode updated = json.readTree(response.body());
        assertEquals(id.toString(), updated.get("id").asText());
        assertEquals("Ny navn", updated.get("name").asText());
        assertEquals("Ny navn", reload(id).getName());
        assertEquals("Nyt emne", reload(id).getSubject());
        assertEquals("Ny tekst", reload(id).getBody());
        assertEquals(1, countTemplates());
    }

    //--------------------------------------------------------------

    @Test
    void putRejectsInvalidInputUnknownIdAndMalformedIdWithoutChangingData() throws Exception {
        UUID id = service.create(request(validBody())).getId();
        String path = "/api/template/" + id;
        for (String body : INVALID_BODIES) {
            assertEquals(400, send("PUT", path, body).statusCode(), body);
        }
        assertEquals(400, send("PUT", path, validBody().put("id", UUID.randomUUID().toString()).toString()).statusCode());
        assertEquals(400, send("PUT", path, validBody().put("unknown", "x").toString()).statusCode());
        assertEquals(400, send("PUT", path, validBody().put("name", " ").toString()).statusCode());
        assertEquals(400, send("PUT", path, validBody().put("body", 42).toString()).statusCode());
        assertEquals(400, send("PUT", "/api/template/not-a-uuid", validBody().toString()).statusCode());
        assertEquals(404, send("PUT", "/api/template/" + UUID.randomUUID(), validBody().toString()).statusCode());
        Template saved = reload(id);
        assertEquals("Kvittering", saved.getName());
        assertEquals("Tak for din ansøgning", saved.getSubject());
        assertEquals("Kære <navn>,\n\nTak for din ansøgning.", saved.getBody());
        assertEquals(1, countTemplates());
    }

    //--------------------------------------------------------------

    @Test
    void postRejectsMalformedJsonUnknownFieldsWrongTypesAndInvalidValuesWithoutSaving() throws Exception {
        for (String body : INVALID_BODIES) {
            assertEquals(400, send("POST", "/api/template", body).statusCode(), body);
        }
        assertEquals(400, send("POST", "/api/template", validBody().put("id", UUID.randomUUID().toString()).toString()).statusCode());
        assertEquals(400, send("POST", "/api/template", validBody().put("unknown", "x").toString()).statusCode());
        for (String field : new String[]{"name", "subject", "body"}) {
            for (String value : new String[]{"1", "true", "[]", "{}"}) {
                ObjectNode body = validBody();
                body.set(field, json.readTree(value));
                assertEquals(400, send("POST", "/api/template", body.toString()).statusCode(), field + "=" + value);
            }
            ObjectNode missing = validBody();
            missing.remove(field);
            assertEquals(400, send("POST", "/api/template", missing.toString()).statusCode());
            assertEquals(400, send("POST", "/api/template", validBody().putNull(field).toString()).statusCode());
            assertEquals(400, send("POST", "/api/template", validBody().put(field, "  ").toString()).statusCode());
        }
        var tooLong = send("POST", "/api/template", validBody().put("name", "a".repeat(151)).toString());
        assertEquals(400, tooLong.statusCode());
        assertEquals("Navn må højst være 150 tegn.", tooLong.body());
        assertEquals(0, countTemplates());
    }

    //--------------------------------------------------------------

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                .header("Content-Type", "application/json")
                .method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
