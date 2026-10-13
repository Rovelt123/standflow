package app.services;

import app.exceptions.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileValidatorTest {
    private final FileValidator validator = new FileValidator();

    //--------------------------------------------------------------

    public static byte[] image(String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    //--------------------------------------------------------------

    public static byte[] pdf(int targetSize) throws Exception {
        int padding = Math.max(0, targetSize - 600);
        for (int attempt = 0; attempt < 8; attempt++) {
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);
                PDStream stream = new PDStream(document);
                try (var content = stream.createOutputStream()) {
                    content.write(" ".repeat(padding).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                page.setContents(stream);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                document.save(output, CompressParameters.NO_COMPRESSION);
                byte[] bytes = output.toByteArray();
                if (targetSize == 0 || bytes.length == targetSize) return bytes;
                padding += targetSize - bytes.length;
            }
        }
        throw new AssertionError("Could not construct exact-sized PDF.");
    }

    //--------------------------------------------------------------

    public static FileValidator.Upload upload(String name, byte[] bytes) {
        return new FileValidator.Upload(name, new ByteArrayInputStream(bytes), bytes.length);
    }

    //--------------------------------------------------------------

    @Test
    void acceptsAllFormatsAndOneToThreeFiles() throws Exception {
        var pdf = upload("document.PDF", pdf(0));
        assertEquals("application/pdf", validator.validate(List.of(pdf)).getFirst().contentType());
        for (String format : List.of("png", "jpg", "jpeg")) {
            byte[] bytes = image(format.equals("png") ? "png" : "jpg");
            for (int count = 1; count <= 3; count++) {
                var files = java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> upload("image." + format, bytes)).toList();
                assertEquals(count, validator.validate(files).size());
            }
        }
    }

    //--------------------------------------------------------------

    @Test
    void acceptsExactLimitAndRejectsOversizeBeforeParsing() throws Exception {
        byte[] exact = pdf(FileValidator.MAX_SIZE);
        assertEquals(FileValidator.MAX_SIZE, validator.validate(List.of(upload("exact.pdf", exact))).getFirst().content().length);
        assertStatus(413, List.of(upload("large.pdf", Arrays.copyOf(exact, exact.length + 1))));
        InputStream unreadable = new InputStream() {
            @Override public int read() { fail("Oversized declared file must not be read or parsed"); return -1; }
        };
        assertStatus(413, List.of(new FileValidator.Upload("large.pdf", unreadable, FileValidator.MAX_SIZE + 1L)));
    }

    //--------------------------------------------------------------

    @Test
    void limitsReadsEvenWhenReportedSizeIsFalse() {
        int[] read = { 0 };
        InputStream endless = new InputStream() {
            @Override public int read() { read[0]++; return 1; }
        };
        assertStatus(413, List.of(new FileValidator.Upload("large.pdf", endless, 1)));
        assertEquals(FileValidator.MAX_SIZE + 1, read[0]);
    }

    //--------------------------------------------------------------

    @Test
    void rejectsEmptyUnsupportedMismatchedMalformedAndUnsafeNames() throws Exception {
        assertStatus(400, List.of());
        assertStatus(400, List.of(upload("empty.pdf", new byte[0])));
        assertStatus(415, List.of(upload("text.txt", new byte[]{ 1 })));
        assertStatus(415, List.of(upload("fake.pdf", image("png"))));
        assertStatus(415, List.of(upload("fake.png", image("jpg"))));
        assertStatus(415, List.of(upload("fake.pdf", "%PDF-1.7\nmalformed".getBytes())));
        for (String format : List.of("png", "jpg")) {
            byte[] bytes = image(format);
            assertStatus(415, List.of(upload("broken." + format, Arrays.copyOf(bytes, bytes.length - 8))));
        }
        byte[] corrupted = image("png");
        corrupted[29] ^= 1; // Damage IHDR CRC without changing image dimensions.
        assertStatus(415, List.of(upload("corrupt.png", corrupted)));
        for (String name : List.of("../x.pdf", "..\\x.pdf", "x\u0000.pdf")) {
            assertStatus(400, List.of(upload(name, pdf(0))));
        }
    }

    //--------------------------------------------------------------

    @Test
    void rejectsFourFilesAndMixedBatch() throws Exception {
        byte[] png = image("png");
        assertStatus(400, java.util.stream.IntStream.range(0, 4).mapToObj(i -> upload("a.png", png)).toList());
        assertStatus(415, List.of(upload("a.png", png), upload("b.png", png), upload("c.jpg", png)));
        // Input model has no client MIME field: type is derived exclusively from content.
        assertEquals("image/png", validator.validate(List.of(upload("a.png", png))).getFirst().contentType());
    }

    //--------------------------------------------------------------

    private void assertStatus(int status, List<FileValidator.Upload> files) {
        assertEquals(status, assertThrows(ApiException.class, () -> validator.validate(files)).getStatus());
    }
}
