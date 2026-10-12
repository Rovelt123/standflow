package app.controllers;

import app.daos.FileDAO;
import app.dtos.UserDTO;
import app.entities.User;
import app.exceptions.ApiException;
import app.security.AccessService;
import app.security.SecurityService;
import app.server.Setup;
import app.services.FileService;
import app.services.FileValidator;
import app.services.LocalFileStorage;
import app.support.MessageTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static app.services.FileValidatorTest.*;
import static io.javalin.apibuilder.ApiBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

class FileControllerTest extends MessageTestSupport {
    @TempDir Path temporary;
    private Javalin server;
    private HttpClient client;
    private jakarta.persistence.EntityManager previousEm;
    private static final String TEST_KEY = "file-upload-test-signing-key-32-bytes-minimum";
    private static final String BOUNDARY = "file-upload-test-boundary";

    record PartData(String name, String filename, byte[] bytes, String mime) {}

    //--------------------------------------------------------------

    @BeforeEach
    void startServer() throws Exception {
        previousEm = Setup.em;
        Setup.em = em;
        AccessService access = new AccessService();
        // Keep production route authorization, with an isolated JWT/database fixture.
        // Existing JWTTokenGenerator holds a static DAO, which cannot follow per-test databases.
        var field = AccessService.class.getDeclaredField("securityController");
        field.setAccessible(true);
        field.set(access, new SecurityService() {
            @Override public io.javalin.http.Handler authenticate() {
                return ctx -> {
                    try {
                        String header = ctx.header("Authorization");
                        if (header == null || !header.startsWith("Bearer ")) throw new UnauthorizedResponse("Login required");
                        SignedJWT jwt = SignedJWT.parse(header.substring(7));
                        if (!jwt.verify(new MACVerifier(TEST_KEY)) || !jwt.getJWTClaimsSet().getExpirationTime().after(new Date())) {
                            throw new UnauthorizedResponse("Invalid JWT");
                        }
                        UUID id = UUID.fromString(jwt.getJWTClaimsSet().getSubject());
                        User user = dao.getUser(id);
                        if (user == null) throw new UnauthorizedResponse("Invalid JWT user");
                        ctx.attribute("user", UserDTO.builder().id(id).build());
                    } catch (UnauthorizedResponse exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new UnauthorizedResponse("Invalid JWT");
                    }
                };
            }
        });
        FileService files = new FileService(new FileDAO(em), new LocalFileStorage(temporary), new FileValidator(), Clock.systemUTC());
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            FileController.configureMultipart(config);
            config.router.apiBuilder(() -> path("/api", () -> {
                FileController.registerRoutes(files).addEndpoints();
                get("/unrelated", ctx -> ctx.result("OK"), app.enums.Role.ADMIN);
            }));
        }).beforeMatched(access::accessHandler)
                .exception(ApiException.class, (error, ctx) -> ctx.status(error.getStatus()).result(error.getMessage()))
                .start(0);
        client = HttpClient.newHttpClient();
    }

    //--------------------------------------------------------------

    @AfterEach
    void stopServer() {
        if (client != null) client.close();
        if (server != null) server.stop();
        Setup.em = previousEm;
    }

    //--------------------------------------------------------------

    private String token(User user) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(user.getId().toString()).expirationTime(new Date(System.currentTimeMillis() + 60_000)).build());
        jwt.sign(new MACSigner(TEST_KEY));
        return jwt.serialize();
    }

    //--------------------------------------------------------------

    private byte[] multipart(List<PartData> parts) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (PartData part : parts) {
            body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + part.name()
                    + "\"; filename=\"" + part.filename() + "\"\r\nContent-Type: " + part.mime() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(part.bytes());
            body.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return body.toByteArray();
    }

    //--------------------------------------------------------------

    private HttpResponse<String> post(byte[] bytes, String contentType, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/files"))
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    //--------------------------------------------------------------

    private HttpResponse<String> upload(User user, List<PartData> parts) throws Exception {
        return post(multipart(parts), "multipart/form-data; boundary=" + BOUNDARY, user == null ? null : token(user));
    }

    //--------------------------------------------------------------

    @Test
    void acceptsOneAndThreeAndNeverExposesInternalMetadataOrTrustsMime() throws Exception {
        byte[] png = image("png");
        for (User uploader : List.of(alice, admin)) {
            var parts = List.of(new PartData("files", "a.png", png, "text/plain"),
                    new PartData("files", "b.pdf", pdf(0), "image/jpeg"), new PartData("files", "c.jpg", image("jpg"), "application/pdf"));
            for (int count : List.of(1, 3)) {
                var response = upload(uploader, parts.subList(0, count));
                assertEquals(201, response.statusCode(), response.body());
                var result = json.readTree(response.body()).get("files");
                assertEquals(count, result.size());
                assertEquals("image/png", result.get(0).get("contentType").asText());
                assertEquals(5, result.get(0).size());
                assertFalse(response.body().contains("storageKey"));
                assertFalse(response.body().contains(temporary.toString()));
                UUID id = UUID.fromString(result.get(0).get("id").asText());
                assertEquals(uploader.getId(), new FileDAO(em).getById(id).getUploadedBy().getId());
            }
        }
    }

    //--------------------------------------------------------------

    @Test
    void missingInvalidExpiredJwtAndOwnerAreRejectedAndUnrelatedPolicyIsUnchanged() throws Exception {
        var parts = List.of(new PartData("files", "a.png", image("png"), "image/png"));
        assertEquals(401, upload(null, parts).statusCode());
        assertEquals(401, post(multipart(parts), "multipart/form-data; boundary=" + BOUNDARY, "invalid").statusCode());
        SignedJWT expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(alice.getId().toString()).expirationTime(new Date(0)).build());
        expired.sign(new MACSigner(TEST_KEY));
        assertEquals(401, post(multipart(parts), "multipart/form-data; boundary=" + BOUNDARY, expired.serialize()).statusCode());
        assertEquals(403, upload(owner, parts).statusCode());
        for (User user : List.of(alice, owner)) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/unrelated"))
                    .header("Authorization", "Bearer " + token(user)).GET().build();
            assertEquals(user == owner ? 200 : 401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    //--------------------------------------------------------------

    @Test
    void rejectsMissingEmptyFourUnknownFieldAndInvalidMultipart() throws Exception {
        byte[] png = image("png");
        assertEquals(400, upload(alice, List.of()).statusCode());
        assertEquals(400, upload(alice, List.of(new PartData("files", "empty.png", new byte[0], "image/png"))).statusCode());
        var four = java.util.stream.IntStream.range(0, 4).mapToObj(i -> new PartData("files", i + ".png", png, "image/png")).toList();
        assertEquals(400, upload(alice, four).statusCode());
        assertEquals(400, upload(alice, List.of(new PartData("userId", "x.png", png, "image/png"))).statusCode());
        assertEquals(400, post(new byte[]{ 1 }, "application/json", token(alice)).statusCode());
        assertEquals(400, post(new byte[]{ 1 }, "multipart/form-data", token(alice)).statusCode());
        assertTrue(new FileDAO(em).getAll().isEmpty());
    }

    //--------------------------------------------------------------

    @Test
    void rejectsOversizeUnsupportedAndMixedBatchWithNoFiles() throws Exception {
        byte[] png = image("png");
        assertEquals(413, upload(alice, List.of(new PartData("files", "large.pdf", new byte[FileValidator.MAX_SIZE + 1], "application/pdf"))).statusCode());
        assertEquals(415, upload(alice, List.of(new PartData("files", "a.txt", png, "image/png"))).statusCode());
        var mixed = List.of(new PartData("files", "a.png", png, "image/png"), new PartData("files", "b.png", png, "image/png"),
                new PartData("files", "c.jpg", png, "image/jpeg"));
        assertEquals(415, upload(alice, mixed).statusCode());
        assertTrue(new FileDAO(em).getAll().isEmpty());
        try (var entries = Files.list(temporary)) { assertEquals(0, entries.count()); }
    }

    //--------------------------------------------------------------

    @Test
    void acceptsThreeExactLimitFilesAndEnforcesTotalRequestLimit() throws Exception {
        byte[] exact = pdf(FileValidator.MAX_SIZE);
        var parts = java.util.stream.IntStream.range(0, 3).mapToObj(i -> new PartData("files", i + ".pdf", exact, "application/pdf")).toList();
        var response = upload(alice, parts);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(3, json.readTree(response.body()).get("files").size());
        assertEquals(413, post(new byte[(int) FileController.MAX_REQUEST_SIZE + 1],
                "multipart/form-data; boundary=" + BOUNDARY, token(alice)).statusCode());
    }

    //--------------------------------------------------------------

    @Test
    void unexpectedStorageAndDatabaseFailuresAreSafeServerErrors() throws Exception {
        byte[] body = multipart(List.of(new PartData("files", "a.png", image("png"), "image/png")));
        String jwt = token(alice);
        LocalFileStorage failing = new LocalFileStorage(temporary.resolve("failing")) {
            @Override public String store(byte[] bytes, String extension) throws java.io.IOException {
                throw new java.io.IOException("Internal path: " + temporary);
            }
        };
        FileService broken = new FileService(new FileDAO(em), failing, new FileValidator(), Clock.systemUTC());
        FileDAO unavailable = new FileDAO(em) {
            @Override public User getUser(UUID id) { throw new IllegalStateException("Internal database details"); }
        };
        FileService databaseFailure = new FileService(unavailable, failing, new FileValidator(), Clock.systemUTC());
        server.unsafeConfig().router.apiBuilder(() -> {
            path("/api/storage", FileController.registerRoutes(broken));
            path("/api/database", FileController.registerRoutes(databaseFailure));
        });
        for (String path : List.of("storage", "database")) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/" + path + "/files"))
                    .header("Authorization", "Bearer " + jwt).header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, response.statusCode());
            assertFalse(response.body().contains(temporary.toString()));
            assertFalse(response.body().contains("Internal"));
        }
    }
}
