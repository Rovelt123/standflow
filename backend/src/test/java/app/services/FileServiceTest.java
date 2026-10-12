package app.services;

import app.daos.FileDAO;
import app.entities.UploadedFile;
import app.exceptions.ApiException;
import app.support.MessageTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static app.services.FileValidatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest extends MessageTestSupport {
    @TempDir Path temporary;
    private final Instant now = Instant.parse("2026-10-06T12:00:00Z");

    //--------------------------------------------------------------

    private FileService files(FileDAO dao, LocalFileStorage storage) {
        return new FileService(dao, storage, new FileValidator(), Clock.fixed(now, ZoneOffset.UTC));
    }

    //--------------------------------------------------------------

    @Test
    void uploadsOneAndThreeWithCorrectMetadataAndUploader() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        byte[] png = image("png");
        var one = files(dao, storage).upload(alice.getId(), List.of(upload("one.png", png)));
        assertEquals(1, one.size());
        var three = files(dao, storage).upload(admin.getId(), List.of(upload("a.png", png), upload("b.png", png), upload("c.pdf", pdf(0))));
        assertEquals(3, three.size());
        assertEquals("a.png", three.getFirst().originalName());
        assertEquals("image/png", three.getFirst().contentType());
        assertEquals(png.length, three.getFirst().size());
        assertEquals(now.toString(), three.getFirst().uploadedAt());
        for (var dto : three) {
            UploadedFile file = dao.getById(dto.id());
            assertEquals(admin.getId(), file.getUploadedBy().getId());
            assertNull(file.getMessage());
            assertTrue(storage.exists(file.getStorageKey()));
        }
        assertEquals(4, dao.getAll().size());
    }

    //--------------------------------------------------------------

    @Test
    void invalidMixedBatchHasNoPhysicalFilesOrMetadata() throws Exception {
        FileDAO dao = new FileDAO(em);
        var service = files(dao, new LocalFileStorage(temporary));
        byte[] png = image("png");
        assertEquals(415, assertThrows(ApiException.class, () -> service.upload(alice.getId(),
                List.of(upload("a.png", png), upload("b.jpg", png)))).getStatus());
        assertTrue(dao.getAll().isEmpty());
        try (var entries = Files.list(temporary)) { assertEquals(0, entries.count()); }
        assertEquals(401, assertThrows(ApiException.class, () -> service.upload(null, List.of())).getStatus());
        assertEquals(403, assertThrows(ApiException.class, () -> service.upload(owner.getId(), List.of())).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void storageFailureOnSecondFileRollsBackFirstFile() throws Exception {
        LocalFileStorage failing = new LocalFileStorage(temporary) {
            private int calls;
            @Override public String store(byte[] content, String extension) throws IOException {
                if (++calls == 2) throw new IOException("Simulated disk failure");
                return super.store(content, extension);
            }
        };
        FileDAO dao = new FileDAO(em);
        byte[] png = image("png");
        assertEquals(500, assertThrows(ApiException.class, () -> files(dao, failing).upload(alice.getId(),
                List.of(upload("a.png", png), upload("b.png", png)))).getStatus());
        assertTrue(dao.getAll().isEmpty());
        try (var entries = Files.list(temporary)) { assertEquals(0, entries.count()); }
    }

    //--------------------------------------------------------------

    @Test
    void databaseFailureRemovesEntirePhysicalBatch() throws Exception {
        FileDAO rollback = new FileDAO(em) {
            @Override public List<UploadedFile> createAll(List<UploadedFile> files) {
                return inTransaction(() -> {
                    super.createAll(files);
                    throw new IllegalStateException("Simulated database failure");
                });
            }
        };
        byte[] png = image("png");
        var error = assertThrows(ApiException.class, () -> files(rollback, new LocalFileStorage(temporary)).upload(alice.getId(),
                List.of(upload("a.png", png), upload("b.png", png), upload("c.png", png))));
        assertEquals(500, error.getStatus());
        assertFalse(error.getMessage().contains("database"));
        assertTrue(new FileDAO(em).getAll().isEmpty());
        try (var entries = Files.list(temporary)) { assertEquals(0, entries.count()); }
    }

    //--------------------------------------------------------------

    @Test
    void attachmentValidationEnforcesOwnershipUniquenessLimitsAndExpiration() {
        List<String> ids = java.util.stream.IntStream.range(0, 4).mapToObj(i -> UUID.randomUUID().toString()).toList();
        assertEquals(400, assertThrows(ApiException.class, () -> FileService.attachmentIds(Map.of("attachmentIds", ids))).getStatus());
        for (Object invalid : List.of(List.of(ids.getFirst(), ids.getFirst()), List.of("invalid"), "not-array", List.of(42))) {
            assertEquals(400, assertThrows(ApiException.class, () -> FileService.attachmentIds(Map.of("attachmentIds", invalid))).getStatus());
        }
        UploadedFile file = UploadedFile.builder().uploadedBy(alice).uploadedAt(now).build();
        assertEquals(403, assertThrows(ApiException.class, () -> FileService.validateAttachments(List.of(file), bob.getId(), now)).getStatus());
        file.setUploadedAt(now.minus(FileService.PENDING_LIFETIME));
        assertEquals(400, assertThrows(ApiException.class, () -> FileService.validateAttachments(List.of(file), alice.getId(), now)).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void expiresOnlyPendingFilesAtTwentyFourHoursAndSchedulerCloses() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        var service = files(dao, storage);
        byte[] png = image("png");
        var created = service.upload(alice.getId(), List.of(upload("expired.png", png), upload("attached.png", png), upload("recent.png", png)));
        var message = message(alice, admin, "Subject", "Body", 0, false);
        dao.inTransaction(() -> {
            dao.getById(created.get(0).id()).setUploadedAt(now.minus(FileService.PENDING_LIFETIME));
            var attached = dao.getById(created.get(1).id());
            attached.setUploadedAt(now.minus(FileService.PENDING_LIFETIME));
            attached.setMessage(message);
            return null;
        });
        assertEquals(1, service.cleanupExpired());
        assertNull(dao.getById(created.getFirst().id()));
        assertTrue(storage.exists(dao.getById(created.get(1).id()).getStorageKey()));
        assertTrue(storage.exists(dao.getById(created.get(2).id()).getStorageKey()));
        service.startCleanup(emf);
        service.close();
        service.close();
        assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && t.getName().equals("pending-upload-cleanup")));
    }

    //--------------------------------------------------------------

    @Test
    void cleanupWaitsForAttachmentLockAndDoesNotDeleteAttachedFile() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        var created = files(dao, storage).upload(alice.getId(), List.of(upload("document.png", image("png")))).getFirst();
        UploadedFile file = dao.getById(created.id());
        String key = file.getStorageKey();
        var message = message(alice, admin, "Subject", "Body", 0, false);
        dao.inTransaction(() -> { file.setUploadedAt(now.minus(FileService.PENDING_LIFETIME)); return null; });
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch cleanupLockAttempt = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attach = executor.submit(() -> {
                try (var writer = emf.createEntityManager()) {
                    new FileDAO(writer).inTransaction(() -> {
                        var pending = new FileDAO(writer).lock(created.id());
                        locked.countDown();
                        try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException exception) { throw new IllegalStateException(exception); }
                        pending.setMessage(writer.find(app.entities.Message.class, message.getId()));
                        return null;
                    });
                }
            });
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            var cleanup = executor.submit(() -> {
                try (var reader = emf.createEntityManager()) {
                    FileDAO tracking = new FileDAO(reader) {
                        @Override public UploadedFile lock(UUID id) {
                            cleanupLockAttempt.countDown();
                            return super.lock(id);
                        }
                    };
                    return files(tracking, storage).cleanupExpired();
                }
            });
            try {
                assertTrue(cleanupLockAttempt.await(5, TimeUnit.SECONDS));
                assertTrue(storage.exists(key));
            } finally {
                release.countDown();
            }
            attach.get(5, TimeUnit.SECONDS);
            assertEquals(0, cleanup.get(5, TimeUnit.SECONDS));
        }
        assertTrue(storage.exists(key));
    }

    //--------------------------------------------------------------

    @Test
    void bothParticipantsCanDownloadEitherSendersAttachmentsWithoutChanges() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        var fileService = files(dao, storage);
        for (var sender : List.of(alice, admin)) {
            byte[] content = image("png");
            var uploaded = fileService.upload(sender.getId(), List.of(upload("original.png", content))).getFirst();
            var message = sender == alice
                    ? service.sendCustomer(alice.getId(), Map.of("subject", "Documents", "body", "Attached", "attachmentIds", List.of(uploaded.id().toString())))
                    : service.sendAdmin(admin.getId(), alice.getId(), Map.of("body", "Reply", "attachmentIds", List.of(uploaded.id().toString())));
            for (var requester : List.of(alice, admin)) {
                try (var download = fileService.download(requester.getId(), uploaded.id())) {
                    assertEquals("original.png", download.metadata().originalName());
                    assertEquals("image/png", download.metadata().contentType());
                    assertArrayEquals(content, download.content().readAllBytes());
                }
            }
            assertEquals(message.getId(), dao.findForDownload(uploaded.id()).getMessage().getId());
            assertEquals(now, dao.findForDownload(uploaded.id()).getUploadedAt());
            assertFalse(reload(message.getId()).isRead());
            assertTrue(storage.exists(dao.getById(uploaded.id()).getStorageKey()));
        }
        assertEquals(2, dao.getAll().size());
    }

    //--------------------------------------------------------------

    @Test
    void unavailableAndUnauthorizedRequestsNeverAccessStorage() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        var uploaded = files(dao, storage).upload(alice.getId(), List.of(upload("a.png", image("png")))).getFirst();
        FileService guarded = files(dao, new LocalFileStorage(temporary) {
            @Override public StoredFile open(String key) { fail("Unauthorized download accessed storage"); return null; }
        });
        assertEquals(401, assertThrows(ApiException.class, () -> guarded.download(null, uploaded.id())).getStatus());
        assertEquals(400, assertThrows(ApiException.class, () -> guarded.download(alice.getId(), null)).getStatus());
        assertEquals(404, assertThrows(ApiException.class, () -> guarded.download(alice.getId(), UUID.randomUUID())).getStatus());
        assertEquals(404, assertThrows(ApiException.class, () -> guarded.download(alice.getId(), uploaded.id())).getStatus());
        assertEquals(404, assertThrows(ApiException.class, () -> guarded.download(owner.getId(), uploaded.id())).getStatus());
        service.sendCustomer(alice.getId(), Map.of("subject", "Documents", "body", "Attached", "attachmentIds", List.of(uploaded.id().toString())));
        assertEquals(404, assertThrows(ApiException.class, () -> guarded.download(bob.getId(), uploaded.id())).getStatus());
        // Ownership alone cannot authorize an attachment associated with another conversation.
        var bobMessage = message(bob, admin, "Other", "Body", 1, false);
        dao.inTransaction(() -> { dao.getById(uploaded.id()).setMessage(bobMessage); return null; });
        assertEquals(404, assertThrows(ApiException.class, () -> guarded.download(alice.getId(), uploaded.id())).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void adminCannotDownloadConversationOutsideCurrentChatPolicy() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        var uploaded = files(dao, storage).upload(alice.getId(), List.of(upload("a.png", image("png")))).getFirst();
        var unrelated = message(alice, bob, "Unrelated", "Body", 0, false);
        dao.inTransaction(() -> { dao.getById(uploaded.id()).setMessage(unrelated); return null; });
        assertEquals(404, assertThrows(ApiException.class, () -> files(dao, storage).download(admin.getId(), uploaded.id())).getStatus());
        var current = message(alice, admin, "Current", "Body", 1, false);
        dao.inTransaction(() -> { dao.getById(uploaded.id()).setMessage(current); return null; });
        user("Other admin", "Other", app.enums.Role.ADMIN);
        assertEquals(500, assertThrows(ApiException.class, () -> files(dao, storage).download(admin.getId(), uploaded.id())).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void missingUnreadableAndInconsistentContentHaveSafeErrorsAndCloseStreams() throws Exception {
        FileDAO dao = new FileDAO(em);
        LocalFileStorage storage = new LocalFileStorage(temporary);
        var uploaded = files(dao, storage).upload(alice.getId(), List.of(upload("a.png", image("png")))).getFirst();
        service.sendCustomer(alice.getId(), Map.of("subject", "Documents", "body", "Attached", "attachmentIds", List.of(uploaded.id().toString())));
        LocalFileStorage unreadable = new LocalFileStorage(temporary) {
            @Override public StoredFile open(String key) throws IOException {
                throw new java.nio.file.AccessDeniedException("Internal path: " + temporary);
            }
        };
        var error = assertThrows(ApiException.class, () -> files(dao, unreadable).download(alice.getId(), uploaded.id()));
        assertEquals(500, error.getStatus());
        assertFalse(error.getMessage().contains(temporary.toString()));
        boolean[] closed = { false };
        LocalFileStorage inconsistent = new LocalFileStorage(temporary) {
            @Override public StoredFile open(String key) {
                return new StoredFile(new java.io.ByteArrayInputStream(new byte[]{ 1 }) {
                    @Override public void close() { closed[0] = true; }
                }, 1);
            }
        };
        assertEquals(500, assertThrows(ApiException.class, () -> files(dao, inconsistent).download(alice.getId(), uploaded.id())).getStatus());
        assertTrue(closed[0]);
        storage.delete(dao.getById(uploaded.id()).getStorageKey());
        assertEquals(404, assertThrows(ApiException.class, () -> files(dao, storage).download(alice.getId(), uploaded.id())).getStatus());
        assertNotNull(dao.getById(uploaded.id()));
    }
}
