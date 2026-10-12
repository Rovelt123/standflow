package app.services;

import app.exceptions.ApiException;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class FileValidator {
    public static final int MAX_FILES = 3;
    public static final int MAX_SIZE = 1_048_576;
    private static final long MAX_IMAGE_PIXELS = 16_777_216;

    public record Upload(String originalName, InputStream content, long declaredSize) {}
    public record ValidatedFile(String originalName, String extension, String contentType, byte[] content) {}

    //--------------------------------------------------------------

    public List<ValidatedFile> validate(List<Upload> uploads) {
        if (uploads == null || uploads.isEmpty() || uploads.size() > MAX_FILES) {
            throw new ApiException(400, "Vælg mellem 1 og 3 filer.");
        }
        List<ValidatedFile> result = new ArrayList<>();
        for (Upload upload : uploads) result.add(validate(upload));
        return result;
    }

    //--------------------------------------------------------------

    private ValidatedFile validate(Upload upload) {
        String name = upload.originalName();
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(400, "Filnavnet er ugyldigt.");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (name.lastIndexOf('.') < 1 || !List.of("pdf", "png", "jpg", "jpeg").contains(extension)) {
            throw new ApiException(415, "Kun PDF, PNG, JPG og JPEG er tilladt.");
        }
        if (upload.declaredSize() > MAX_SIZE) throw tooLarge();
        byte[] bytes;
        try (InputStream input = upload.content()) {
            // Never trust the reported size; read at most one byte beyond the limit.
            bytes = input.readNBytes(MAX_SIZE + 1);
        } catch (IOException exception) {
            throw new ApiException(400, "Filen kunne ikke læses.");
        }
        if (bytes.length > MAX_SIZE) throw tooLarge();
        if (bytes.length == 0) throw new ApiException(400, "Tomme filer kan ikke uploades.");
        try {
            if (extension.equals("pdf")) validatePdf(bytes);
            else validateImage(bytes, extension);
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(415, "Filens indhold er ugyldigt eller matcher ikke filtypen.");
        }
        return new ValidatedFile(name, extension,
                extension.equals("pdf") ? "application/pdf" : extension.equals("png") ? "image/png" : "image/jpeg", bytes);
    }

    //--------------------------------------------------------------

    private void validatePdf(byte[] bytes) throws IOException {
        if (!new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII).startsWith("%PDF-")) {
            throw new IOException("Invalid PDF header.");
        }
        try (var source = new RandomAccessReadBuffer(bytes)) {
            PDFParser parser = new PDFParser(source);
            try (var document = parser.parse(false)) {
                if (document.getNumberOfPages() < 1) throw new IOException("PDF has no pages.");
                // Walk the page tree without rendering or extracting compressed page content.
                for (var page : document.getPages()) page.getMediaBox();
            }
        }
    }

    //--------------------------------------------------------------

    private void validateImage(byte[] bytes, String extension) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid image.");
            ImageReader reader = readers.next();
            try {
                String expected = extension.equals("png") ? "png" : "JPEG";
                if (!reader.getFormatName().equalsIgnoreCase(expected)) throw new IOException("Wrong image format.");
                if (extension.equals("png")) validatePngStructure(bytes);
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0) throw new IOException("Invalid image dimensions.");
                var parameters = reader.getDefaultReadParam();
                int sampling = Math.max(1, (int) Math.ceil(Math.sqrt((double) pixels / MAX_IMAGE_PIXELS)));
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                final boolean[] warning = { false };
                reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                if (reader.read(0, parameters) == null || warning[0]) throw new IOException("Malformed image.");
                // ImageIO accepts certain truncated streams; also require their end markers.
                if (extension.equals("png")) {
                    byte[] end = { 0, 0, 0, 0, 73, 69, 78, 68, (byte) 174, 66, 96, (byte) 130 };
                    if (bytes.length < end.length) throw new IOException("Truncated PNG.");
                    for (int i = 0; i < end.length; i++) {
                        if (bytes[bytes.length - end.length + i] != end[i]) throw new IOException("Truncated PNG.");
                    }
                } else if (bytes.length < 2 || bytes[bytes.length - 2] != (byte) 255 || bytes[bytes.length - 1] != (byte) 217) {
                    throw new IOException("Truncated JPEG.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    //--------------------------------------------------------------

    private ApiException tooLarge() {
        return new ApiException(413, "Hver fil må højst være 1 MiB (1.048.576 bytes).");
    }

    //--------------------------------------------------------------

    private void validatePngStructure(byte[] bytes) throws IOException {
        int offset = 8;
        boolean header = false;
        boolean data = false;
        while (offset <= bytes.length - 12) {
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            if (length < 0 || length > bytes.length - offset - 12) throw new IOException("Invalid PNG chunk length.");
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            if (!header && (!type.equals("IHDR") || length != 13)) throw new IOException("Invalid PNG header.");
            if (type.equals("IHDR")) {
                if (header) throw new IOException("Duplicate PNG header.");
                header = true;
            }
            CRC32 checksum = new CRC32();
            checksum.update(bytes, offset + 4, length + 4);
            long expected = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + length + 8, 4).getInt());
            if (checksum.getValue() != expected) throw new IOException("Invalid PNG checksum.");
            offset += length + 12;
            if (type.equals("IDAT")) data = true;
            if (type.equals("IEND")) {
                if (!data || length != 0 || offset != bytes.length) throw new IOException("Invalid PNG end.");
                return;
            }
        }
        throw new IOException("Truncated PNG.");
    }
}
