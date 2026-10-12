package app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {
    @TempDir Path temporary;

    //--------------------------------------------------------------

    @Test
    void createsDirectoryStoresUniqueFilesAndDeletesOnlyRequestedFile() throws Exception {
        Path directory = temporary.resolve("new-directory");
        LocalFileStorage storage = new LocalFileStorage(directory);
        assertTrue(Files.isDirectory(directory));
        String first = storage.store(new byte[]{ 1, 2 }, "pdf");
        String second = storage.store(new byte[]{ 3 }, "pdf");
        assertNotEquals(first, second);
        UUID.fromString(first.substring(0, 36));
        assertArrayEquals(new byte[]{ 1, 2 }, Files.readAllBytes(directory.resolve(first)));
        assertTrue(storage.exists(first));
        storage.delete(first);
        assertFalse(storage.exists(first));
        assertTrue(storage.exists(second));
    }

    //--------------------------------------------------------------

    @Test
    void neverOverwritesAndRejectsTraversal() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporary);
        String key = storage.store(new byte[]{ 1 }, "png");
        assertThrows(IOException.class, () -> storage.write(key, new byte[]{ 2 }));
        assertArrayEquals(new byte[]{ 1 }, Files.readAllBytes(temporary.resolve(key)));
        for (String path : java.util.List.of("../secret.pdf", "..\\secret.pdf", temporary.resolve("outside.pdf").toString())) {
            assertThrows(IllegalArgumentException.class, () -> storage.delete(path));
            assertThrows(IllegalArgumentException.class, () -> storage.write(path, new byte[]{ 1 }));
        }
    }

    //--------------------------------------------------------------

    @Test
    void failsClearlyForInvalidDirectoryAndWriteFailure() throws Exception {
        Path file = temporary.resolve("not-a-directory");
        Files.write(file, new byte[]{ 1 });
        assertThrows(IllegalStateException.class, () -> new LocalFileStorage(file));
        assertThrows(IllegalArgumentException.class, () -> new LocalFileStorage(Path.of("relative")));
        LocalFileStorage storage = new LocalFileStorage(temporary.resolve("removed"));
        Files.delete(temporary.resolve("removed"));
        assertThrows(IOException.class, () -> storage.store(new byte[]{ 1 }, "pdf"));
    }

    //--------------------------------------------------------------

    @Test
    void opensCorrectContentReadOnlyAndClosesItsChannel() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporary);
        byte[] expected = { 1, 2, 3, 4 };
        String key = storage.store(expected, "pdf");
        var modifiedAt = Files.getLastModifiedTime(temporary.resolve(key));
        var opened = storage.open(key);
        try (opened) {
            assertEquals(expected.length, opened.size());
            assertArrayEquals(expected, opened.content().readAllBytes());
        }
        assertThrows(IOException.class, () -> opened.content().read());
        assertArrayEquals(expected, Files.readAllBytes(temporary.resolve(key)));
        assertEquals(modifiedAt, Files.getLastModifiedTime(temporary.resolve(key)));
        assertTrue(storage.exists(key));
    }

    //--------------------------------------------------------------

    @Test
    void readRejectsMissingNonregularAndTraversalPaths() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporary);
        assertThrows(java.nio.file.NoSuchFileException.class, () -> storage.open(UUID.randomUUID() + ".pdf"));
        String directoryKey = UUID.randomUUID() + ".pdf";
        Files.createDirectory(temporary.resolve(directoryKey));
        assertThrows(java.nio.file.NoSuchFileException.class, () -> storage.open(directoryKey));
        for (String key : java.util.List.of("../outside.pdf", "..\\outside.pdf", temporary.resolve("outside.pdf").toString())) {
            assertThrows(IllegalArgumentException.class, () -> storage.open(key));
        }
    }

    //--------------------------------------------------------------

    @Test
    void readDoesNotFollowSymbolicLinks() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temporary.resolve("uploads"));
        Path outside = temporary.resolve("outside.pdf");
        Files.write(outside, new byte[]{ 42 });
        String key = UUID.randomUUID() + ".pdf";
        try {
            Files.createSymbolicLink(temporary.resolve("uploads").resolve(key), outside);
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Platform does not permit creation of symbolic links.");
        }
        assertThrows(java.nio.file.NoSuchFileException.class, () -> storage.open(key));
        assertArrayEquals(new byte[]{ 42 }, Files.readAllBytes(outside));
    }
}
