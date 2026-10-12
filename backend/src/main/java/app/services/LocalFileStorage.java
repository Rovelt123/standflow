package app.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class LocalFileStorage {
    private final Path directory;

    public record StoredFile(InputStream content, long size) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    //--------------------------------------------------------------

    public static LocalFileStorage fromEnvironment() {
        String configured = System.getenv("UPLOAD_DIR");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("UPLOAD_DIR must specify an absolute, writable storage directory.");
        }
        try {
            return new LocalFileStorage(Path.of(configured));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("UPLOAD_DIR is invalid or cannot be accessed.", exception);
        }
    }

    //--------------------------------------------------------------

    public LocalFileStorage(Path directory) {
        if (!directory.isAbsolute()) throw new IllegalArgumentException("UPLOAD_DIR must be absolute.");
        try {
            Files.createDirectories(directory);
            this.directory = directory.toRealPath();
            if (!Files.isDirectory(this.directory) || !Files.isWritable(this.directory)) {
                throw new IOException("Storage directory is not writable.");
            }
            Path probe = Files.createTempFile(this.directory, ".storage-check-", ".tmp");
            Files.delete(probe);
        } catch (IOException exception) {
            throw new IllegalStateException("UPLOAD_DIR must be an accessible, writable directory.", exception);
        }
    }

    //--------------------------------------------------------------

    public String store(byte[] content, String extension) throws IOException {
        String key = UUID.randomUUID() + "." + extension;
        write(key, content);
        return key;
    }

    //--------------------------------------------------------------

    public void write(String key, byte[] content) throws IOException {
        Path target = resolve(key);
        // CREATE_NEW rejects both collisions and pre-existing symbolic links.
        var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (var stream = output) {
            stream.write(content);
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    //--------------------------------------------------------------

    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    //--------------------------------------------------------------

    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key), LinkOption.NOFOLLOW_LINKS);
    }

    //--------------------------------------------------------------

    public StoredFile open(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
            throw new NoSuchFileException("Stored content is unavailable.");
        }
        var channel = Files.newByteChannel(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try {
            return new StoredFile(Channels.newInputStream(channel), channel.size());
        } catch (IOException | RuntimeException exception) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    //--------------------------------------------------------------

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(pdf|png|jpg|jpeg)")) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        Path target = directory.resolve(key).normalize();
        if (!target.getParent().equals(directory)) throw new IllegalArgumentException("Invalid storage key.");
        return target;
    }
}
