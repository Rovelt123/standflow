package app.controllers;

import app.dtos.UserDTO;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.security.SecurityService;
import app.support.MessageTestSupport;
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
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.junit.jupiter.api.Assertions.*;

class MessageControllerTest extends MessageTestSupport {
    private Javalin server;
    private HttpClient client;

    //--------------------------------------------------------------

    @BeforeEach
    void startServer() {
        SecurityService security = new SecurityService();
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.router.apiBuilder(() -> path("/api", () -> MessageController.registerRoutes(service).addEndpoints()));
        }).beforeMatched(ctx -> {
            // Isolated authentication fixture; use the real authorization policy and context shape.
            String header = ctx.header("Test-User");
            if (header == null) throw new UnauthorizedResponse("Authentication required");
            User user = dao.getUser(UUID.fromString(header));
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
    void customerAndAdminFlowsReturnDirectDTOsAndSelectiveReadUpdates() throws Exception {
        var created = send("POST", "/api/messages", "{\"subject\":\"Question\",\"body\":\"Hello\"}", alice);
        assertEquals(201, created.statusCode());
        JsonNode dto = json.readTree(created.body());
        assertEquals(7, dto.size());
        assertEquals(alice.getId().toString(), dto.get("senderId").asText());
        assertEquals(admin.getId().toString(), dto.get("recipientId").asText());
        assertFalse(dto.get("read").asBoolean());
        assertTrue(dto.get("createdAt").isTextual());
        assertFalse(dto.has("sender"));
        assertFalse(dto.has("recipient"));
        UUID id = UUID.fromString(dto.get("id").asText());
        service.sendCustomer(bob.getId(), Map.of("subject", "Private", "body", "Secret"));
        var mine = send("GET", "/api/messages/mine?customerId=" + bob.getId() + "&senderId=" + bob.getId(), null, alice);
        assertEquals(200, mine.statusCode());
        assertEquals(1, json.readTree(mine.body()).size());
        assertEquals(id.toString(), json.readTree(mine.body()).get(0).get("id").asText());
        String thread = "/api/messages/threads/" + alice.getId();
        assertEquals(200, send("GET", thread, null, admin).statusCode());
        assertFalse(reload(id).isRead());
        var reply = send("POST", thread, "{\"body\":\"Answer\"}", admin);
        assertEquals(201, reply.statusCode());
        assertEquals("Question", json.readTree(reply.body()).get("subject").asText());
        UUID replyId = UUID.fromString(json.readTree(reply.body()).get("id").asText());
        var read = send("PATCH", thread + "/read", null, admin);
        assertEquals(204, read.statusCode());
        assertTrue(read.body().isEmpty());
        assertTrue(reload(id).isRead());
        assertFalse(reload(replyId).isRead());
        assertEquals(2, json.readTree(send("GET", thread, null, admin).body()).size());
        assertTrue(service.getThreads(admin.getId(), null).stream()
                .filter(t -> t.getCustomerId().equals(bob.getId())).findFirst().orElseThrow().isUnread());
    }

    //--------------------------------------------------------------

    @Test
    void adminCanStartConversationAndRequiresSubjectOnlyForFirstMessage() throws Exception {
        String thread = "/api/messages/threads/" + alice.getId();
        assertEquals(400, send("POST", thread, "{\"body\":\"Hello\"}", admin).statusCode());
        assertEquals(201, send("POST", thread, "{\"subject\":\"Welcome\",\"body\":\"Hello\"}", admin).statusCode());
        assertEquals(400, send("POST", thread, "{\"subject\":null,\"body\":\"Reply\"}", admin).statusCode());
        assertEquals(201, send("POST", thread, "{\"body\":\"Reply\"}", admin).statusCode());
        assertEquals(2, json.readTree(send("GET", "/api/messages/mine", null, alice).body()).size());
        assertFalse(service.getThreads(admin.getId(), null).getFirst().isUnread());
    }

    //--------------------------------------------------------------

    @Test
    void overviewSortsAndEmptyOrInvalidThreadsHaveExpectedResponses() throws Exception {
        assertEquals("[]", send("GET", "/api/messages/threads", null, admin).body());
        assertEquals("[]", send("GET", "/api/messages/mine", null, alice).body());
        String thread = "/api/messages/threads/" + alice.getId();
        assertEquals("[]", send("GET", thread, null, admin).body());
        assertEquals(204, send("PATCH", thread + "/read", null, admin).statusCode());
        message(alice, admin, "Zulu", "Alice", 2, false);
        message(bob, admin, "Alpha", "Bob", 1, false);
        for (String sort : List.of("name", "company", "subject", "date")) {
            var response = send("GET", "/api/messages/threads?sort=" + sort, null, admin);
            assertEquals(200, response.statusCode());
            JsonNode threads = json.readTree(response.body());
            UUID expected = sort.equals("name") || sort.equals("date") ? alice.getId() : bob.getId();
            assertEquals(expected.toString(), threads.get(0).get("customerId").asText());
            assertEquals(7, threads.get(0).size());
        }
        assertEquals(400, send("GET", "/api/messages/threads?sort=wrong", null, admin).statusCode());
        for (String method : List.of("GET", "POST", "PATCH")) {
            String suffix = method.equals("PATCH") ? "/read" : "";
            String body = method.equals("POST") ? "{\"body\":\"b\"}" : null;
            assertEquals(400, send(method, "/api/messages/threads/not-a-uuid" + suffix, body, admin).statusCode());
            assertEquals(404, send(method, "/api/messages/threads/" + UUID.randomUUID() + suffix, body, admin).statusCode());
            assertEquals(400, send(method, "/api/messages/threads/" + owner.getId() + suffix, body, admin).statusCode());
        }
    }

    //--------------------------------------------------------------

    @Test
    void malformedJsonWrongTypesAndControlledFieldsReturn400WithoutWrites() throws Exception {
        for (String invalid : List.of("", "null", "[]", "\"text\"", "{", "{} {}",
                "{\"subject\":\"a\",\"subject\":\"b\",\"body\":\"b\"}", "{}")) {
            assertEquals(400, send("POST", "/api/messages", invalid, alice).statusCode(), invalid);
            assertEquals(400, send("POST", "/api/messages/threads/" + alice.getId(), invalid, admin).statusCode(), invalid);
        }
        for (String field : List.of("subject", "body")) {
            for (String value : List.of("null", "42", "true", "[]", "{}", "\" \"",
                    "\"" + "x".repeat(field.equals("subject") ? 201 : 5001) + "\"")) {
                var request = json.createObjectNode().put("subject", "s").put("body", "b");
                request.set(field, json.readTree(value));
                assertEquals(400, send("POST", "/api/messages", request.toString(), alice).statusCode());
                assertEquals(400, send("POST", "/api/messages/threads/" + alice.getId(), request.toString(), admin).statusCode());
            }
        }
        for (String field : List.of("id", "senderId", "recipientId", "createdAt", "read", "unknown")) {
            String body = json.createObjectNode().put("subject", "s").put("body", "b").putNull(field).toString();
            assertEquals(400, send("POST", "/api/messages", body, alice).statusCode());
            assertEquals(400, send("POST", "/api/messages/threads/" + alice.getId(), body, admin).statusCode());
            assertEquals(400, send("PATCH", "/api/messages/threads/" + alice.getId() + "/read", body, admin).statusCode());
        }
        assertEquals(0, countMessages());
        service.sendCustomer(alice.getId(), Map.of("subject", "s", "body", "b"));
        JsonNode before = storedMessages();
        for (String field : List.of("id", "senderId", "recipientId", "createdAt", "read", "unknown")) {
            String body = json.createObjectNode().put("body", "reply").putNull(field).toString();
            assertEquals(400, send("POST", "/api/messages/threads/" + alice.getId(), body, admin).statusCode());
        }
        assertEquals(before, storedMessages());
    }

    //--------------------------------------------------------------

    @Test
    void routesAndServiceEnforceRolesWhileOwnerBypassCannotImpersonateAdmin() throws Exception {
        for (String customerPath : List.of("/api/messages", "/api/messages/mine")) {
            String method = customerPath.endsWith("mine") ? "GET" : "POST";
            String body = method.equals("POST") ? "{\"subject\":\"s\",\"body\":\"b\"}" : null;
            assertEquals(401, send(method, customerPath, body, null).statusCode());
            assertEquals(401, send(method, customerPath, body, admin).statusCode());
            assertEquals(403, send(method, customerPath, body, owner).statusCode());
            admin.getRoles().add(Role.USER);
            new app.daos.UserDAO(em).update(admin);
            assertEquals(403, send(method, customerPath, body, admin).statusCode());
            admin.getRoles().remove(Role.USER);
            new app.daos.UserDAO(em).update(admin);
        }
        for (String method : List.of("GET", "POST", "PATCH")) {
            String route = "/api/messages/threads/" + alice.getId() + (method.equals("PATCH") ? "/read" : "");
            String body = method.equals("POST") ? "{\"subject\":\"s\",\"body\":\"b\"}" : null;
            assertEquals(401, send(method, route, body, alice).statusCode());
            assertEquals(403, send(method, route, body, owner).statusCode());
            assertEquals(401, send(method, route, body, null).statusCode());
        }
        assertEquals(401, send("GET", "/api/messages/threads", null, alice).statusCode());
        assertEquals(403, send("GET", "/api/messages/threads", null, owner).statusCode());
        assertEquals(0, countMessages());
    }

    //--------------------------------------------------------------

    @Test
    void invalidAdminConfigurationReturns500WithoutChangingMessages() throws Exception {
        message(alice, admin, "s", "b", 0, false);
        message(admin, alice, "s", "reply", 1, false);
        JsonNode before = storedMessages();
        admin.setRoles(new java.util.HashSet<>());
        new app.daos.UserDAO(em).update(admin);
        assertTrue(dao.getAdmins().isEmpty());
        assertConfigurationFailureOverHttp();
        assertEquals(before, storedMessages());
        admin.getRoles().add(Role.ADMIN);
        new app.daos.UserDAO(em).update(admin);
        user("Second", "Admin", Role.ADMIN);
        assertEquals(2, dao.getAdmins().size());
        assertConfigurationFailureOverHttp();
        assertEquals(before, storedMessages());
    }

    //--------------------------------------------------------------

    @Test
    void everyGetPreservesPersistedReadStateAndConversationOrder() throws Exception {
        var later = message(admin, alice, "s", "later", 2, false);
        var earlier = message(alice, admin, "s", "earlier", 0, false);
        message(bob, admin, "other", "private", 1, false);
        message(alice, admin, "s", "already read", 3, true);
        JsonNode before = storedMessages();
        for (String route : List.of("/api/messages/mine", "/api/messages/threads/" + alice.getId())) {
            var response = send("GET", route, null, route.endsWith("mine") ? alice : admin);
            assertEquals(200, response.statusCode());
            JsonNode messages = json.readTree(response.body());
            assertEquals(3, messages.size());
            assertEquals(earlier.getId().toString(), messages.get(0).get("id").asText());
            assertEquals(later.getId().toString(), messages.get(1).get("id").asText());
            assertEquals(before, storedMessages());
        }
        for (String suffix : List.of("", "?sort=name", "?sort=company", "?sort=subject", "?sort=date")) {
            assertEquals(200, send("GET", "/api/messages/threads" + suffix, null, admin).statusCode());
            assertEquals(before, storedMessages());
        }
    }

    //--------------------------------------------------------------

    @Test
    void mixedUserAdminIsRejectedAsCustomerAndAllowedAsDesignatedAdmin() throws Exception {
        admin.setRoles(new HashSet<>(Set.of(Role.USER, Role.ADMIN)));
        new app.daos.UserDAO(em).update(admin);
        assertEquals(Set.of(Role.USER, Role.ADMIN), dao.getUser(admin.getId()).getRoles());
        assertEquals(403, send("GET", "/api/messages/mine", null, admin).statusCode());
        assertEquals(403, send("POST", "/api/messages", "{\"subject\":\"s\",\"body\":\"b\"}", admin).statusCode());
        String thread = "/api/messages/threads/" + alice.getId();
        assertEquals(201, send("POST", thread, "{\"subject\":\"s\",\"body\":\"hello\"}", admin).statusCode());
        assertEquals(201, send("POST", thread, "{\"body\":\"reply\"}", admin).statusCode());
        assertEquals(200, send("GET", thread, null, admin).statusCode());
        assertEquals(200, send("GET", "/api/messages/threads", null, admin).statusCode());
        assertEquals(204, send("PATCH", thread + "/read", null, admin).statusCode());
        assertEquals(400, send("GET", "/api/messages/threads/" + admin.getId(), null, admin).statusCode());
    }

    //--------------------------------------------------------------

    @Test
    void everySuccessResponseUsesExactDTOFieldsAndTypesWithoutWrappers() throws Exception {
        var customer = send("POST", "/api/messages", "{\"subject\":\"Question\",\"body\":\"Hello\"}", alice);
        assertEquals(201, customer.statusCode());
        assertMessageShape(json.readTree(customer.body()));
        String thread = "/api/messages/threads/" + alice.getId();
        var reply = send("POST", thread, "{\"body\":\"Answer\"}", admin);
        assertEquals(201, reply.statusCode());
        assertMessageShape(json.readTree(reply.body()));
        var initial = send("POST", "/api/messages/threads/" + bob.getId(),
                "{\"subject\":\"Welcome\",\"body\":\"Information\"}", admin);
        assertEquals(201, initial.statusCode());
        assertMessageShape(json.readTree(initial.body()));
        for (String route : List.of("/api/messages/mine", thread)) {
            var response = send("GET", route, null, route.endsWith("mine") ? alice : admin);
            assertEquals(200, response.statusCode());
            JsonNode list = json.readTree(response.body());
            assertTrue(list.isArray());
            assertEquals(2, list.size());
            list.forEach(this::assertMessageShape);
        }
        user("No messages", "Empty", Role.USER);
        message(owner, admin, "Excluded", "Not a customer", 4, false);
        var overview = send("GET", "/api/messages/threads", null, admin);
        assertEquals(200, overview.statusCode());
        JsonNode threads = json.readTree(overview.body());
        assertTrue(threads.isArray());
        assertEquals(2, threads.size());
        Set<String> customers = new HashSet<>();
        for (JsonNode dto : threads) {
            assertEquals(Set.of("customerId", "customerName", "company", "subject", "lastMessage", "lastMessageAt", "unread"), fields(dto));
            for (String field : List.of("customerId", "customerName", "company", "subject", "lastMessage", "lastMessageAt")) {
                assertTrue(dto.get(field).isTextual(), field);
            }
            UUID.fromString(dto.get("customerId").asText());
            java.time.LocalDateTime.parse(dto.get("lastMessageAt").asText());
            assertTrue(dto.get("unread").isBoolean());
            customers.add(dto.get("customerId").asText());
        }
        assertEquals(Set.of(alice.getId().toString(), bob.getId().toString()), customers);
        var read = send("PATCH", thread + "/read", null, admin);
        assertEquals(204, read.statusCode());
        assertEquals("", read.body());
    }

    //--------------------------------------------------------------

    private void assertConfigurationFailureOverHttp() throws Exception {
        assertEquals(500, send("POST", "/api/messages", "{\"subject\":\"s\",\"body\":\"b\"}", alice).statusCode());
        assertEquals(500, send("GET", "/api/messages/mine", null, alice).statusCode());
        // OWNER passes the unchanged route policy even when no ADMIN exists.
        for (String route : List.of("/api/messages/threads", "/api/messages/threads/" + alice.getId())) {
            assertEquals(500, send("GET", route, null, owner).statusCode());
        }
        assertEquals(500, send("POST", "/api/messages/threads/" + alice.getId(), "{\"body\":\"b\"}", owner).statusCode());
        assertEquals(500, send("PATCH", "/api/messages/threads/" + alice.getId() + "/read", null, owner).statusCode());
    }

    //--------------------------------------------------------------

    private JsonNode storedMessages() {
        try (var reader = emf.createEntityManager()) {
            return json.valueToTree(reader.createQuery("select m from Message m order by m.id", app.entities.Message.class)
                    .getResultList().stream().map(new app.mappers.MessageMapper()::toDTO).toList());
        }
    }

    //--------------------------------------------------------------

    private void assertMessageShape(JsonNode dto) {
        assertTrue(dto.isObject());
        assertEquals(Set.of("id", "senderId", "recipientId", "subject", "body", "createdAt", "read"), fields(dto));
        for (String field : List.of("id", "senderId", "recipientId", "subject", "body", "createdAt")) {
            assertTrue(dto.get(field).isTextual(), field);
        }
        for (String field : List.of("id", "senderId", "recipientId")) UUID.fromString(dto.get(field).asText());
        java.time.LocalDateTime.parse(dto.get("createdAt").asText());
        assertTrue(dto.get("read").isBoolean());
    }

    //--------------------------------------------------------------

    private Set<String> fields(JsonNode dto) {
        Set<String> names = new HashSet<>();
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
