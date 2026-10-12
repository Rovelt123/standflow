package app.services;

import app.daos.FileDAO;
import app.dtos.FileDTO;
import app.entities.UploadedFile;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileService implements AutoCloseable {
    public static final Duration PENDING_LIFETIME = Duration.ofHours(24);
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private final FileDAO dao;
    private final LocalFileStorage storage;
    private final FileValidator validator;
    private final Clock clock;
    private ScheduledExecutorService cleanup;

    public record Download(FileDTO metadata, InputStream content) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    //--------------------------------------------------------------

    public FileService(FileDAO dao, LocalFileStorage storage, FileValidator validator, Clock clock) {
        this.dao = Objects.requireNonNull(dao);
        this.storage = Objects.requireNonNull(storage);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
    }

    //--------------------------------------------------------------

    public Download download(UUID requesterId, UUID fileId) {
        if (requesterId == null) throw new ApiException(401, "Login er påkrævet.");
        if (fileId == null) throw new ApiException(400, "Filens id er ugyldigt.");
        try {
            User requester = dao.getUser(requesterId);
            if (requester == null || requester.getRoles().stream().noneMatch(role -> role == Role.USER || role == Role.ADMIN)) {
                throw unavailable();
            }
            UploadedFile file = dao.findForDownload(fileId);
            if (file == null || file.getMessage() == null) throw unavailable();
            authorizeDownload(requester, file);
            FileDTO metadata = FileDTO.from(file);
            LocalFileStorage.StoredFile stored = storage.open(file.getStorageKey());
            try {
                if (stored.size() != file.getSize()) throw new IOException("Stored file length differs from its metadata.");
                return new Download(metadata, stored.content());
            } catch (IOException | RuntimeException exception) {
                try {
                    stored.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
                throw exception;
            }
        } catch (NoSuchFileException exception) {
            throw unavailable();
        } catch (ApiException exception) {
            if (exception.getStatus() < 500) throw exception;
            LOGGER.error("Download authorization or metadata retrieval failed.", exception);
            throw new ApiException(500, "Filen kunne ikke downloades. Prøv igen senere.");
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("File download failed.", exception);
            throw new ApiException(500, "Filen kunne ikke downloades. Prøv igen senere.");
        }
    }

    //--------------------------------------------------------------

    private void authorizeDownload(User requester, UploadedFile file) {
        List<User> admins = dao.getAdmins();
        if (admins.size() != 1) throw new ApiException(500, "Chatten kræver præcis én admin.");
        UUID adminId = admins.getFirst().getId();
        User sender = file.getMessage().getSender();
        User recipient = file.getMessage().getRecipient();
        User customer;
        if (sender.getId().equals(adminId)) customer = recipient;
        else if (recipient.getId().equals(adminId)) customer = sender;
        else throw unavailable();
        if (!customer.getRoles().contains(Role.USER) || customer.getRoles().contains(Role.ADMIN)
                || customer.getRoles().contains(Role.OWNER)) throw unavailable();
        if (!requester.getId().equals(adminId) && !requester.getId().equals(customer.getId())) throw unavailable();
    }

    //--------------------------------------------------------------

    private ApiException unavailable() {
        return new ApiException(404, "Filen findes ikke eller er ikke tilgængelig.");
    }

    //--------------------------------------------------------------

    public List<FileDTO> upload(UUID uploaderId, List<FileValidator.Upload> uploads) {
        if (uploaderId == null) throw new ApiException(401, "Login er påkrævet.");
        User uploader = dao.getUser(uploaderId);
        if (uploader == null || uploader.getRoles().stream().noneMatch(role -> role == Role.USER || role == Role.ADMIN)) {
            throw new ApiException(403, "Kun kunder og administratorer kan uploade filer.");
        }
        List<FileValidator.ValidatedFile> validated = validator.validate(uploads);
        List<UploadedFile> files = new ArrayList<>();
        try {
            for (var file : validated) {
                String key = storage.store(file.content(), file.extension());
                files.add(UploadedFile.builder().uploadedBy(uploader).originalName(file.originalName())
                        .storageKey(key).contentType(file.contentType()).size(file.content().length)
                        .uploadedAt(clock.instant()).build());
            }
            return dao.createAll(files).stream().map(FileDTO::from).toList();
        } catch (IOException | RuntimeException exception) {
            for (var file : files) {
                try {
                    storage.delete(file.getStorageKey());
                } catch (IOException | RuntimeException cleanupFailure) {
                    LOGGER.error("Failed to remove a file after upload rollback.", cleanupFailure);
                }
            }
            LOGGER.error("File upload failed; new files rolled back.", exception);
            throw new ApiException(500, "Filerne kunne ikke gemmes. Prøv igen senere.");
        }
    }

    //--------------------------------------------------------------

    public static List<UUID> attachmentIds(Map<String, ?> request) {
        if (!request.containsKey("attachmentIds")) return List.of();
        if (!(request.get("attachmentIds") instanceof List<?> values) || values.size() > FileValidator.MAX_FILES) {
            throw new ApiException(400, "attachmentIds skal være en liste med højst 3 unikke id'er.");
        }
        List<UUID> ids = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String text) || !text.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                throw new ApiException(400, "Vedhæftningens id er ugyldigt.");
            }
            ids.add(UUID.fromString(text));
        }
        if (new HashSet<>(ids).size() != ids.size()) throw new ApiException(400, "Vedhæftningens id er angivet flere gange.");
        return ids;
    }

    //--------------------------------------------------------------

    public static void validateAttachments(List<UploadedFile> files, UUID senderId, Instant now) {
        for (UploadedFile file : files) {
            if (file == null) throw new ApiException(404, "Vedhæftningen findes ikke.");
            if (!file.getUploadedBy().getId().equals(senderId)) throw new ApiException(403, "Vedhæftningen tilhører en anden bruger.");
            if (file.getMessage() != null) throw new ApiException(400, "Vedhæftningen er allerede sendt.");
            if (!file.getUploadedAt().isAfter(now.minus(PENDING_LIFETIME))) {
                throw new ApiException(400, "Vedhæftningen er udløbet. Vælg filen igen.");
            }
        }
    }

    //--------------------------------------------------------------

    public int cleanupExpired() {
        Instant cutoff = clock.instant().minus(PENDING_LIFETIME);
        int removed = 0;
        for (UUID id : dao.getExpiredIds(cutoff)) {
            boolean deleted = dao.inTransaction(() -> {
                UploadedFile file = dao.lock(id);
                // Re-check after taking the same row lock used by message association.
                if (file == null || file.getMessage() != null || file.getUploadedAt().isAfter(cutoff)) return false;
                try {
                    storage.delete(file.getStorageKey());
                } catch (IOException exception) {
                    throw new IllegalStateException("Pending upload cleanup failed.", exception);
                }
                dao.delete(file);
                return true;
            });
            if (deleted) removed++;
        }
        return removed;
    }

    //--------------------------------------------------------------

    public synchronized void startCleanup(EntityManagerFactory factory) {
        if (cleanup != null) return;
        Runnable job = () -> {
            try (var em = factory.createEntityManager()) {
                FileService worker = new FileService(new FileDAO(em), storage, validator, clock);
                while (!Thread.currentThread().isInterrupted() && worker.cleanupExpired() == 100) {
                    // Process bounded batches without retaining all expired uploads in memory.
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Pending upload cleanup failed; retained metadata will be retried.", exception);
            }
        };
        job.run();
        cleanup = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pending-upload-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanup.scheduleWithFixedDelay(job, 1, 1, TimeUnit.HOURS);
    }

    //--------------------------------------------------------------

    @Override
    public synchronized void close() {
        if (cleanup == null) return;
        cleanup.shutdown();
        try {
            if (!cleanup.awaitTermination(10, TimeUnit.SECONDS)) cleanup.shutdownNow();
        } catch (InterruptedException exception) {
            cleanup.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
