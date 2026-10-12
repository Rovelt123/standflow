package app.daos;

import app.entities.UploadedFile;
import app.support.MessageTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileDAOTest extends MessageTestSupport {
    //--------------------------------------------------------------

    private UploadedFile pending(String key) {
        return UploadedFile.builder().uploadedBy(alice).originalName("document.pdf").storageKey(key)
                .contentType("application/pdf").size(12).uploadedAt(Instant.now()).build();
    }

    //--------------------------------------------------------------

    @Test
    void persistsPendingFilesAndAssociatesMultipleFilesWithMessage() {
        FileDAO files = new FileDAO(em);
        var created = files.createAll(List.of(pending(UUID.randomUUID() + ".pdf"), pending(UUID.randomUUID() + ".pdf")));
        assertNotNull(created.getFirst().getId());
        assertNull(files.getById(created.getFirst().getId()).getMessage());
        var message = message(alice, admin, "Documents", "Attached", 0, false);
        files.inTransaction(() -> {
            created.forEach(file -> file.setMessage(message));
            return null;
        });
        try (var reader = emf.createEntityManager()) {
            FileDAO persisted = new FileDAO(reader);
            assertEquals(2, persisted.getForMessage(message.getId()).size());
            assertEquals(alice.getId(), persisted.getById(created.getFirst().getId()).getUploadedBy().getId());
        }
    }

    //--------------------------------------------------------------

    @Test
    void duplicateKeysRollBackWholeBatch() {
        FileDAO files = new FileDAO(em);
        String key = UUID.randomUUID() + ".pdf";
        assertThrows(RuntimeException.class, () -> files.createAll(List.of(pending(key), pending(key))));
        assertTrue(files.getAll().isEmpty());
    }

    //--------------------------------------------------------------

    @Test
    void requiredFieldsCannotBeNull() {
        for (String field : List.of("uploadedBy", "originalName", "storageKey", "contentType")) {
            UploadedFile invalid = pending(UUID.randomUUID() + ".pdf");
            switch (field) {
                case "uploadedBy" -> invalid.setUploadedBy(null);
                case "originalName" -> invalid.setOriginalName(null);
                case "storageKey" -> invalid.setStorageKey(null);
                default -> invalid.setContentType(null);
            }
            assertThrows(RuntimeException.class, () -> new FileDAO(em).create(invalid), field);
        }
        assertTrue(new FileDAO(em).getAll().isEmpty());
    }
}
