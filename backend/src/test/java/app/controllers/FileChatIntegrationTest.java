package app.controllers;

import app.daos.FileDAO;
import app.daos.MessageDAO;
import app.dtos.UserDTO;
import app.entities.Message;
import app.entities.UploadedFile;
import app.entities.User;
import app.exceptions.ApiException;
import app.services.ConversationService;
import app.services.FileService;
import app.services.FileValidator;
import app.services.LocalFileStorage;
import app.support.MessageTestSupport;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static app.services.FileValidatorTest.*;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.junit.jupiter.api.Assertions.*;

class FileChatIntegrationTest extends MessageTestSupport {
    @TempDir Path temporary;
    private Javalin server;
    private HttpClient client;
    private FileService files;
    private LocalFileStorage storage;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);

    //--------------------------------------------------------------

    @BeforeEach
    void startServer() {
        storage = new LocalFileStorage(temporary);
        files = new FileService(new FileDAO(em), storage, new FileValidator(), clock);
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.router.apiBuilder(() -> path("/api", MessageController.registerRoutes(service)));
        }).beforeMatched(ctx -> {
            String header = ctx.header("Test-User");
            if (header == null) throw new UnauthorizedResponse("Login required");
            ctx.attribute("user", UserDTO.builder().id(UUID.fromString(header)).build());
        }).exception(ApiException.class, (error, ctx) -> ctx.status(error.getStatus()).result(error.getMessage())).start(0);
        client = HttpClient.newHttpClient();
    }

    //--------------------------------------------------------------

    @AfterEach
    void stopServer() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    //--------------------------------------------------------------

    private List<String> uploads(User sender, int count) throws Exception {
        byte[] png = image("png");
        return files.upload(sender.getId(), java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> upload("document-" + i + ".png", png)).toList()).stream().map(dto -> dto.id().toString()).toList();
    }

    //--------------------------------------------------------------

    private HttpResponse<String> request(String method, String route, User user, Map<String, ?> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api" + route))
                .header("Test-User", user.getId().toString()).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    //--------------------------------------------------------------

    @Test
    void customerOneAndThreeAndAdminAttachmentsAppearInCorrectConversation() throws Exception {
        for (int count : List.of(1, 3)) {
            var created = request("POST", "/messages", alice, Map.of("subject", "Documents", "body", "Attached", "attachmentIds", uploads(alice, count)));
            assertEquals(201, created.statusCode(), created.body());
            assertEquals(count, json.readTree(created.body()).get("attachments").size());
        }
        var adminReply = request("POST", "/messages/threads/" + alice.getId(), admin, Map.of("body", "Reply", "attachmentIds", uploads(admin, 3)));
        assertEquals(201, adminReply.statusCode(), adminReply.body());
        var mine = request("GET", "/messages/mine", alice, null);
        var thread = request("GET", "/messages/threads/" + alice.getId(), admin, null);
        assertEquals(json.readTree(mine.body()), json.readTree(thread.body()));
        assertEquals(3, json.readTree(mine.body()).size());
        assertTrue(java.util.stream.StreamSupport.stream(json.readTree(mine.body()).get(0).get("attachments").spliterator(), false)
                .anyMatch(file -> file.get("originalName").asText().equals("document-0.png")));
        assertEquals("[]", request("GET", "/messages/mine", bob, null).body());
        assertEquals("[]", request("GET", "/messages/threads/" + bob.getId(), admin, null).body());
        assertFalse(mine.body().contains("storageKey"));
        assertEquals(3, countMessages());
    }

    //--------------------------------------------------------------

    @Test
    void rejectsInvalidForeignMissingReusedAndFourIdsWithoutPartialMessages() throws Exception {
        List<String> own = uploads(alice, 3);
        List<String> foreign = uploads(bob, 1);
        Map<Object, Integer> cases = new java.util.LinkedHashMap<>();
        cases.put(List.of(own.getFirst(), own.getFirst()), 400);
        cases.put(List.of(own.getFirst(), "invalid"), 400);
        cases.put(List.of(own.getFirst(), foreign.getFirst()), 403);
        cases.put(List.of(own.getFirst(), UUID.randomUUID().toString()), 404);
        cases.put(List.of(own.get(0), own.get(1), own.get(2), foreign.getFirst()), 400);
        cases.put("not-array", 400);
        for (var entry : cases.entrySet()) {
            assertEquals(entry.getValue(), request("POST", "/messages", alice,
                    Map.of("subject", "Documents", "body", "Attached", "attachmentIds", entry.getKey())).statusCode());
            assertEquals(0, countMessages());
            assertNull(new FileDAO(em).getById(UUID.fromString(own.getFirst())).getMessage());
        }
        assertEquals(201, request("POST", "/messages", alice, Map.of("subject", "Documents", "body", "Attached", "attachmentIds", own)).statusCode());
        assertEquals(400, request("POST", "/messages", alice, Map.of("subject", "Documents", "body", "Again", "attachmentIds", own)).statusCode());
        assertEquals(1, countMessages());
    }

    //--------------------------------------------------------------

    @Test
    void failedDatabaseWriteKeepsUnattachedFilesForControlledRetry() throws Exception {
        List<String> ids = uploads(alice, 3);
        MessageDAO failing = new MessageDAO(em) {
            @Override public Message createWithAttachments(Message message, List<UUID> ids, Consumer<List<UploadedFile>> validate) {
                message.setBody(null); // Real constraint failure inside the shared transaction.
                return super.createWithAttachments(message, ids, validate);
            }
        };
        ConversationService broken = new ConversationService(failing, clock, messageEmailService);
        Map<String, ?> body = Map.of("subject", "Documents", "body", "Attached", "attachmentIds", ids);
        assertEquals(500, assertThrows(ApiException.class, () -> broken.sendCustomer(alice.getId(), body)).getStatus());
        assertEquals(0, countMessages());
        for (String id : ids) {
            UploadedFile pending = new FileDAO(em).getById(UUID.fromString(id));
            assertNull(pending.getMessage());
            assertTrue(storage.exists(pending.getStorageKey()));
        }
        assertNotNull(service.sendCustomer(alice.getId(), body).getId());
        assertEquals(1, countMessages());
    }

    //--------------------------------------------------------------

    @Test
    void concurrentReuseProducesOnlyOneMessage() throws Exception {
        List<String> ids = uploads(alice, 1);
        Map<String, ?> body = Map.of("subject", "Documents", "body", "Attached", "attachmentIds", ids);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(() -> sendOutcome(body), () -> sendOutcome(body)));
            assertEquals(java.util.Set.of(201, 400), java.util.Set.of(results.get(0).get(), results.get(1).get()));
        }
        assertEquals(1, countMessages());
    }

    //--------------------------------------------------------------

    private int sendOutcome(Map<String, ?> body) {
        try {
            service.sendCustomer(alice.getId(), body);
            return 201;
        } catch (ApiException exception) {
            return exception.getStatus();
        }
    }

    //--------------------------------------------------------------

    @Test
    void textOnlyAndExistingTextValidationRemainUnchanged() throws Exception {
        assertEquals(201, request("POST", "/messages", alice, Map.of("subject", "Text", "body", "Hello")).statusCode());
        assertEquals(201, request("POST", "/messages/threads/" + alice.getId(), admin, Map.of("body", "Reply")).statusCode());
        var ids = uploads(alice, 1);
        for (var invalid : List.of(Map.of("subject", "Text", "body", "", "attachmentIds", ids),
                Map.of("subject", "", "body", "Text", "attachmentIds", ids),
                Map.of("subject", "Text", "body", "Text", "attachmentIds", ids, "senderId", bob.getId().toString()))) {
            assertEquals(400, request("POST", "/messages", alice, invalid).statusCode());
        }
        assertEquals(2, countMessages());
    }
}
