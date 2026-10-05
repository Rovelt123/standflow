package app.controllers;

import app.daos.ApplicationDAO;
import app.entities.Application;
import app.enums.Role;
import app.exceptions.ApiException;
import app.services.ApplicationService;
import app.support.ApplicationTestSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationControllerTest extends ApplicationTestSupport {

    private Javalin server;
    private HttpClient client;

    @BeforeEach
    void startServer() {
        start(service);
        client = HttpClient.newHttpClient();
    }

    //--------------------------------------------------------------

    private void start(ApplicationService applicationService) {
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.router.apiBuilder(() -> path("/api", () ->
                    ApplicationController.registerRoutes(applicationService).addEndpoints()));
        }).beforeMatched(ctx -> {
            // Verify the actual registered role, without relying on the broken security baseline.
            if (!ctx.routeRoles().contains(Role.ANYONE)) {
                throw new ApiException(401, "Authentication required");
            }
        }).exception(ApiException.class, (error, ctx) ->
                ctx.status(error.getStatus()).result(error.getMessage())).start(0);
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
    void publicPostReturnsOnlyReceiptAfterCommit() throws Exception {
        var response = post(validBody().toString());
        assertEquals(201, response.statusCode());
        var receipt = json.readTree(response.body());
        assertEquals(3, receipt.size());
        assertEquals("PENDING", receipt.get("status").asText());
        assertEquals("2026-10-05", receipt.get("createdAt").asText());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertTrue(response.headers().firstValue("location").isEmpty());
        UUID id = UUID.fromString(receipt.get("id").asText());
        try (var reader = emf.createEntityManager()) {
            assertNotNull(reader.find(Application.class, id));
        }
    }

    //--------------------------------------------------------------

    @Test
    void rejectsMalformedJsonUnknownFieldsAndCoercionWithoutSaving() throws Exception {
        for (String body : new String[]{"", "null", "[]", "{", "{} {}", "{\"company\":1,\"company\":2}"}) {
            assertEquals(400, post(body).statusCode(), body);
        }
        for (String field : new String[]{"id", "status", "createdAt", "price", "open"}) {
            assertEquals(400, post(validBody().put(field, "untrusted").toString()).statusCode());
        }
        for (String field : new String[]{"tables", "chairs"}) {
            for (String value : new String[]{"\"2\"", "1.5", "2147483648", "true", "[]", "{}"}) {
                ObjectNode body = validBody();
                body.set(field, json.readTree(value));
                assertEquals(400, post(body.toString()).statusCode());
            }
        }
        assertEquals(400, post(validBody().put("previousExhibitor", "true").toString()).statusCode());
        assertEquals(400, post(validBody().put("company", 42).toString()).statusCode());
        ObjectNode body = validBody();
        body.putArray("standType").add("A");
        assertEquals(400, post(body.toString()).statusCode());
        assertEquals(0, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void returnsValidationAndIntakeErrorsAsTextWithoutSaving() throws Exception {
        assertEquals(400, post(validBody().put("cvr", "abcdefgh").toString()).statusCode());
        intake.set(new ApplicationService.IntakeStatus(false, LocalDate.of(2026, 11, 1)));
        var closed = post(validBody().toString());
        assertEquals(409, closed.statusCode());
        assertEquals("Ansøgninger er lukket. Der åbnes for ansøgninger igen 2026-11-01.", closed.body());
        intake.set(null);
        assertEquals(503, post(validBody().toString()).statusCode());
        assertEquals(0, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void databaseFailureReturnsGeneric500() throws Exception {
        server.stop();
        ApplicationDAO failingDAO = new ApplicationDAO(em) {
            @Override
            public Application create(Application application) {
                throw new IllegalStateException("SQL credentials and private request data");
            }
        };
        start(new ApplicationService(failingDAO, intake::get, clock));
        var response = post(validBody().toString());
        assertEquals(500, response.statusCode());
        assertEquals("Ansøgningen kunne ikke gemmes.", response.body());
        assertEquals(0, countApplications());
    }

    //--------------------------------------------------------------

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/applications"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
