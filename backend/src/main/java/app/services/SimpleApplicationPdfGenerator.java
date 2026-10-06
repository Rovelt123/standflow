package app.services;

import app.entities.Application;
import app.exceptions.ApiException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SimpleApplicationPdfGenerator implements ApplicationPdfGenerator {

    @Override
    public byte[] generate(Application application) {
        try {
            List<String> lines = applicationLines(application);
            byte[] content = contentStream(lines).getBytes(StandardCharsets.ISO_8859_1);
            return pdfDocument(content);
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Ansogningen kunne ikke danne PDF.");
        }
    }

    //--------------------------------------------------------------

    private List<String> applicationLines(Application application) {
        List<String> lines = new ArrayList<>();
        lines.add("StandFlow ansogning");
        lines.add("ID: " + application.getId());
        lines.add("Oprettet: " + application.getCreatedAt());
        lines.add("Status: " + application.getStatus());
        lines.add("Virksomhed: " + application.getCompany());
        lines.add("Kontaktperson: " + application.getContact());
        lines.add("CVR: " + application.getCvr());
        lines.add("E-mail: " + application.getEmail());
        lines.add("Telefon: " + application.getPhone());
        lines.add("Adresse: " + application.getAddress());
        lines.add("Postnr. og by: " + application.getCity());
        lines.add("Website: " + valueOrEmpty(application.getWebsite()));
        lines.add("Tidligere stadeholder: " + (application.isPreviousExhibitor() ? "Ja" : "Nej"));
        lines.add("Standtype: " + application.getStandType());
        lines.add("Borde: " + application.getTables());
        lines.add("Stole: " + application.getChairs());
        lines.add("Produkter:");
        lines.addAll(wrap(application.getProducts(), 86));
        return lines;
    }

    //--------------------------------------------------------------

    private String contentStream(List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 11 Tf\n14 TL\n72 780 Td\n");
        for (String line : lines) {
            stream.append("(").append(escape(line)).append(") Tj\nT*\n");
        }
        stream.append("ET\n");
        return stream.toString();
    }

    //--------------------------------------------------------------

    private byte[] pdfDocument(byte[] content) {
        List<byte[]> objects = List.of(
                bytes("<< /Type /Catalog /Pages 2 0 R >>\n"),
                bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n"),
                bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\n"),
                bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n"),
                bytes("<< /Length " + content.length + " >>\nstream\n"
                        + new String(content, StandardCharsets.ISO_8859_1) + "endstream\n")
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(out.size());
            write(out, (index + 1) + " 0 obj\n");
            write(out, objects.get(index));
            write(out, "endobj\n");
        }
        int xrefOffset = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n");
        write(out, "0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, String.format("%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n");
        write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");
        return out.toByteArray();
    }

    //--------------------------------------------------------------

    private List<String> wrap(String value, int lineLength) {
        List<String> lines = new ArrayList<>();
        String text = value == null ? "" : value;
        for (int start = 0; start < text.length(); start += lineLength) {
            lines.add(text.substring(start, Math.min(start + lineLength, text.length())));
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    //--------------------------------------------------------------

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    //--------------------------------------------------------------

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    //--------------------------------------------------------------

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    //--------------------------------------------------------------

    private void write(ByteArrayOutputStream out, String value) {
        write(out, bytes(value));
    }

    //--------------------------------------------------------------

    private void write(ByteArrayOutputStream out, byte[] value) {
        out.writeBytes(value);
    }
}
