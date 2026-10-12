package app.services;

import app.exceptions.ApiException;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class FileValidator {
    public static final int MAX_FILES = 3;
    public static final int MAX_SIZE = 1_048_576;
    private static final long MAX_IMAGE_PIXELS = 16_777_216;
    private static final Set<String> UNSAFE_PDF_KEYS = Set.of(
            "AA", "A", "EF", "EmbeddedFiles", "JavaScript", "JS", "Launch",
            "RichMedia", "XFA", "OpenAction", "FileAttachment", "AcroForm",
            "Collection", "GoToR", "GoToE", "SubmitForm", "ImportData",
            "Sound", "Movie", "3D", "Rendition", "Screen");

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
        if (upload == null || upload.content() == null) {
            throw new ApiException(400, "Uploaden er ugyldig.");
        }
        String name = upload.originalName();
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(400, "Filnavnet er ugyldigt.");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (name.lastIndexOf('.') < 1 || !List.of("pdf", "png", "jpg", "jpeg").contains(extension)) {
            throw new ApiException(415, "Kun PDF, PNG, JPG og JPEG er tilladt.");
        }
        if (upload.declaredSize() < 0) throw new ApiException(400, "Filens størrelse er ugyldig.");
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
        if (!extension.equals("pdf")) {
            try {
                bytes = normalizeImage(bytes, extension);
                if (bytes.length == 0) throw new IOException("Normalized image is empty.");
                if (bytes.length > MAX_SIZE) throw tooLarge();
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof ApiException apiException) throw apiException;
                throw new ApiException(415, "Billedet kunne ikke behandles sikkert.");
            }
        }
        return new ValidatedFile(name, extension,
                extension.equals("pdf") ? "application/pdf" : extension.equals("png") ? "image/png" : "image/jpeg", bytes);
    }

    //--------------------------------------------------------------

    private void validatePdf(byte[] bytes) throws IOException {
        if (!new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII).startsWith("%PDF-")) {
            throw new IOException("Invalid PDF header.");
        }
        rejectTrailingContent(bytes);
        try (var source = new RandomAccessReadBuffer(bytes)) {
            PDFParser parser = new PDFParser(source);
            try (var document = parser.parse(false)) {
                if (document.getNumberOfPages() < 1) throw new IOException("PDF has no pages.");
                if (document.isEncrypted()) throw new IOException("Encrypted PDF is not accepted.");
                rejectUnsafePdfObjects(document.getDocument().getTrailer(),
                        Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                // Walk the page tree without rendering or extracting compressed page content.
                for (var page : document.getPages()) page.getMediaBox();
            }
        }
    }

    //--------------------------------------------------------------

    private void rejectTrailingContent(byte[] bytes) throws IOException {
        int eof = lastIndexOf(bytes, "%%EOF".getBytes(StandardCharsets.US_ASCII));
        if (eof < 0) throw new IOException("Missing PDF end marker.");
        for (int index = eof + 5; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            if (value != 0x09 && value != 0x0a && value != 0x0c && value != 0x0d && value != 0x20) {
                throw new IOException("Unexpected content after PDF end marker.");
            }
        }
    }

    //--------------------------------------------------------------

    private void rejectUnsafePdfObjects(COSBase base, Set<COSBase> visited, int depth) throws IOException {
        if (base == null || !visited.add(base)) return;
        if (visited.size() > 100_000 || depth > 256) throw new IOException("PDF object graph exceeds safety limits.");
        if (base instanceof COSDictionary dictionary) {
            for (COSName key : dictionary.keySet()) {
                if (UNSAFE_PDF_KEYS.contains(key.getName())) throw new IOException("Unsafe PDF feature.");
                rejectUnsafePdfObjects(dictionary.getItem(key), visited, depth + 1);
            }
        } else if (base instanceof COSObject object) {
            rejectUnsafePdfObjects(object.getObject(), visited, depth + 1);
        } else if (base instanceof COSArray array) {
            for (COSBase item : array) rejectUnsafePdfObjects(item, visited, depth + 1);
        }
    }

    //--------------------------------------------------------------

    private int lastIndexOf(byte[] bytes, byte[] marker) {
        for (int index = bytes.length - marker.length; index >= 0; index--) {
            boolean matches = true;
            for (int offset = 0; offset < marker.length; offset++) {
                if (bytes[index + offset] != marker[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return index;
        }
        return -1;
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
                } else {
                    if (bytes.length < 2 || bytes[bytes.length - 2] != (byte) 255 || bytes[bytes.length - 1] != (byte) 217) {
                        throw new IOException("Truncated JPEG.");
                    }
                    if (countJpegEndMarkers(bytes) != 1) throw new IOException("JPEG has ambiguous end markers.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    //--------------------------------------------------------------

    private byte[] normalizeImage(byte[] bytes, String extension) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                int sampling = Math.max(1, (int) Math.ceil(Math.sqrt((double) width * height / MAX_IMAGE_PIXELS)));
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                BufferedImage image = reader.read(0, parameters);
                if (image == null) throw new IOException("Could not decode image.");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, extension.equals("png") ? "png" : "jpeg", output)) {
                    throw new IOException("No image writer available.");
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        }
    }

    //--------------------------------------------------------------

    private int countJpegEndMarkers(byte[] bytes) {
        int count = 0;
        for (int index = 0; index < bytes.length - 1; index++) {
            if ((bytes[index] & 0xff) == 0xff && (bytes[index + 1] & 0xff) == 0xd9) count++;
        }
        return count;
    }

    //--------------------------------------------------------------

    private ApiException tooLarge() {
        return new ApiException(413, "Hver fil må højst være 1 MiB (1.048.576 bytes).");
    }

    //--------------------------------------------------------------

    private void validatePngStructure(byte[] bytes) throws IOException {
        byte[] signature = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };
        if (bytes.length < signature.length) throw new IOException("Truncated PNG signature.");
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) throw new IOException("Invalid PNG signature.");
        }
        int offset = 8;
        boolean header = false;
        boolean data = false;
        boolean dataEnded = false;
        boolean palette = false;
        boolean end = false;
        while (offset <= bytes.length - 12) {
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            if (length < 0 || length > bytes.length - offset - 12) throw new IOException("Invalid PNG chunk length.");
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            if (!type.matches("[A-Za-z]{4}") || (type.charAt(2) & 0x20) != 0) throw new IOException("Invalid PNG chunk type.");
            if (!header && (!type.equals("IHDR") || length != 13)) throw new IOException("Invalid PNG header.");
            if (type.equals("IHDR")) {
                if (header || offset != 8) throw new IOException("Duplicate or misplaced PNG header.");
                int bitDepth = bytes[offset + 8] & 0xff;
                int colorType = bytes[offset + 9] & 0xff;
                boolean validDepth = switch (colorType) {
                    case 0 -> Set.of(1, 2, 4, 8, 16).contains(bitDepth);
                    case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
                    case 3 -> Set.of(1, 2, 4, 8).contains(bitDepth);
                    default -> false;
                };
                if (!validDepth || bytes[offset + 10] != 0 || bytes[offset + 11] != 0
                        || (bytes[offset + 12] != 0 && bytes[offset + 12] != 1)) {
                    throw new IOException("Invalid PNG image parameters.");
                }
                header = true;
            } else if (!header) {
                throw new IOException("PNG header must be first.");
            }
            if (end) throw new IOException("Data found after PNG end chunk.");
            if (type.equals("IEND")) end = true;
            if (type.equals("PLTE")) {
                if (palette || data || length == 0 || length % 3 != 0 || length > 768) throw new IOException("Invalid PNG palette.");
                palette = true;
            }
            if (type.equals("IDAT")) {
                if (dataEnded) throw new IOException("PNG data chunks are not consecutive.");
                data = true;
            } else if (data) {
                dataEnded = true;
            }
            boolean critical = (type.charAt(0) & 0x20) == 0;
            if (critical && !Set.of("IHDR", "PLTE", "IDAT", "IEND").contains(type)) {
                throw new IOException("Unknown critical PNG chunk.");
            }
            CRC32 checksum = new CRC32();
            checksum.update(bytes, offset + 4, length + 4);
            long expected = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + length + 8, 4).getInt());
            if (checksum.getValue() != expected) throw new IOException("Invalid PNG checksum.");
            offset += length + 12;
            if (type.equals("IEND")) {
                if (!data || length != 0 || offset != bytes.length || !header || !end) throw new IOException("Invalid PNG end.");
                return;
            }
        }
        throw new IOException("Truncated PNG.");
    }
}
