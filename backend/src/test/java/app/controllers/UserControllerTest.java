package app.controllers;

import app.configs.TestHibernateConfig;
import app.daos.UserDAO;
import app.daos.ApplicationDAO;
import app.entities.User;
import app.entities.Application;
import app.enums.Role;
import app.mappers.UserMapper;
import app.security.SecurityService;
import app.security.JWTTokenGenerator;
import app.server.Setup;
import app.services.PasswordService;
import app.services.ApplicationService;
import app.services.UserService;
import app.exceptions.ApiException;
import app.services.SimpleApplicationPdfGenerator;
import app.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static io.javalin.apibuilder.ApiBuilder.path;

class UserControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static EntityManagerFactory emf;
    private static Setup server;
    private static String baseUrl;
    private static final java.util.List<String> welcomeRecipients = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean failWelcome;

    //--------------------------------------------------------------

    @BeforeAll
    static void startServer() {
        emf = TestHibernateConfig.getTestEmf();
        server = new Setup(emf.createEntityManager(), 0);
        server.getApp().exception(ApiException.class, (error, ctx) -> ctx.status(error.getStatus()).result(error.getMessage()));
        server.getApp().exception(Exception.class, (error, ctx) -> ctx.status(500).result(error.getMessage()));
        UserService userService = new UserService(new UserDAO(Setup.em), recipient -> {
            try (var reader = emf.createEntityManager()) {
                assertNotNull(reader.find(User.class, recipient.getId()));
            }
            welcomeRecipients.add(recipient.getEmail());
            if (failWelcome) throw new ApiException(500, "SMTP failed");
        });
        server.getApp().unsafeConfig().router.apiBuilder(() -> path("/api", () -> {
            UserController.registerRoutes(userService).addEndpoints();
            ApplicationController.registerRoutes().addEndpoints();
        }));
        // Exercise persistence and PDF generation without sending real email during tests.
        ApplicationService applicationService = new ApplicationService(new ApplicationDAO(Setup.em),
                () -> new ApplicationService.IntakeStatus(true, null), Clock.systemUTC(),
                new SimpleApplicationPdfGenerator(), (application, pdf, filename) -> assertTrue(pdf.length > 0));
        server.getApp().unsafeConfig().router.apiBuilder(() -> path("/api/test",
                ApplicationController.registerRoutes(applicationService)));
        baseUrl = "http://localhost:" + server.getApp().port() + "/api";
    }

    //--------------------------------------------------------------

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.getApp().stop();
            Setup.em.close();
        }
        if (emf != null) emf.close();
    }

    //--------------------------------------------------------------

    @Test
    void registrationPersistsSeparateConsentsAndHashesPassword() throws Exception {
        for (boolean marketing : new boolean[]{false, true}) {
            Map<String, Object> request = registration();
            request.put("acceptMarketing", marketing);
            HttpResponse<String> response = post("/users/auth/register", request, null);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals(1, welcomeRecipients.stream().filter(request.get("email")::equals).count());
            JsonNode data = JSON.readTree(response.body()).path("data");
            assertFalse(data.path("token").asText().isBlank());
            assertTrue(data.path("data").path("acceptTerms").asBoolean());
            assertTrue(data.path("data").path("acceptPrivacy").asBoolean());
            assertEquals(marketing, data.path("data").path("acceptMarketing").asBoolean());
            assertFalse(response.body().contains("password"));
            try (var reader = emf.createEntityManager()) {
                User saved = new UserDAO(reader).getByEmail((String) request.get("email"));
                assertTrue(saved.isAcceptTerms());
                assertTrue(saved.isAcceptPrivacy());
                assertEquals(marketing, saved.isAcceptMarketing());
                assertNotEquals(request.get("password"), saved.getPassword());
                assertTrue(PasswordService.passwordEquals((String) request.get("password"), saved.getPassword()));
            }
        }
    }

    //--------------------------------------------------------------

    @Test
    void welcomeFailureKeepsAccountAndDuplicateOrInvalidRegistrationDoesNotSend() throws Exception {
        Map<String, Object> body = registration();
        int before = welcomeRecipients.size();
        failWelcome = true;
        try {
            assertEquals(201, post("/users/auth/register", body, null).statusCode());
        } finally {
            failWelcome = false;
        }
        assertEquals(before + 1, welcomeRecipients.size());
        assertEquals(200, post("/users/auth/login", Map.of("email", body.get("email"),
                "password", body.get("password")), null).statusCode());
        assertEquals(409, post("/users/auth/register", body, null).statusCode());
        body = registration();
        body.put("acceptTerms", false);
        assertEquals(400, post("/users/auth/register", body, null).statusCode());
        assertEquals(before + 1, welcomeRecipients.size());
    }

    //--------------------------------------------------------------

    @Test
    void requiredConsentsRejectMissingFalseNullAndNonBooleanValues() throws Exception {
        for (String field : new String[]{"acceptTerms", "acceptPrivacy"}) {
            for (Object value : new Object[]{false, null, "true", 1}) {
                Map<String, Object> request = registration();
                request.put(field, value);
                assertEquals(400, post("/users/auth/register", request, null).statusCode());
            }
            Map<String, Object> request = registration();
            request.remove(field);
            assertEquals(400, post("/users/auth/register", request, null).statusCode());
        }
    }

    //--------------------------------------------------------------

    @Test
    void marketingIsOptionalButMustBeBooleanWhenProvided() throws Exception {
        Map<String, Object> request = registration();
        request.remove("acceptMarketing");
        HttpResponse<String> response = post("/users/auth/register", request, null);
        assertEquals(201, response.statusCode());
        assertFalse(JSON.readTree(response.body()).path("data").path("data").path("acceptMarketing").asBoolean());
        request = registration();
        request.put("acceptMarketing", "false");
        assertEquals(400, post("/users/auth/register", request, null).statusCode());
    }

    //--------------------------------------------------------------

    @Test
    void registrationPreservesValidationAndDuplicateDetection() throws Exception {
        for (String field : new String[]{"company", "firstName", "lastName", "cvr", "email", "phone", "address", "city", "password", "confirmPassword"}) {
            Map<String, Object> request = registration();
            request.remove(field);
            assertEquals(400, post("/users/auth/register", request, null).statusCode(), field);
        }
        for (String password : new String[]{"short", "lowercase!", "UPPERCASE!", "NoSpecial123", "Ab!".repeat(11)}) {
            Map<String, Object> request = registration();
            request.put("password", password);
            request.put("confirmPassword", password);
            assertEquals(400, post("/users/auth/register", request, null).statusCode());
        }
        Map<String, Object> request = registration();
        request.put("confirmPassword", "AnotherPassword!");
        assertEquals(400, post("/users/auth/register", request, null).statusCode());
        request = registration();
        assertEquals(201, post("/users/auth/register", request, null).statusCode());
        assertEquals(409, post("/users/auth/register", request, null).statusCode());
    }

    //--------------------------------------------------------------

    @Test
    void loginKeepsDefaultLifetimeAndRememberMeExtendsIt() throws Exception {
        Map<String, Object> request = registration();
        assertEquals(201, post("/users/auth/register", request, null).statusCode());
        Map<String, Object> login = new HashMap<>(Map.of("email", request.get("email"), "password", request.get("password")));
        long defaultLifetime = Long.parseLong(Utils.getPropertyValue("TOKEN_EXPIRE_TIME", "config.properties"));
        for (Boolean rememberMe : new Boolean[]{null, false, true}) {
            if (rememberMe != null) login.put("rememberMe", rememberMe);
            long before = System.currentTimeMillis();
            HttpResponse<String> response = post("/users/auth/login", login, null);
            assertEquals(200, response.statusCode(), response.body());
            String token = JSON.readTree(response.body()).path("data").path("token").asText();
            long expiration = SignedJWT.parse(token).getJWTClaimsSet().getExpirationTime().getTime();
            long lifetime = Boolean.TRUE.equals(rememberMe) ? Math.max(Duration.ofDays(30).toMillis(), defaultLifetime * 2) : defaultLifetime;
            assertTrue(Math.abs(expiration - before - lifetime) < 5000);
            assertTrue(JWTTokenGenerator.tokenIsValid(token, Utils.getPropertyValue("SECRET_KEY", "config.properties")));
            assertEquals(request.get("email"), SignedJWT.parse(token).getJWTClaimsSet().getSubject());
        }
        login.put("rememberMe", "true");
        assertEquals(400, post("/users/auth/login", login, null).statusCode());
        login.remove("rememberMe");
        login.put("password", "WrongPassword!");
        assertEquals(401, post("/users/auth/login", login, null).statusCode());
        login.put("email", "unknown@example.dk");
        assertEquals(401, post("/users/auth/login", login, null).statusCode());
    }

    //--------------------------------------------------------------

    @Test
    void malformedBodiesAreBadRequests() throws Exception {
        for (String endpoint : new String[]{"register", "login"}) {
            for (String body : new String[]{"null", "[]", "{", "{}"}) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/users/auth/" + endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                assertEquals(400, CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            }
        }
    }

    //--------------------------------------------------------------

    @Test
    void applicationsRequireAValidLogin() throws Exception {
        Map<String, Object> application = new HashMap<>(Map.of(
                "company", "Test", "contact", "Test Person", "cvr", "12345678", "email", "app@example.dk",
                "phone", "12345678", "address", "Testvej 1", "city", "2100 København", "products", "Keramik",
                "standType", "A", "tables", 1));
        application.put("chairs", 2);
        application.put("previousExhibitor", false);
        assertEquals(401, post("/applications", application, null).statusCode());
        assertEquals(401, post("/applications", application, "invalid").statusCode());
        HttpResponse<String> registered = post("/users/auth/register", registration(), null);
        String token = JSON.readTree(registered.body()).path("data").path("token").asText();
        assertEquals(400, post("/applications", Map.of(), token).statusCode());
        HttpResponse<String> response = post("/test/applications", application, token);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals("PENDING", JSON.readTree(response.body()).path("status").asText());
        assertFalse(JSON.readTree(response.body()).path("createdAt").asText().isBlank());
    }

    //--------------------------------------------------------------

    @Test
    void adminCanListReadAndUpdateApplicationsWhileOrdinaryUsersCannot() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String user = tokenFor(Role.USER);
        String owner = tokenFor(Role.OWNER);
        UUID id = createAdminTestApplication(user);
        String path = "/applications/" + id;
        for (String token : new String[]{null, user}) {
            assertEquals(401, request("GET", "/applications", null, token).statusCode());
            assertEquals(401, request("GET", path, null, token).statusCode());
            assertEquals(401, request("PATCH", path + "/status", Map.of("status", "ACCEPTED"), token).statusCode());
            assertEquals(401, request("PUT", path + "/comment", Map.of("comment", "Not allowed"), token).statusCode());
        }
        HttpResponse<String> list = request("GET", "/applications", null, admin);
        assertEquals(200, list.statusCode());
        assertTrue(JSON.readTree(list.body()).isArray());
        assertEquals(200, request("GET", path, null, owner).statusCode());
        HttpResponse<String> comment = request("PUT", path + "/comment", Map.of("comment", "Mangler CVR"), admin);
        assertEquals(200, comment.statusCode(), comment.body());
        assertEquals("Mangler CVR", JSON.readTree(comment.body()).path("comment").asText());
        for (String status : new String[]{"ACCEPTED", "REJECTED", "INFO_REQUESTED"}) {
            HttpResponse<String> changed = request("PATCH", path + "/status", Map.of("status", status), admin);
            assertEquals(200, changed.statusCode(), changed.body());
            assertEquals(status, JSON.readTree(changed.body()).path("status").asText());
        }
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, id);
            assertEquals("INFO_REQUESTED", saved.getStatus().name());
            assertEquals("Mangler CVR", saved.getInternalComment());
        }
        JsonNode detail = JSON.readTree(request("GET", path, null, admin).body());
        assertEquals(id.toString(), detail.path("id").asText());
        assertEquals("Mangler CVR", detail.path("comment").asText());
    }

    //--------------------------------------------------------------

    @Test
    void adminEndpointsRejectBadIdsBodiesAndMissingApplications() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        UUID id = createAdminTestApplication(admin);
        for (String suffix : new String[]{"bad-id", UUID.randomUUID().toString()}) {
            int expected = suffix.equals("bad-id") ? 400 : 404;
            assertEquals(expected, request("GET", "/applications/" + suffix, null, admin).statusCode());
            assertEquals(expected, request("PATCH", "/applications/" + suffix + "/status", Map.of("status", "ACCEPTED"), admin).statusCode());
            assertEquals(expected, request("PUT", "/applications/" + suffix + "/comment", Map.of("comment", "note"), admin).statusCode());
        }
        for (Object body : new Object[]{null, Map.of(), Map.of("status", "PENDING"), Map.of("status", 1), Map.of("status", "INVALID")}) {
            assertEquals(400, request("PATCH", "/applications/" + id + "/status", body, admin).statusCode());
        }
        for (Object body : new Object[]{null, Map.of(), Map.of("comment", false), Map.of("comment", "x".repeat(5001))}) {
            assertEquals(400, request("PUT", "/applications/" + id + "/comment", body, admin).statusCode());
        }
    }

    //--------------------------------------------------------------

    @Test
    void currentUserReturnsOnlyTheAuthenticatedProfile() throws Exception {
        assertEquals(401, request("GET", "/users/me", null, null).statusCode());
        Map<String, Object> registration = registration();
        HttpResponse<String> registered = post("/users/auth/register", registration, null);
        String token = JSON.readTree(registered.body()).path("data").path("token").asText();
        HttpResponse<String> response = request("GET", "/users/me", null, token);
        assertEquals(200, response.statusCode());
        JsonNode user = JSON.readTree(response.body());
        for (String field : new String[]{"company", "cvr", "email", "phone", "address", "city"}) {
            assertEquals(registration.get(field), user.path(field).asText());
        }
        assertEquals(registration.get("firstName"), user.path("firstname").asText());
        assertEquals(registration.get("lastName"), user.path("lastname").asText());
        assertFalse(user.has("password"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    //--------------------------------------------------------------

    @Test
    void authenticatedUserCanReadAndChangeMarketingConsent() throws Exception {
        Map<String, Object> registration = registration();
        registration.put("acceptMarketing", true);
        HttpResponse<String> registered = post("/users/auth/register", registration, null);
        String token = JSON.readTree(registered.body()).path("data").path("token").asText();

        HttpResponse<String> current = request("GET", "/users/me/consent", null, token);
        assertEquals(200, current.statusCode(), current.body());
        assertTrue(JSON.readTree(current.body()).path("marketingConsent").asBoolean());

        HttpResponse<String> withdrawn = request("PATCH", "/users/me/consent",
                Map.of("marketingConsent", false), token);
        assertEquals(200, withdrawn.statusCode(), withdrawn.body());
        assertFalse(JSON.readTree(withdrawn.body()).path("marketingConsent").asBoolean());

        try (var reader = emf.createEntityManager()) {
            User saved = new UserDAO(reader).getByEmail((String) registration.get("email"));
            assertFalse(saved.isAcceptMarketing());
            assertTrue(saved.isEmailNotifications());
        }

        HttpResponse<String> enabled = request("PATCH", "/users/me/consent",
                Map.of("marketingConsent", true), token);
        assertEquals(200, enabled.statusCode(), enabled.body());
        assertTrue(JSON.readTree(enabled.body()).path("marketingConsent").asBoolean());

        try (var reader = emf.createEntityManager()) {
            User saved = new UserDAO(reader).getByEmail((String) registration.get("email"));
            assertTrue(saved.isAcceptMarketing());
        }
    }

    //--------------------------------------------------------------

    @Test
    void unsubscribeRevokesMarketingConsentAndIsIdempotent() throws Exception {
        Map<String, Object> registration = registration();
        registration.put("acceptMarketing", true);
        HttpResponse<String> registered = post("/users/auth/register", registration, null);
        String token = JSON.readTree(registered.body()).path("data").path("token").asText();

        for (int i = 0; i < 2; i++) {
            HttpResponse<String> response = request("POST", "/users/me/unsubscribe", null, token);
            assertEquals(200, response.statusCode(), response.body());
            assertFalse(JSON.readTree(response.body()).path("marketingConsent").asBoolean());
        }

        assertEquals(200, request("GET", "/users/me", null, token).statusCode());
        try (var reader = emf.createEntityManager()) {
            User saved = new UserDAO(reader).getByEmail((String) registration.get("email"));
            assertNotNull(saved);
            assertFalse(saved.isAcceptMarketing());
            assertTrue(saved.isEmailNotifications());
        }
    }

    //--------------------------------------------------------------

    @Test
    void accountDeletionRequiresAuthenticationAndDeletesOnlyAuthenticatedUser() throws Exception {
        assertEquals(401, request("DELETE", "/users/me",
                Map.of("currentPassword", "StrongPassword!", "confirmDelete", true), null).statusCode());

        Map<String, Object> firstRegistration = registration();
        HttpResponse<String> firstRegistered = post("/users/auth/register", firstRegistration, null);
        JsonNode firstData = JSON.readTree(firstRegistered.body()).path("data");
        String firstToken = firstData.path("token").asText();
        UUID firstUserId = UUID.fromString(firstData.path("data").path("id").asText());

        Map<String, Object> secondRegistration = registration();
        HttpResponse<String> secondRegistered = post("/users/auth/register", secondRegistration, null);
        JsonNode secondData = JSON.readTree(secondRegistered.body()).path("data");
        UUID secondUserId = UUID.fromString(secondData.path("data").path("id").asText());

        UUID firstApplicationId = createAdminTestApplication(firstToken);
        HttpResponse<String> deleted = request("DELETE", "/users/me", Map.of(
                "currentPassword", "StrongPassword!",
                "confirmDelete", true,
                "id", secondUserId.toString()), firstToken);
        assertEquals(204, deleted.statusCode(), deleted.body());

        try (var reader = emf.createEntityManager()) {
            assertNull(reader.find(User.class, firstUserId));
            assertNotNull(reader.find(User.class, secondUserId));
            assertNull(reader.find(Application.class, firstApplicationId));
        }
    }

    //--------------------------------------------------------------

    @Test
    void adminCanSaveCustomerNoteAndOnlyOwningCustomerCanReadIt() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String customer = tokenFor(Role.USER);
        String other = tokenFor(Role.USER);
        UUID id = createAdminTestApplication(customer);
        String path = "/applications/" + id + "/comment";
        Map<String, String> notes = Map.of("comment", "Private admin note", "customerNote", "Oplys venligst dit CVR");
        assertEquals(401, request("PUT", path, notes, customer).statusCode());
        assertEquals(401, request("PUT", path, notes, null).statusCode());
        var saved = request("PUT", path, notes, admin);
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals("Oplys venligst dit CVR", JSON.readTree(saved.body()).path("customerNote").asText());
        assertEquals("Private admin note", JSON.readTree(saved.body()).path("comment").asText());
        var own = request("GET", "/applications/mine/" + id, null, customer);
        assertEquals(200, own.statusCode(), own.body());
        JsonNode ownBody = JSON.readTree(own.body());
        assertEquals("Oplys venligst dit CVR", ownBody.path("customerNote").asText());
        assertFalse(ownBody.has("comment"));
        assertFalse(ownBody.has("internalComment"));
        assertFalse(own.body().contains("Private admin note"));
        JsonNode list = JSON.readTree(request("GET", "/applications/mine", null, customer).body());
        assertTrue(list.isArray());
        assertEquals("Oplys venligst dit CVR", list.get(0).path("customerNote").asText());
        assertEquals(404, request("GET", "/applications/mine/" + id, null, other).statusCode());
        assertEquals(400, request("PUT", path, Map.of("comment", "Changed", "customerNote", 123), admin).statusCode());
        assertEquals(400, request("PUT", path, Map.of("comment", "Changed", "customerNote", "x".repeat(5001)), admin).statusCode());
        var legacy = request("PUT", path, Map.of("comment", "Legacy update"), admin);
        assertEquals(200, legacy.statusCode());
        assertEquals("Oplys venligst dit CVR", JSON.readTree(legacy.body()).path("customerNote").asText());
    }

    //--------------------------------------------------------------

    private static String tokenFor(Role role) throws Exception {
        Map<String, Object> registration = registration();
        assertEquals(201, post("/users/auth/register", registration, null).statusCode());
        UserDAO dao = new UserDAO(Setup.em);
        User user = dao.getByEmail((String) registration.get("email"));
        user.setRoles(new HashSet<>(Set.of(Role.USER)));
        user.getRoles().add(role);
        dao.update(user);
        return new SecurityService().createToken(new UserMapper().toDTO(user));
    }

    //--------------------------------------------------------------

    private static UUID createAdminTestApplication(String token) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("company", "Test", "contact", "Test Person",
                "cvr", "12345678", "email", "test@example.dk", "phone", "12345678", "address", "Testvej 1",
                "city", "2100 København", "products", "Keramik", "standType", "A", "tables", 1));
        body.put("chairs", 2);
        body.put("previousExhibitor", false);
        HttpResponse<String> response = post("/test/applications", body, token);
        assertEquals(201, response.statusCode(), response.body());
        JsonNode receipt = JSON.readTree(response.body());
        assertEquals(3, receipt.size());
        assertEquals("PENDING", receipt.path("status").asText());
        assertFalse(receipt.has("comment"));
        return UUID.fromString(receipt.path("id").asText());
    }

    //--------------------------------------------------------------

    private static HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    //--------------------------------------------------------------

    private static Map<String, Object> registration() {
        Map<String, Object> request = new HashMap<>(Map.of(
                "company", "Testvirksomhed", "firstName", "Test", "lastName", "Person", "cvr", "12345678",
                "email", UUID.randomUUID() + "@example.dk", "phone", "12345678", "address", "Testvej 1",
                "city", "2100 København", "password", "StrongPassword!", "confirmPassword", "StrongPassword!"));
        request.put("acceptTerms", true);
        request.put("acceptPrivacy", true);
        request.put("acceptMarketing", false);
        return request;
    }

    //--------------------------------------------------------------

    private static HttpResponse<String> post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
